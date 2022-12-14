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

package com.webank.wedatasphere.dss.orchestrator.publish.impl;

import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.common.label.DSSLabel;
import com.webank.wedatasphere.dss.common.label.DSSLabelUtil;
import com.webank.wedatasphere.dss.common.utils.DSSExceptionUtils;
import com.webank.wedatasphere.dss.common.utils.IoUtils;
import com.webank.wedatasphere.dss.common.utils.MapUtils;
import com.webank.wedatasphere.dss.common.utils.ZipHelper;
import com.webank.wedatasphere.dss.contextservice.service.ContextService;
import com.webank.wedatasphere.dss.orchestrator.common.entity.DSSOrchestratorInfo;
import com.webank.wedatasphere.dss.orchestrator.common.entity.DSSOrchestratorVersion;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.RequestImportOrchestrator;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.RequestProjectImportOrchestrator;
import com.webank.wedatasphere.dss.orchestrator.common.ref.OrchestratorRefConstant;
import com.webank.wedatasphere.dss.orchestrator.core.DSSOrchestrator;
import com.webank.wedatasphere.dss.orchestrator.core.plugin.AbstractDSSOrchestratorPlugin;
import com.webank.wedatasphere.dss.orchestrator.core.service.BMLService;
import com.webank.wedatasphere.dss.orchestrator.core.utils.OrchestratorUtils;
import com.webank.wedatasphere.dss.orchestrator.db.dao.OrchestratorMapper;
import com.webank.wedatasphere.dss.orchestrator.loader.OrchestratorManager;
import com.webank.wedatasphere.dss.orchestrator.publish.ImportDSSOrchestratorPlugin;
import com.webank.wedatasphere.dss.orchestrator.publish.io.input.MetaInputService;
import com.webank.wedatasphere.dss.orchestrator.publish.utils.OrchestrationDevelopmentOperationUtils;
import com.webank.wedatasphere.dss.sender.service.DSSSenderServiceFactory;
import com.webank.wedatasphere.dss.standard.app.development.operation.RefImportOperation;
import com.webank.wedatasphere.dss.standard.app.development.ref.ImportRequestRef;
import com.webank.wedatasphere.dss.standard.app.development.ref.RefJobContentResponseRef;
import com.webank.wedatasphere.dss.standard.app.development.service.RefImportService;
import com.webank.wedatasphere.dss.standard.app.development.standard.DevelopmentIntegrationStandard;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.webank.wedatasphere.dss.orchestrator.publish.impl.ExportDSSOrchestratorPluginImpl.DEFAULT_ORC_NAME;


@Component
public class ImportDSSOrchestratorPluginImpl extends AbstractDSSOrchestratorPlugin implements ImportDSSOrchestratorPlugin {

