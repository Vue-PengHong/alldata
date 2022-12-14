/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.apache.linkis.manager.am.service.engine


import org.apache.commons.lang3.StringUtils
import org.apache.linkis.common.ServiceInstance
import org.apache.linkis.common.exception.LinkisRetryException
import org.apache.linkis.common.utils.{ByteTimeUtils, Logging, Utils}
import org.apache.linkis.governance.common.conf.GovernanceCommonConf
import org.apache.linkis.governance.common.conf.GovernanceCommonConf.ENGINE_CONN_MANAGER_SPRING_NAME
import org.apache.linkis.governance.common.utils.JobUtils
import org.apache.linkis.manager.am.conf.{AMConfiguration, EngineConnConfigurationService}
import org.apache.linkis.manager.am.exception.{AMErrorCode, AMErrorException}
import org.apache.linkis.manager.am.label.EngineReuseLabelChooser
import org.apache.linkis.manager.am.pointer.EngineConnPluginPointer
import org.apache.linkis.manager.am.selector.{ECAvailableRule, NodeSelector}
import org.apache.linkis.manager.common.constant.AMConstant
import org.apache.linkis.manager.common.entity.enumeration.NodeStatus
import org.apache.linkis.manager.common.entity.node.{EMNode, EngineNode}
import org.apache.linkis.manager.common.entity.resource.NodeResource
import org.apache.linkis.manager.common.protocol.engine.{EngineCreateRequest, EngineStopRequest}
import org.apache.linkis.manager.common.utils.ManagerUtils
import org.apache.linkis.manager.engineplugin.common.launch.entity.{EngineConnBuildRequestImpl, EngineConnCreationDescImpl}
import org.apache.linkis.manager.engineplugin.common.resource.TimeoutEngineResourceRequest
import org.apache.linkis.manager.label.builder.factory.LabelBuilderFactoryContext
import org.apache.linkis.manager.label.entity.engine.{EngineInstanceLabel, EngineTypeLabel}
import org.apache.linkis.manager.label.entity.node.AliasServiceInstanceLabel
import org.apache.linkis.manager.label.entity.{EngineNodeLabel, Label}
import org.apache.linkis.manager.label.service.{NodeLabelService, UserLabelService}
import org.apache.linkis.manager.label.utils.LabelUtils
import org.apache.linkis.manager.persistence.{NodeManagerPersistence, NodeMetricManagerPersistence, ResourceManagerPersistence}
import org.apache.linkis.manager.rm.service.ResourceManager
import org.apache.linkis.manager.rm.{AvailableResource, NotEnoughResource}
import org.apache.linkis.manager.service.common.label.{LabelChecker, LabelFilter}
import org.apache.linkis.rpc.Sender
import org.apache.linkis.rpc.message.annotation.Receiver
import org.apache.linkis.server.BDPJettyServerHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.util
import java.util.concurrent.{TimeoutException, TimeUnit}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration



@Service
class DefaultEngineCreateService extends AbstractEngineService with EngineCreateService with Logging {


  @Autowired
  private var nodeSelector: NodeSelector = _

  @Autowired
  private var engineRecycleService: EngineRecycleService = _

  @Autowired
  private var nodeLabelService: NodeLabelService = _

  @Autowired
  private var resourceManager: ResourceManager = _

  @Autowired
  private var labelCheckerList: util.List[LabelChecker] = _

  @Autowired
  private var labelFilter: LabelFilter = _

  @Autowired
  private var userLabelService: UserLabelService = _

  @Autowired
  private var engineConnConfigurationService: EngineConnConfigurationService = _

  @Autowired
  private var engineConnPluginPointer: EngineConnPluginPointer = _

  @Autowired
  private var nodeMetricManagerPersistence: NodeMetricManagerPersistence = _

  @Autowired
  private var resourceManagerPersistence: ResourceManagerPersistence = _

  @Autowired
  private var engineReuseLabelChoosers: util.List[EngineReuseLabelChooser] = _

  @Autowired
  private var nodeManagerPersistence: NodeManagerPersistence = _

  @Autowired
  private var engineStopService: EngineStopService = _

