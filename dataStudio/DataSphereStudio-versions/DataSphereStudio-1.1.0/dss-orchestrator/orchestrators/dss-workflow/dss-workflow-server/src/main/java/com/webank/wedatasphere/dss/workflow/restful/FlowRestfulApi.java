/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.workflow.restful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.webank.wedatasphere.dss.appconn.manager.utils.AppConnManagerUtils;
import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.common.label.DSSLabel;
import com.webank.wedatasphere.dss.common.label.EnvDSSLabel;
import com.webank.wedatasphere.dss.contextservice.service.ContextService;
import com.webank.wedatasphere.dss.contextservice.service.impl.ContextServiceImpl;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.ResponseConvertOrchestrator;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import com.webank.wedatasphere.dss.standard.sso.utils.SSOHelper;
import com.webank.wedatasphere.dss.workflow.WorkFlowManager;
import com.webank.wedatasphere.dss.workflow.common.entity.DSSFlow;
import com.webank.wedatasphere.dss.workflow.constant.DSSWorkFlowConstant;
import com.webank.wedatasphere.dss.workflow.entity.request.*;
import com.webank.wedatasphere.dss.workflow.entity.vo.ExtraToolBarsVO;
import com.webank.wedatasphere.dss.workflow.exception.DSSWorkflowErrorException;
import com.webank.wedatasphere.dss.workflow.lock.DSSFlowEditLockManager;
import com.webank.wedatasphere.dss.workflow.service.DSSFlowService;
import com.webank.wedatasphere.dss.workflow.service.PublishService;
import org.apache.commons.lang.StringUtils;
import org.apache.linkis.server.Message;
import org.apache.linkis.server.security.SecurityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping(path = "/dss/workflow", produces = {"application/json"})
public class FlowRestfulApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowRestfulApi.class);

    private ContextService contextService = ContextServiceImpl.getInstance();
    @Autowired
    private DSSFlowService flowService;
    @Autowired
    private PublishService publishService;
    @Autowired
    private DSSFlowService dssFlowService;
    @Autowired
    private WorkFlowManager workFlowManager;

    @PostConstruct
    public void init() {
        AppConnManagerUtils.autoLoadAppConnManager();
    }

    /**
     * ??????subflow??????
     *
     * @param req
     * @param addFlowRequest
     * @return
     * @throws DSSErrorException
     * @throws JsonProcessingException
     */
    @RequestMapping(value = "addFlow", method = RequestMethod.POST)
    public Message addFlow(HttpServletRequest req, @RequestBody AddFlowRequest addFlowRequest) throws DSSErrorException, JsonProcessingException {
        //??????????????????????????????????????????????????????????????????
        String userName = SecurityFilter.getLoginUsername(req);
        // TODO: 2019/5/23 flowName????????????????????????
        String name = addFlowRequest.getName();
        String workspaceName = addFlowRequest.getWorkspaceName();
        String projectName = addFlowRequest.getProjectName();
        String version = addFlowRequest.getVersion();
        String description = addFlowRequest.getDescription();
        Long parentFlowID = addFlowRequest.getParentFlowID();
        String uses = addFlowRequest.getUses();
        List<DSSLabel> dssLabelList = new ArrayList<>();
        dssLabelList.add(new EnvDSSLabel(addFlowRequest.getLabels().getRoute()));
        String contextId = contextService.createContextID(workspaceName, projectName, name, version, userName);
        DSSFlow dssFlow = workFlowManager.createWorkflow(userName, null, name, contextId, description, parentFlowID,
                uses, new ArrayList<>(), dssLabelList, null, null);

        // TODO: 2019/5/16 ??????????????????????????????
        return Message.ok().data("flow", dssFlow);
    }

    @RequestMapping(value = "publishWorkflow", method = RequestMethod.POST)
    public Message publishWorkflow(HttpServletRequest request, @RequestBody PublishWorkflowRequest publishWorkflowRequest) throws Exception {
        Long workflowId = publishWorkflowRequest.getWorkflowId();
        Map<String, Object> labels = new HashMap<>();
        labels.put(EnvDSSLabel.DSS_ENV_LABEL_KEY, publishWorkflowRequest.getLabels().getRoute());
        String comment = publishWorkflowRequest.getComment();
        Workspace workspace = SSOHelper.getWorkspace(request);
        String publishUser = SecurityFilter.getLoginUsername(request);
        Message message;
        try {
            String taskId = publishService.submitPublish(publishUser, workflowId, labels, workspace, comment);
            LOGGER.info("submit publish task ok ,taskId is {}.", taskId);
            if (DSSWorkFlowConstant.PUBLISHING_ERROR_CODE.equals(taskId)) {
                message = Message.error("?????????????????????????????????????????????????????????????????????");
            } else if (StringUtils.isNotEmpty(taskId)) {
                message = Message.ok("?????????????????????????????????").data("releaseTaskId", taskId);
            } else {
                LOGGER.error("taskId {} is error.", taskId);
                message = Message.error("?????????????????????");
            }
        } catch (DSSWorkflowErrorException e) {
            throw e;
        } catch (final Throwable t) {
            LOGGER.error("failed to submit publish task for workflow id {}.", workflowId, t);
            message = Message.error("?????????????????????");
        }
        return message;
    }

    /**
     * ????????????????????????
     *
     * @param request
     * @param releaseTaskId
     * @return
     */
    @RequestMapping(value = "getReleaseStatus", method = RequestMethod.GET)
    public Message getReleaseStatus(HttpServletRequest request,
                                    @NotNull(message = "???????????????id????????????") @RequestParam(required = false, name = "releaseTaskId") Long releaseTaskId) {
        String username = SecurityFilter.getLoginUsername(request);
        Message message;
        try {
            ResponseConvertOrchestrator response = publishService.getStatus(username, releaseTaskId.toString());
            if (null != response.getResponse()) {
                String status = response.getResponse().getJobStatus().toString();
                status = StringUtils.isNotBlank(status) ? status.toLowerCase() : status;
                //????????????????????????????????????
                if ("failed".equalsIgnoreCase(status)) {
                    message = Message.error("????????????:" + response.getResponse().getMessage()).data("status", status);
                } else if (StringUtils.isNotBlank(status)) {
                    message = Message.ok("??????????????????").data("status", status);
                } else {
                    LOGGER.error("status is null or empty, failed to get status");
                    message = Message.error("??????????????????");
                }
            } else {
                LOGGER.error("status is null or empty, failed to get status");
                message = Message.error("??????????????????");
            }
        } catch (final Throwable t) {
            LOGGER.error("Failed to get release status for {}", releaseTaskId, t);
            message = Message.error("????????????:" + t.getMessage());
        }
        return message;
    }


    /**
     * ????????????????????????????????????????????????Json,BML?????????
     *
     * @param req
     * @param updateFlowBaseInfoRequest
     * @return
     * @throws DSSErrorException
     */

    @RequestMapping(value = "updateFlowBaseInfo", method = RequestMethod.POST)
    public Message updateFlowBaseInfo(HttpServletRequest req, @RequestBody UpdateFlowBaseInfoRequest updateFlowBaseInfoRequest) throws DSSErrorException {
        Long flowID = updateFlowBaseInfoRequest.getId();
        String name = updateFlowBaseInfoRequest.getName();
        String description = updateFlowBaseInfoRequest.getDescription();
        String uses = updateFlowBaseInfoRequest.getUses();
        // TODO: 2019/6/13  projectVersionID???????????????
        //????????????????????????
        DSSFlow dssFlow = new DSSFlow();
        dssFlow.setId(flowID);
        dssFlow.setName(name);
        dssFlow.setDescription(description);
        dssFlow.setUses(uses);
        flowService.updateFlowBaseInfo(dssFlow);
        return Message.ok();
    }

    /**
     * ??????????????????Json??????????????????????????????
     *
     * @param req
     * @param flowID
     * @return
     * @throws DSSErrorException
     */

    @RequestMapping(value = "get", method = RequestMethod.GET)
    public Message get(HttpServletRequest req,
                       @RequestParam(required = false, name = "flowId") Long flowID,
                       @RequestParam(required = false, name = "isNotHaveLock") Boolean isNotHaveLock) throws DSSErrorException {
        String username = SecurityFilter.getLoginUsername(req);
        DSSFlow dssFlow = flowService.getFlow(flowID);
        if (isNotHaveLock != null && isNotHaveLock) {
            return Message.ok().data("flow", dssFlow);
        }
        Cookie[] cookies = req.getCookies();
        String ticketId = Arrays.stream(cookies).filter(cookie -> DSSWorkFlowConstant.BDP_USER_TICKET_ID.equals(cookie.getName())).findFirst().map(Cookie::getValue).get();
        if (dssFlow != null) {
            // ??????????????????????????????
            try {
                String flowEditLock = DSSFlowEditLockManager.tryAcquireLock(dssFlow, username, ticketId);
                dssFlow.setFlowEditLock(flowEditLock);
            } catch (DSSErrorException e) {
                if (DSSWorkFlowConstant.EDIT_LOCK_ERROR_CODE == e.getErrCode()) {
                    return Message.error(e.getDesc());
                }
                throw e;
            }
        }
        return Message.ok().data("flow", dssFlow);
    }

    @RequestMapping(value = "deleteFlow", method = RequestMethod.POST)
    public Message deleteFlow(HttpServletRequest req, @RequestBody DeleteFlowRequest deleteFlowRequest) throws DSSErrorException {
        Long flowID = deleteFlowRequest.getId();
        boolean sure = deleteFlowRequest.getSure() != null && deleteFlowRequest.getSure().booleanValue();
        // TODO: 2019/6/13  projectVersionID???????????????
        //state???true?????????????????????
        if (flowService.getFlowByID(flowID).getState() && !sure) {
            return Message.ok().data("warmMsg", "???????????????????????????????????????????????????????????????????????????????????????????????????");
        }
        flowService.batchDeleteFlow(Arrays.asList(flowID));
        return Message.ok();
    }

    /**
     * ????????????????????????????????????Json???????????????????????????????????????Json??????
     *
     * @param req
     * @param saveFlowRequest
     * @return
     * @throws DSSErrorException
     * @throws IOException
     */
    @RequestMapping(value = "saveFlow", method = RequestMethod.POST)