    @Autowired
    private MetaInputService metaInputService;
    @Autowired
    private OrchestratorMapper orchestratorMapper;
    @Autowired
    private BMLService bmlService;
    @Autowired
    private ContextService contextService;
    @Autowired
    private OrchestratorManager orchestratorManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importOrchestrator(RequestImportOrchestrator requestImportOrchestrator) throws Exception {
        String userName = requestImportOrchestrator.getUserName();
        String projectName = requestImportOrchestrator.getProjectName();
        Long projectId = requestImportOrchestrator.getProjectId();
        String resourceId = requestImportOrchestrator.getResourceId();
        String version = requestImportOrchestrator.getBmlVersion();
        List<DSSLabel> dssLabels = requestImportOrchestrator.getDssLabels();
        Workspace workspace = requestImportOrchestrator.getWorkspace();

        //1?????????BML???Orchestrator????????????
        String inputZipPath = IoUtils.generateIOPath(userName, projectName, DEFAULT_ORC_NAME + ".zip");
        bmlService.downloadToLocalPath(userName, resourceId, version, inputZipPath);
        String inputPath = ZipHelper.unzip(inputZipPath);

        //2?????????Info??????(??????????????????)
        List<DSSOrchestratorInfo> dssOrchestratorInfos = metaInputService.importOrchestrator(inputPath);
        DSSOrchestratorInfo importDssOrchestratorInfo = dssOrchestratorInfos.get(0);

        //?????????????????????????????????UUID?????????????????????ID
        if (requestImportOrchestrator.getCopyProjectId() != null
                && StringUtils.isNotBlank(requestImportOrchestrator.getCopyProjectName())) {
            projectId = requestImportOrchestrator.getCopyProjectId();
            importDssOrchestratorInfo.setProjectId(projectId);
            importDssOrchestratorInfo.setUUID(UUID.randomUUID().toString());
        }
        //????????????ID?????????????????????
        String uuid = orchestratorMapper.getOrcNameByParam(importDssOrchestratorInfo.getProjectId(), importDssOrchestratorInfo.getName());
        //??????UUID???????????????????????????
        DSSOrchestratorInfo existFlag = orchestratorMapper.getOrchestratorByUUID(importDssOrchestratorInfo.getUUID());
        //add ???update??????????????????????????????id????????????????????????????????????
        //todo ???????????????????????????ID??????????????????????????????????????????????????????ID???????????????ID,??????????????????????????????????????????????????????????????????
        if (null != existFlag) {
            //?????????????????????????????????????????????
            if (StringUtils.isNotBlank(uuid) && !uuid.equals(importDssOrchestratorInfo.getUUID())) {
                DSSExceptionUtils
                        .dealErrorException(61002, "The same orchestration name already exists", DSSErrorException.class);
            }
            importDssOrchestratorInfo.setId(existFlag.getId());
            //??????Orchestrator?????????????????????????????????????????????????????????????????????name,????????????????????????
            orchestratorMapper.updateOrchestrator(importDssOrchestratorInfo);
        } else {
            //?????????????????????????????????????????????
            if (StringUtils.isNotBlank(uuid)) {
                DSSExceptionUtils
                        .dealErrorException(61002, "The same orchestration name already exists", DSSErrorException.class);
            }
            //?????????????????????id
            importDssOrchestratorInfo.setId(null);
            importDssOrchestratorInfo.setCreator(userName);
            importDssOrchestratorInfo.setCreateTime(new Date());
            //0.x?????????????????????
            if (importDssOrchestratorInfo.getWorkspaceId() == null) {
                importDssOrchestratorInfo.setWorkspaceId(workspace.getWorkspaceId());
            }
            if (StringUtils.isEmpty(importDssOrchestratorInfo.getOrchestratorMode())) {
                importDssOrchestratorInfo.setOrchestratorMode("pom_work_flow");
            }
            if (StringUtils.isEmpty(importDssOrchestratorInfo.getOrchestratorWay())) {
                importDssOrchestratorInfo.setOrchestratorWay(",pom_work_flow_DAG,");
            }
            orchestratorMapper.addOrchestrator(importDssOrchestratorInfo);
        }
        String flowZipPath = inputPath + File.separator + "orc_flow.zip";
        //3??????????????????zip??????bml
        InputStream inputStream = bmlService.readLocalResourceFile(userName, flowZipPath);
        Map<String, Object> resultMap = bmlService.upload(userName, inputStream, importDssOrchestratorInfo.getName() + "_orc_flow.zip", projectName);
        String orcResourceId = resultMap.get("resourceId").toString();
        String orcBmlVersion = resultMap.get("version").toString();

        //4???????????????Version??????
        DSSOrchestratorVersion dssOrchestratorVersion = new DSSOrchestratorVersion();
        dssOrchestratorVersion.setAppId(null);
        dssOrchestratorVersion.setComment("orchestrator import");
        dssOrchestratorVersion.setOrchestratorId(importDssOrchestratorInfo.getId());
        dssOrchestratorVersion.setContent("");
        dssOrchestratorVersion.setProjectId(projectId);
        dssOrchestratorVersion.setSource("Orchestrator create");
        dssOrchestratorVersion.setUpdater(userName);
        //?????????????????????????????????????????????????????????
        dssOrchestratorVersion.setValidFlag(DSSLabelUtil.isDevEnv(dssLabels) ? 1 : 0);

        String oldVersion = orchestratorMapper.getLatestVersion(importDssOrchestratorInfo.getId(), 1);
        if (StringUtils.isNotEmpty(oldVersion)) {
            dssOrchestratorVersion.setVersion(OrchestratorUtils.increaseVersion(oldVersion));
        } else {
            dssOrchestratorVersion.setVersion(OrchestratorUtils.generateNewVersion());
        }

        //5??????????????????ContextId
        String contextId = contextService.createContextID(workspace.getWorkspaceName(), projectName, importDssOrchestratorInfo.getName(), dssOrchestratorVersion.getVersion(), userName);
        dssOrchestratorVersion.setFormatContextId(contextId);
        LOGGER.info("Create a new ContextId for import: {} ", contextId);

        dssOrchestratorVersion.setUpdateTime(new Date());
        orchestratorMapper.addOrchestratorVersion(dssOrchestratorVersion);

        //6????????????????????????????????????????????????Visualis???Qualities
        DSSOrchestrator dssOrchestrator = orchestratorManager.getOrCreateOrchestrator(userName,
                workspace.getWorkspaceName(), importDssOrchestratorInfo.getType(), dssLabels);
        Long finalProjectId = projectId;
        RefJobContentResponseRef responseRef = OrchestrationDevelopmentOperationUtils.tryOrchestrationOperation(importDssOrchestratorInfo,
                dssOrchestrator, userName, workspace, dssLabels,
                DevelopmentIntegrationStandard::getRefImportService,
                developmentService -> ((RefImportService) developmentService).getRefImportOperation(),
                dssContextRequestRef -> dssContextRequestRef.setContextId(contextId),
                projectRefRequestRef -> projectRefRequestRef.setRefProjectId(finalProjectId).setProjectName(projectName),
                (developmentOperation, developmentRequestRef) -> {
                    ImportRequestRef requestRef = (ImportRequestRef) developmentRequestRef;
                    requestRef.setResourceMap(MapUtils.newCommonMap(ImportRequestRef.RESOURCE_ID_KEY, orcResourceId, ImportRequestRef.RESOURCE_VERSION_KEY, orcBmlVersion));
                    requestRef.setNewVersion(dssOrchestratorVersion.getVersion());
                    return ((RefImportOperation) developmentOperation).importRef(requestRef);
                }, "import");
        long orchestrationId = (Long) responseRef.getRefJobContent().get(OrchestratorRefConstant.ORCHESTRATION_ID_KEY);
        String orchestrationContent = (String) responseRef.getRefJobContent().get(OrchestratorRefConstant.ORCHESTRATION_CONTENT_KEY);

        //??????????????????
        dssOrchestratorVersion.setAppId(orchestrationId);
        dssOrchestratorVersion.setContent(orchestrationContent);
        orchestratorMapper.updateOrchestratorVersion(dssOrchestratorVersion);
//        synProjectOrchestrator(importDssOrchestratorInfo, dssOrchestratorVersion, dssLabels);
        return dssOrchestratorVersion.getOrchestratorId();
    }

    public void synProjectOrchestrator(DSSOrchestratorInfo importDssOrchestratorInfo, DSSOrchestratorVersion dssOrchestratorVersion, List<DSSLabel> dssLabels) {
        //Is dev environment
        if (DSSLabelUtil.isDevEnv(dssLabels)) {
            RequestProjectImportOrchestrator projectImportOrchestrator = new RequestProjectImportOrchestrator();
            BeanUtils.copyProperties(importDssOrchestratorInfo, projectImportOrchestrator);
            projectImportOrchestrator.setVersionId(dssOrchestratorVersion.getId());
            //?????????????????????????????????
            DSSSenderServiceFactory.getOrCreateServiceInstance().getProjectServerSender()
                    .ask(projectImportOrchestrator);
        }
    }
}