  def getEngineNode(serviceInstance: ServiceInstance): EngineNode = {
    val engineNode = getEngineNodeManager.getEngineNode(serviceInstance)
    if (engineNode != null) {
      if (engineNode.getNodeStatus == null) {
        val nodeMetric = nodeMetricManagerPersistence.getNodeMetrics(engineNode)
        engineNode.setNodeStatus(if (Option(nodeMetric).isDefined) NodeStatus.values()(nodeMetric.getStatus) else NodeStatus.Starting)
      }
      return engineNode
    }
    val labels = resourceManagerPersistence.getLabelsByTicketId(serviceInstance.getInstance)
    labels.asScala.foreach { label =>
      LabelBuilderFactoryContext.getLabelBuilderFactory
        .createLabel[Label[_]](label.getLabelKey, label.getStringValue) match {
        case engineInstanceLabel: EngineInstanceLabel =>
          val serviceInstance = ServiceInstance(engineInstanceLabel.getServiceName, engineInstanceLabel.getInstance)
          val engineNode = getEngineNodeManager.getEngineNode(serviceInstance)
          if (engineNode != null) {
            if (engineNode.getNodeStatus == null) {
              engineNode.setNodeStatus(NodeStatus.values()(nodeMetricManagerPersistence.getNodeMetrics(engineNode).getStatus))
            }
            return engineNode
          }
        case _ =>
      }
    }
    throw new AMErrorException(AMErrorCode.NOT_EXISTS_ENGINE_CONN.getCode, AMErrorCode.NOT_EXISTS_ENGINE_CONN.getMessage)
  }