//    @ProjectPrivChecker
    public Message saveFlow(HttpServletRequest req, @RequestBody SaveFlowRequest saveFlowRequest) throws DSSErrorException, IOException {
        Long flowID = saveFlowRequest.getId();
        String jsonFlow = saveFlowRequest.getJson();
        String workspaceName = saveFlowRequest.getWorkspaceName();
        String projectName = saveFlowRequest.getProjectName();

        Boolean isNotHaveLock = saveFlowRequest.getNotHaveLock();
        String userName = SecurityFilter.getLoginUsername(req);
        String version;
        synchronized (DSSWorkFlowConstant.saveFlowLock.intern(flowID)) {
            if (isNotHaveLock != null && isNotHaveLock.booleanValue()) {
                version = flowService.saveFlow(flowID, jsonFlow, null, userName, workspaceName, projectName);
                return Message.ok().data("flowVersion", version).data("flowEditLock", null);
            }
            version = flowService.saveFlow(flowID, jsonFlow, null, userName, workspaceName, projectName);
        }
        return Message.ok().data("flowVersion", version);
    }

    /**
     * ??????????????????????????????
     *
     * @param req          request
     * @param flowEditLock ???????????????
     * @return ???????????????
     */
    @RequestMapping(value = "/updateFlowEditLock", method = RequestMethod.GET)
    public Message updateFlowEditLock(HttpServletRequest req, @RequestParam(required = false, name = "flowEditLock") String flowEditLock) throws DSSErrorException {
        if (StringUtils.isBlank(flowEditLock)) {
            throw new DSSErrorException(60067, "update flowEditLock failed,because flowEditLock is empty");
        }
        return Message.ok().data("flowEditLock", DSSFlowEditLockManager.updateLock(flowEditLock));
    }

    @RequestMapping(value = "/getExtraToolBars", method = RequestMethod.POST)
    public Message getExtraToolBars(HttpServletRequest req, @RequestBody GetExtraToolBarsRequest getExtraToolBarsRequest) throws DSSErrorException {
        String userName = SecurityFilter.getLoginUsername(req);
        Workspace workspace = SSOHelper.getWorkspace(req);
        List<ExtraToolBarsVO> barsVOList = flowService.getExtraToolBars(workspace.getWorkspaceId(), getExtraToolBarsRequest.getProjectId());
        return Message.ok().data("extraBars", barsVOList);
    }


    @RequestMapping(value = "/deleteFlowEditLock/{flowEditLock}", method = RequestMethod.POST)
    public Message deleteFlowEditLock(HttpServletRequest req, @PathVariable("flowEditLock") String flowEditLock) throws DSSErrorException {
        DSSFlowEditLockManager.deleteLock(flowEditLock);
        return Message.ok();
    }

}