  @Receiver
  @throws[LinkisRetryException]
  override def createEngine(engineCreateRequest: EngineCreateRequest, sender: Sender): EngineNode = {
    val startTime = System.currentTimeMillis
    val taskId = JobUtils.getJobIdFromStringMap(engineCreateRequest.getProperties)
    logger.info(s"Task: $taskId start to create Engine for request: $engineCreateRequest.")
    val labelBuilderFactory = LabelBuilderFactoryContext.getLabelBuilderFactory
    val timeout = if (engineCreateRequest.getTimeOut <= 0) AMConfiguration.ENGINE_START_MAX_TIME.getValue.toLong else engineCreateRequest.getTimeOut

    // 1. ??????Label????????????
    var labelList: util.List[Label[_]] = LabelUtils.distinctLabel(labelBuilderFactory.getLabels(engineCreateRequest.getLabels),
      userLabelService.getUserLabels(engineCreateRequest.getUser))

    //label chooser
    if (null != engineReuseLabelChoosers) {
      engineReuseLabelChoosers.asScala.foreach { chooser =>
        labelList = chooser.chooseLabels(labelList)
      }
    }

    for (labelChecker <- labelCheckerList.asScala) {
      if (!labelChecker.checkEngineLabel(labelList)) {
        throw new AMErrorException(AMConstant.EM_ERROR_CODE, "Need to specify engineType and userCreator label")
      }
    }

    val emLabelList = new util.ArrayList[Label[_]](labelList)
    val emInstanceLabel = labelBuilderFactory.createLabel(classOf[AliasServiceInstanceLabel])
    emInstanceLabel.setAlias(ENGINE_CONN_MANAGER_SPRING_NAME.getValue)
    emLabelList.add(emInstanceLabel)

    // 2. NodeLabelService getNodesByLabel  ??????EMNodeList
    val emScoreNodeList =
      getEMService().getEMNodes(emLabelList.asScala.filter(!_.isInstanceOf[EngineTypeLabel]).asJava)

    //3. ??????Select  ?????????????????????????????????????????????EM????????????????????????????????????????????????
    val choseNode = if (null == emScoreNodeList || emScoreNodeList.isEmpty) null else {
      logger.info(s"Suitable ems size is ${emScoreNodeList.length}")
      nodeSelector.choseNode(emScoreNodeList.toArray)
    }
    if (null == choseNode || choseNode.isEmpty) {
      throw new LinkisRetryException(
        AMConstant.EM_ERROR_CODE,
        s" The em of labels ${engineCreateRequest.getLabels} not found"
      )
    }
    val emNode = choseNode.get.asInstanceOf[EMNode]
    //4. ????????????
    val (resourceTicketId, resource) = requestResource(engineCreateRequest, labelFilter.choseEngineLabel(labelList), emNode, timeout)

    //5. ??????engineBuildRequest??????,????????????EM????????????
    val engineBuildRequest = EngineConnBuildRequestImpl(
      resourceTicketId,
      labelFilter.choseEngineLabel(labelList),
      resource,
      EngineConnCreationDescImpl(engineCreateRequest.getCreateService, engineCreateRequest.getDescription, engineCreateRequest.getProperties))

    //6. ??????EM??????????????????????????????ASK
    //AM?????????serviceInstance???  ?????????ticketID????????????,????????? EngineConn???Label ????????????EngineInstanceLabel ??????id???Instance??????
    val oldServiceInstance = new ServiceInstance
    oldServiceInstance.setApplicationName(GovernanceCommonConf.ENGINE_CONN_SPRING_NAME.getValue)
    oldServiceInstance.setInstance(resourceTicketId)

    val engineNode = Utils.tryCatch(getEMService().createEngine(engineBuildRequest, emNode)) {
      case t: Throwable =>
        logger.info(s"Failed to create ec($resourceTicketId) ask ecm ${emNode.getServiceInstance}")
        val failedEcNode = getEngineNodeManager.getEngineNode(oldServiceInstance)
        if (null == failedEcNode) {
          logger.info(s" engineConn is not exists in db: $oldServiceInstance ")
        } else {
          failedEcNode.setLabels(nodeLabelService.getNodeLabels(oldServiceInstance))
          failedEcNode.getLabels.addAll(LabelUtils.distinctLabel(labelFilter.choseEngineLabel(labelList), emNode.getLabels))
          engineStopService.engineConnInfoClear(failedEcNode)
        }
        throw t
    }

    logger.info(s"Task: $taskId finished to create  engineConn $engineNode. ticketId is $resourceTicketId")
    engineNode.setTicketId(resourceTicketId)
    //7. ????????????????????????????????????engine/metrics

    Utils.tryCatch(getEngineNodeManager.updateEngineNode(oldServiceInstance, engineNode)) { t =>
      logger.warn(s"Failed to update engineNode $engineNode", t)
      val stopEngineRequest = new EngineStopRequest(engineNode.getServiceInstance, ManagerUtils.getAdminUser)
      engineStopService.asyncStopEngine(stopEngineRequest)
      val failedEcNode = getEngineNodeManager.getEngineNode(oldServiceInstance)
      if (null == failedEcNode) {
        logger.info(s" engineConn is not exists in db: $oldServiceInstance ")
      } else {
        failedEcNode.setLabels(nodeLabelService.getNodeLabels(oldServiceInstance))
        failedEcNode.getLabels.addAll(LabelUtils.distinctLabel(labelFilter.choseEngineLabel(labelList), emNode.getLabels))
        engineStopService.engineConnInfoClear(failedEcNode)
      }
      throw new LinkisRetryException(AMConstant.EM_ERROR_CODE, s"Failed to update engineNode: ${t.getMessage}")
    }

    //8. ?????? EngineConn???Label,??????engineConn???Alias
    val engineConnAliasLabel = labelBuilderFactory.createLabel(classOf[AliasServiceInstanceLabel])
    engineConnAliasLabel.setAlias(GovernanceCommonConf.ENGINE_CONN_SPRING_NAME.getValue)
    labelList.add(engineConnAliasLabel)
    nodeLabelService.addLabelsToNode(engineNode.getServiceInstance, labelFilter.choseEngineLabel(LabelUtils.distinctLabel(labelList, fromEMGetEngineLabels(emNode.getLabels))))
    if(System.currentTimeMillis - startTime >= timeout && engineCreateRequest.isIgnoreTimeout) {
      logger.info(s"Return a EngineConn $engineNode for request: $engineCreateRequest since the creator set ignoreTimeout=true and maxStartTime is reached.")
      return engineNode
    }
    val leftWaitTime = timeout - (System.currentTimeMillis - startTime)
    if (ECAvailableRule.getInstance.isNeedAvailable(labelList)) {
      ensureECAvailable(engineNode, resourceTicketId, leftWaitTime)
      logger.info(s"Task: $taskId finished to create Engine for request: $engineCreateRequest and get engineNode $engineNode. time taken ${System.currentTimeMillis() - startTime}ms")
    } else {
      logger.info(s"Task: $taskId finished to create Engine for request: $engineCreateRequest and get engineNode $engineNode.And did not judge the availability,time taken ${System.currentTimeMillis() - startTime}ms")
    }
    engineNode
  }

  private def requestResource(engineCreateRequest: EngineCreateRequest, labelList: util.List[Label[_]], emNode: EMNode, timeout: Long): (String, NodeResource) = {
    //4.  ???RM????????????EM??????????????????, ???????????????????????????RetryException
    // 4.1 TODO ??????EM?????????????????????EM???????????????engine
    // 4.2 TODO ????????????????????????????????????????????????engine??????
    //???????????????????????????
    if (engineCreateRequest.getProperties == null) {
      engineCreateRequest.setProperties(new util.HashMap[String, String]())
    }
    val configProp = engineConnConfigurationService.getConsoleConfiguration(labelList)
    val props = engineCreateRequest.getProperties
    if (null != configProp && configProp.asScala.nonEmpty) {
      configProp.asScala.foreach(keyValue => {
        if (!props.containsKey(keyValue._1)) {
          props.put(keyValue._1, keyValue._2)
        }
      })
    }
    val timeoutEngineResourceRequest = TimeoutEngineResourceRequest(timeout, engineCreateRequest.getUser, labelList, engineCreateRequest.getProperties)
    val resource = engineConnPluginPointer.createEngineResource(timeoutEngineResourceRequest)
    /*  emNode.setLabels(nodeLabelService.getNodeLabels(emNode.getServiceInstance))*/

    resourceManager.requestResource(LabelUtils.distinctLabel(labelList, emNode.getLabels), resource, timeout) match {
      case AvailableResource(ticketId) =>
        (ticketId, resource)
      case NotEnoughResource(reason) =>
        logger.warn(s"not engough resource: $reason")
        throw new LinkisRetryException(AMConstant.EM_ERROR_CODE, s"not engough resource: : $reason")
    }
  }

  private def fromEMGetEngineLabels(emLabels: util.List[Label[_]]): util.List[Label[_]] = {
    emLabels.asScala.filter { label =>
      label.isInstanceOf[EngineNodeLabel] && !label.isInstanceOf[EngineTypeLabel]
    }.asJava
  }

  private def ensuresIdle(engineNode: EngineNode, resourceTicketId: String): Boolean = {
    //TODO ??????????????????????????????engineConn????????????
    val engineNodeInfo = Utils.tryAndWarnMsg(getEngineNodeManager.getEngineNodeInfoByDB(engineNode))("Failed to from db get engine node info")
    if (null == engineNodeInfo) return false
    if (NodeStatus.isCompleted(engineNodeInfo.getNodeStatus)) {
      val metrics = nodeMetricManagerPersistence.getNodeMetrics(engineNodeInfo)
      val (reason, canRetry) = getStartErrorInfo(metrics.getHeartBeatMsg)
      if(canRetry.isDefined) {
        throw new LinkisRetryException(AMConstant.ENGINE_ERROR_CODE, s"${engineNode.getServiceInstance} ticketID:$resourceTicketId ?????????????????????,??????: ${reason}")
      }
      throw new AMErrorException(AMConstant.EM_ERROR_CODE, s"${engineNode.getServiceInstance} ticketID:$resourceTicketId ?????????????????????,??????: ${reason}")
    }
    NodeStatus.isAvailable(engineNodeInfo.getNodeStatus)
  }

  private def getStartErrorInfo(msg: String): (String, Option[Boolean]) = {

    if (StringUtils.isNotBlank(msg)) {
      val jsonNode = BDPJettyServerHelper.jacksonJson.readTree(msg)
      if (jsonNode != null && jsonNode.has(AMConstant.START_REASON)) {
        val startReason = jsonNode.get(AMConstant.START_REASON).asText()
        if (jsonNode.has(AMConstant.EC_CAN_RETRY)) {
          return (startReason, Some(true))
        } else {
          return (startReason, None)
        }
      }
    }
    (null, None)
  }

  /**
   * Need to ensure that the newly created amount ec is available before returning
   * @param engineNode
   * @param resourceTicketId
   * @param timeout
   * @return
   */
  def ensureECAvailable(engineNode: EngineNode, resourceTicketId: String, timeout: Long): EngineNode = {
    Utils.tryCatch {
      logger.info(s"Start to wait engineConn($engineNode) to be available, but only ${ByteTimeUtils.msDurationToString(timeout)} left.")
      // ????????????????????????????????????????????????????????????IDLE?????????????????????????????????????????????????????????
      Utils.waitUntil(() => ensuresIdle(engineNode, resourceTicketId), Duration(timeout, TimeUnit.MILLISECONDS))
    } {
      case _: TimeoutException =>
        logger.info(s"Waiting for engineNode:$engineNode($resourceTicketId) initialization TimeoutException , now stop it.")
        val stopEngineRequest = new EngineStopRequest(engineNode.getServiceInstance, ManagerUtils.getAdminUser)
        engineStopService.asyncStopEngine(stopEngineRequest)
        throw new LinkisRetryException(AMConstant.ENGINE_ERROR_CODE, s"Waiting for engineNode:$engineNode($resourceTicketId) initialization TimeoutException, already waiting $timeout ms")
      case t: Throwable =>
        logger.info(s"Waiting for $engineNode($resourceTicketId) initialization failure , now stop it.")
        val stopEngineRequest = new EngineStopRequest(engineNode.getServiceInstance, ManagerUtils.getAdminUser)
        engineStopService.asyncStopEngine(stopEngineRequest)
        throw t
    }
    engineNode
  }

}
