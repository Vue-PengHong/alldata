package com.webank.wedatasphere.dss.migrate.restful;

import com.google.common.collect.Lists;
import com.webank.wedatasphere.dss.common.entity.IOType;
import com.webank.wedatasphere.dss.common.entity.Resource;
import com.webank.wedatasphere.dss.common.exception.DSSErrorException;
import com.webank.wedatasphere.dss.common.label.DSSLabel;
import com.webank.wedatasphere.dss.common.label.EnvDSSLabel;
import com.webank.wedatasphere.dss.common.label.LabelKeyConvertor;
import com.webank.wedatasphere.dss.common.protocol.RequestQueryWorkFlow;
import com.webank.wedatasphere.dss.common.protocol.ResponseExportOrchestrator;
import com.webank.wedatasphere.dss.common.utils.*;
import com.webank.wedatasphere.dss.framework.project.entity.DSSProjectDO;
import com.webank.wedatasphere.dss.framework.project.entity.request.ProjectCreateRequest;
import com.webank.wedatasphere.dss.framework.project.entity.vo.DSSProjectVo;
import com.webank.wedatasphere.dss.framework.project.exception.DSSProjectErrorException;
import com.webank.wedatasphere.dss.framework.project.server.service.BMLService;
import com.webank.wedatasphere.dss.framework.project.service.DSSFrameworkProjectService;
import com.webank.wedatasphere.dss.framework.project.service.DSSProjectService;
import com.webank.wedatasphere.dss.framework.workspace.bean.DSSWorkspace;
import com.webank.wedatasphere.dss.framework.workspace.service.DSSWorkspaceService;
import com.webank.wedatasphere.dss.migrate.conf.MigrateConf;
import com.webank.wedatasphere.dss.migrate.exception.MigrateErrorException;
import com.webank.wedatasphere.dss.migrate.service.MetaService;
import com.webank.wedatasphere.dss.migrate.service.MigrateService;
import com.webank.wedatasphere.dss.orchestrator.common.entity.DSSOrchestratorInfo;
import com.webank.wedatasphere.dss.orchestrator.common.entity.DSSOrchestratorVersion;
import com.webank.wedatasphere.dss.orchestrator.common.entity.OrchestratorVo;
import com.webank.wedatasphere.dss.orchestrator.common.protocol.*;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import com.webank.wedatasphere.dss.standard.app.sso.builder.SSOUrlBuilderOperation;
import com.webank.wedatasphere.dss.standard.app.sso.builder.impl.SSOUrlBuilderOperationImpl;
import com.webank.wedatasphere.dss.standard.sso.utils.SSOHelper;
import com.webank.wedatasphere.dss.workflow.common.entity.DSSFlow;
import com.webank.wedatasphere.dss.workflow.common.protocol.ResponseQueryWorkflow;
import com.webank.wedatasphere.dss.workflow.core.WorkflowFactory;
import com.webank.wedatasphere.dss.workflow.core.entity.Workflow;
import com.webank.wedatasphere.dss.workflow.core.json2flow.JsonToFlowParser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.linkis.common.conf.Configuration;
import org.apache.linkis.common.exception.LinkisException;
import org.apache.linkis.rpc.Sender;
import org.apache.linkis.server.BDPJettyServerHelper;
import org.apache.linkis.server.Message;
import org.apache.linkis.server.security.SecurityFilter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

@RequestMapping(path = "/dss/framework/release", produces = {"application/json"})
@RestController
public class DSSMigrateRestful {

    private static final Logger LOG = LoggerFactory.getLogger(DSSMigrateRestful.class);
    public static final int DEFAULT_PROJECT_AREA = 0; // ????????????
    public static final String PROJECT_EDIT_USER_KEY = "editUsers";
    public static final String PROJECT_ACCESS_USER_KEY = "accessUsers";
    public static final String PROJECT_RELEASE_USER_KEY = "releaseUsers";
    public static final String EXPORT_DEFAULT_FILE_NAME = "default_orc";
    public static final String FLOW_ID_KEY = "flowId";
    public static final String FLOW_BML_VERSION_KEY = "bmlVersion";
    public static final String DEFAULT_PROJECT_ORCHESTRATOR_MODE = "pom_work_flow";

    @Autowired
    private MigrateService migrateService;
    @Autowired
    private DSSWorkspaceService dssWorkspaceService;
    @Autowired
    private DSSProjectService dssProjectService;
    @Autowired
    private DSSFrameworkProjectService dssFrameworkProjectService;
    @Autowired
    BMLService bmlService;
    @Autowired
    private MetaService metaService;

    // todo only dev????????????
    private Sender orchestratorSender = Sender.getSender(MigrateConf.ORC_SERVER_NAME);
    private Sender workflowSender = Sender.getSender(MigrateConf.WORKFLOW_SERVER_NAME);


    @PostMapping("/importOldDSSProject")
    public Message importOldDSSProject(HttpServletRequest req,
                                       @RequestParam(value = "dssLabels", required = false) String dssLabels,
                                       @RequestParam(name = "file") List<MultipartFile> files) throws Exception {
        //?????????0.x?????????zip????????????????????????????????????????????????????????????
        //1.?????????????????????
        //2.????????????????????????????????????????????????????????????????????????????????????
        //2.??????????????????,??????1.0
        //3.???????????????????????????????????????zip???
        //4.?????????bml
        //5.??????resourceId ??? version ????????? dev??? orchestrator-server
        String userName = SecurityFilter.getLoginUsername(req);
//        List<FormDataBodyPart> files = form.getFields("file");
        if (files == null || files.size() <= 0) {
            LOG.error("files are null, can not continue");
            return Message.error("no files to import");
        }
        //?????????????????????
        MultipartFile p = files.get(0);
        String fileName = new String(p.getOriginalFilename().getBytes("ISO8859-1"), "UTF-8");
        InputStream is = null;
        OutputStream os = null;
        try {
            String inputPath = IoUtils.generateIOPath(userName, "input", fileName);
            File file = new File(inputPath);
            if (file.getParentFile().exists()) {
                FileUtils.deleteDirectory(file.getParentFile());
            }
            is = p.getInputStream();
            os = IoUtils.generateExportOutputStream(inputPath);
            IOUtils.copy(is, os);
//            Workspace workspace = new Workspace();
            Cookie[] cookies = req.getCookies();
            Workspace workspace = getWorkspace(req);
            migrateService.migrate(userName, inputPath, workspace);
        } catch (Exception e) {
            String errorMsg = "project import failed for:" + e.getMessage();
            Integer errorCode = 40035;
            if (e instanceof DSSProjectErrorException) {
                DSSProjectErrorException errorException = (DSSProjectErrorException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            } else if (e instanceof MigrateErrorException) {
                MigrateErrorException errorException = (MigrateErrorException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            } else if (e instanceof LinkisException) {
                LinkisException errorException = (LinkisException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            }
            LOG.error("fatal error:{}", e.getMessage(), e);
            throw new MigrateErrorException(errorCode, errorMsg);
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }
        return Message.ok();
    }

    public static Workspace getWorkspace(HttpServletRequest request) {
        Workspace workspace = new Workspace();
        Cookie[] cookies = request.getCookies();
        Arrays.stream(cookies).forEach(cookie -> {
            if ("workspaceId".equals(cookie.getName())) {
                workspace.setWorkspaceName(cookie.getValue());
            } else if ("workspaceName".equals(cookie.getName())) {
                workspace.setWorkspaceId(Long.parseLong(cookie.getValue()));
            }
        });
        workspace.setWorkspaceId(100);
        workspace.setWorkspaceName("test_workspace");
        SSOHelper.addWorkspaceInfo(request, workspace);
        return workspace;
    }

    public static Workspace getWorkspaceForOldVersion(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        Cookie workspaceCookie = Arrays.stream(cookies).
                filter(cookie -> "workspaceId".equals(cookie.getName())).findAny().orElse(null);
        Workspace workspace = new Workspace();
        if (workspaceCookie != null) {
            workspace.setWorkspaceName(workspaceCookie.getValue());
        }
        SSOUrlBuilderOperation ssoUrlBuilderOperation = new SSOUrlBuilderOperationImpl();
        Arrays.stream(cookies).forEach(cookie -> ssoUrlBuilderOperation.addCookie(cookie.getName(), cookie.getValue()));
        String gateWayUrl = Configuration.GATEWAY_URL().getValue();
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (StringUtils.isNotEmpty(forwardedHost) && forwardedHost.contains(":")) {
            gateWayUrl = "http://" + forwardedHost + "/";
        }
        ssoUrlBuilderOperation.setDSSUrl(gateWayUrl);
        ssoUrlBuilderOperation.setWorkspace(workspace.getWorkspaceName());
//        workspace.setSSOUrlBuilderOperation(ssoUrlBuilderOperation);
        return workspace;
    }


    /**
     * ?????????dss1.0????????????????????????
     *
     * @param req
     * @param workspaceName ?????????????????????????????????????????????
     * @param projectName   ????????????????????????
     * @param projectUser   ????????????????????????????????????????????????????????????????????????createProject????????????????????? {"editUsers":["alexyang"],"accessUsers":["alexyang"],"releaseUsers":["alexyang"]}
     * @param dssLabels
     * @return
     * @throws Exception
     */
    @PostMapping("/importworkflow")
    public Message importWorkFlow(HttpServletRequest req,
                                  HttpServletResponse response,
                                  @RequestParam(value = "workspaceName", required = false) String workspaceName,
                                  @RequestParam(value = "projectName", required = false) String projectName,
                                  @RequestParam(value = "projectUser", required = false) String projectUser,
                                  @RequestParam(value = "flowName", required = false) String flowName,
                                  @RequestParam(value = "dssLabels", required = false) String dssLabels,
                                  @RequestParam(name = "file") List<MultipartFile> files) throws Exception {
        String userName = SecurityFilter.getLoginUsername(req);
//        List<FormDataBodyPart> files = form.getFields("file");
        if (files == null || files.size() <= 0) {
            LOG.error("files are null, can not continue");
            return Message.error("no files to import");
        }
        //?????????????????????
//        FormDataBodyPart p = files.get(0);
        MultipartFile p = files.get(0);
//        FormDataContentDisposition fileDetail = p.getFormDataContentDisposition();
//        String fileName = new String(fileDetail.getFileName().getBytes("ISO8859-1"), "UTF-8");
        String fileName = new String(p.getOriginalFilename().getBytes("ISO8859-1"), "UTF-8");
        InputStream is = null;
        OutputStream os = null;
        Message responseMsg = Message.ok();

        try {
            String pathRoot = IoUtils.generateIOPath(userName, "input", "");
            // todo ??????????????????????????????????????????????????????????????? default_orc
            String inputZipPath = pathRoot + fileName;
            File file = new File(inputZipPath);
            if (file.getParentFile().exists()) {
                FileUtils.deleteDirectory(file.getParentFile());
            }
            is = p.getInputStream();
            os = IoUtils.generateExportOutputStream(inputZipPath);
            IOUtils.copy(is, os);
            // ??????????????????id??????????????????
            List<DSSWorkspace> workspaces = dssWorkspaceService.getWorkspaces(userName);
            int workspaceId = 0;
            DSSWorkspace dssWorkspace = workspaces.stream().filter((w) -> w.getName().equals(workspaceName)).findFirst().get();
            if (null == dssWorkspace) {
                LOG.error("Cannot find workspace {}, please create it first.", workspaceName);
                throw new MigrateErrorException(0, "Cannot find workspace " + workspaceName);
            }
            workspaceId = dssWorkspace.getId();
            // todo check
//            Workspace workspace = SSOHelper.setWorkspaceId(req, response, workspaceId, workspaceName);
            Workspace workspace = SSOHelper.setAndGetWorkspace(req, response, workspaceId, workspaceName);

            // ??????????????????????????????
            DSSProjectDO projectDO = getOrCreateProject(projectName, projectUser, userName, workspace, workspaceId);
            if (null == projectDO) {
                LOG.error("Create project : {} failed.", projectName);
                throw new MigrateErrorException(0, "createProject : " + projectName + " failed.");
            }
            DSSProjectVo projectVo = new DSSProjectVo();
            projectVo.setId(projectDO.getId());
            projectVo.setName(projectDO.getName());
            projectVo.setDescription(projectDO.getDescription());

            // todo ??????Orchestrator????????????????????????zip???
            ZipHelper.unzip(inputZipPath);
            String inputPath = pathRoot + EXPORT_DEFAULT_FILE_NAME;
            List<DSSOrchestratorInfo> dssOrchestratorInfos = metaService.readOrchestrator(inputPath);
            DSSOrchestratorInfo orchestratorInfo = dssOrchestratorInfos.get(0);
            String orcPath = inputPath;
//            String orcPath = IoUtils.generateIOPath(userName, "input", "default_orc");
//            mkdir(orcPath);
            orchestratorInfo.setProjectId(projectVo.getId());
            orchestratorInfo.setName(flowName);
            String oldUUID = migrateService.queryOrcUUIDByName(new Long(dssWorkspace.getId()), projectVo.getId(), flowName);
            if (null != oldUUID) {
                orchestratorInfo.setUUID(oldUUID);
            } else {
                String uuid = UUID.randomUUID().toString();
                orchestratorInfo.setUUID(uuid);
            }

            //?????????????????????orchestrator??????
            IoUtils.generateIOType(IOType.ORCHESTRATOR, orcPath);
            //????????????????????????env
            IoUtils.generateIOEnv(orcPath);
            metaService.writeOrchestratorInfo(orchestratorInfo, orcPath);
            String orcZipPath = ZipHelper.zip(orcPath);
            InputStream inputStream = new FileInputStream(orcZipPath);
            long importOrcId = 0L;
            try {
                Map<String, Object> uploadMap = bmlService.upload(userName, inputStream, "default_orc.zip", projectVo.getName());
                String resourceId = uploadMap.get("resourceId").toString();
                String version = uploadMap.get("version").toString();
                //?????????release???importservice?????? ??????dev????????????import??????
                importOrcId = migrateService.importOrcToOrchestrator(resourceId, version, projectVo, userName, "dev", workspace, orchestratorInfo);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
            if (importOrcId <= 0) {
                LOG.error("Invalid orcId : {} after imported. User : {}, workspace : {}, projectName : {}, projectId : {}", importOrcId, userName, workspaceName, projectName, projectVo.getId());
            }

            // ??????flowId???bmlVersion????????????
            DSSOrchestratorVersion orchestratorLatestVersion = getLatestOrchestratorVersion(projectVo.getId(), importOrcId, userName);
            if (null != orchestratorLatestVersion) {
                responseMsg.data(FLOW_ID_KEY, orchestratorLatestVersion.getAppId());
                String flowLatestBmlVersion = getLatestFlowBmlVersion(userName, orchestratorLatestVersion.getAppId());
                responseMsg.data(FLOW_BML_VERSION_KEY, flowLatestBmlVersion);
            } else {
                LOG.error("Got null orchestrator version after imported. User : {}, workspace : {}, projectName : {}, projectId : {}", importOrcId, userName, workspaceName, projectName, projectVo.getId());
                responseMsg.data(FLOW_ID_KEY, null);
                responseMsg.data(FLOW_BML_VERSION_KEY, null);
            }

        } catch (Exception e) {
            String errorMsg = "project import failed for:" + e.getMessage();
            Integer errorCode = 40035;
            if (e instanceof DSSProjectErrorException) {
                DSSProjectErrorException errorException = (DSSProjectErrorException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            } else if (e instanceof MigrateErrorException) {
                MigrateErrorException errorException = (MigrateErrorException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            } else if (e instanceof LinkisException) {
                LinkisException errorException = (LinkisException) e;
                errorCode = errorException.getErrCode();
                errorMsg = errorException.getDesc();
            }
            LOG.error("fatal error:{}", e.getMessage(), e);
            throw new MigrateErrorException(errorCode, errorMsg);
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }
        return responseMsg;
    }

    /**
     * ??????????????????
     * 1??????????????????????????????????????????
     * 2????????????????????????json????????????????????????????????????
     * 3?????????????????????????????????????????????+?????????
     * 4?????????????????????
     * 5??????????????????zip????????????
     * 6?????????????????????
     *
     * @param req            reqeust
     * @param resp           response
     * @param outputFileName ??????????????????
     * @param charset        ???????????????
     * @param outputFileType ??????????????????
     * @param projectName    ????????????
     * @param orchestratorId ??????ID
     * @param orcVersionId   ???????????????ID
     * @param addOrcVersion  ?????????????????????
     * @param labels         ??????
     * @throws DSSErrorException
     * @throws IOException
     */
    @RequestMapping(path = "exportOrcSqlFile", method = RequestMethod.GET)
    public void exportOrcSqlFile(HttpServletRequest req,
                                 HttpServletResponse resp,
                                 @RequestParam(defaultValue = "exportOrc", required = false, name = "outputFileName") String outputFileName,
                                 @RequestParam(defaultValue = "utf-8", required = false, name = "charset") String charset,
                                 @RequestParam(defaultValue = "zip", required = false, name = "outputFileType") String outputFileType,
                                 @RequestParam(name = "projectName") String projectName,
                                 @RequestParam(name = "orchestratorId") Long orchestratorId,
                                 @RequestParam(required = false, name = "orcVersionId") Long orcVersionId,
                                 @RequestParam(defaultValue = "false", required = false, name = "addOrcVersion") Boolean addOrcVersion,
                                 @RequestParam(required = false, name = "labels") String labels) throws DSSErrorException, IOException {

        resp.addHeader("Content-Disposition", "attachment;filename="
                + new String(outputFileName.getBytes("UTF-8"), "ISO8859-1") + "." + outputFileType);
        resp.setCharacterEncoding(charset);
        Workspace workspace = SSOHelper.getWorkspace(req);
        String userName = SecurityFilter.getLoginUsername(req);
        List<DSSLabel> dssLabelList = getDSSLabelList(labels);
        ResponseExportOrchestrator exportResponse = null;
        OrchestratorVo orchestratorVo;
        if (orcVersionId != null) {
            orchestratorVo = (OrchestratorVo) orchestratorSender.ask(new RequestQueryByIdOrchestrator(orchestratorId, orcVersionId));
        } else {
            orchestratorVo = (OrchestratorVo) orchestratorSender.ask(new RequestQueryByIdOrchestrator(orchestratorId, null));
        }
        orcVersionId = orchestratorVo.getDssOrchestratorVersion().getId();
        LOG.info("export orchestrator orchestratorId " + orchestratorId + ",orcVersionId:" + orcVersionId);
        try {
            RequestExportOrchestrator requestExportOrchestrator = new RequestExportOrchestrator(
                    userName, orchestratorId, orcVersionId, projectName, dssLabelList, addOrcVersion, workspace);
            exportResponse = (ResponseExportOrchestrator) orchestratorSender.ask(requestExportOrchestrator);
        } catch (Exception e) {
            LOG.error("export orchestrator failed for ", e);
            throw new DSSErrorException(100789, "export orchestrator failed for " + e.getMessage());
        }
        if (null != exportResponse) {
            // ??????????????????
            String pathRoot = IoUtils.generateIOPath(userName, "project", "");
            // ???????????????????????????
            String zipFilePath = bmlService.downloadToLocalPath(userName, exportResponse.resourceId(), exportResponse.version(), pathRoot + "project.zip");
            // ??????????????????
            ZipHelper.unzip(zipFilePath);
            // ??????????????????????????????
            String projectBasePath = pathRoot + "default_orc";
            // ???????????????zip??????
            String orcFlowZipFile = projectBasePath + File.separator + "orc_flow.zip";
            ZipHelper.unzip(orcFlowZipFile);
            // ?????????????????????????????????
            String flowBasePath = projectBasePath + File.separator + projectName;
            // ?????????????????????
            List<DSSFlow> dssFlows = metaService.readFlow(flowBasePath);
            // ?????????????????????????????????
            JsonToFlowParser jsonToFlowParser = WorkflowFactory.INSTANCE.getJsonToFlowParser();
            List<String> uploadResourceNewFileNameList = new ArrayList<>();
            for (DSSFlow dssFlow : dssFlows) {
                String workflowPath = flowBasePath + File.separator + dssFlow.getName() + File.separator;
                String flowJsonFile = workflowPath + dssFlow.getName() + ".json";
                String jsonContent = FileHelper.readFile(flowJsonFile);

                if (StringUtils.isNotBlank(jsonContent)) {
                    //???json?????????string?????????workflow
                    dssFlow.setFlowJson(jsonContent);
                    // ???????????????????????????
                    Workflow workflow = jsonToFlowParser.parse(dssFlow);
                    // ???????????????????????????????????????
                    List<Resource> uploadResources = workflow.getFlowResources();
                    if (uploadResources != null && uploadResources.size() > 0) {
                        for (Resource uploadResource : uploadResources) {
                            String oldUploadResourceName = workflowPath + "resource" + File.separator + uploadResource.getResourceId() + ".re";
                            String newUploadResourceName = workflowPath + "resource" + File.separator + uploadResource.getFileName();
                            FileUtils.getFile(oldUploadResourceName).renameTo(new File(newUploadResourceName));
                            uploadResourceNewFileNameList.add(newUploadResourceName);
                        }
                    }
                    // ????????????sql???????????????????????????
                    workflow.getWorkflowNodes().forEach(workflowNode -> {
                        List<Resource> resources = workflowNode.getDSSNode().getResources();
                        if (resources != null && resources.size() > 0) {
                            for (Resource resource : resources) {
                                String oldSqlFilePath = workflowPath + "resource" + File.separator + resource.getResourceId() + "_" + resource.getVersion() + ".re";
                                String newSqlFilePath = workflowPath + "resource" + File.separator + workflowNode.getDSSNode().getName() + "_" + resource.getVersion() + ".re";
                                FileUtils.getFile(oldSqlFilePath).renameTo(new File(newSqlFilePath));
                            }
                        }
                    });
                }
            }
            //??????????????????????????????zip???,.json,.txt,.properties
            List<String> fileNameList = new ArrayList<>();
            FileHelper.getAllFileNames(pathRoot, fileNameList);
            fileNameList.forEach(fileName -> {
                if (fileName.endsWith(".zip") || fileName.endsWith(".json") || fileName.endsWith(".txt") || fileName.endsWith(".properties")) {
                    // ?????????????????????????????????
                    if (uploadResourceNewFileNameList.stream().noneMatch(fileName::equals)) {
                        new File(fileName).delete();
                    }
                }
            });
            //???????????????zip????????????
            String orcZipPath = ZipHelper.zip(flowBasePath, true);
            try (InputStream inputStream = new FileInputStream(orcZipPath)) {
                IOUtils.copy(inputStream, resp.getOutputStream());
                resp.getOutputStream().flush();
            } catch (IOException e) {
                LOG.error("????????????????????????????????????????????????{}", orcZipPath, e);
                throw new DSSErrorException(100800, "??????????????????????????????:????????? " + e.getMessage());
            }
        }
    }


    //??????label list
    private List<DSSLabel> getDSSLabelList(String labels) {
        String labelStr = DSSCommonUtils.ENV_LABEL_VALUE_DEV;
        try {
            Map<String, Object> labelMap = DSSCommonUtils.COMMON_GSON.fromJson(labels, Map.class);
            if (labelMap.containsKey(LabelKeyConvertor.ROUTE_LABEL_KEY)) {
                labelStr = (String) labelMap.get(LabelKeyConvertor.ROUTE_LABEL_KEY);
            }
        } catch (Exception e) {
            LOG.error("get labels failed for {}", e.getMessage());
        }
        List<DSSLabel> dssLabelList = Arrays.asList(new EnvDSSLabel(labelStr));
        return dssLabelList;
    }

    /**
     * ?????????????????????
     *
     * @param projectName
     * @param projectUserJson {"editUsers":["alexyang"],"accessUsers":["alexyang"],"releaseUsers":["alexyang"]}
     * @return
     * @throws MigrateErrorException
     */
    private DSSProjectDO getOrCreateProject(String projectName, String projectUserJson, String username, Workspace workspace, long workspaceId) throws Exception {
        if (StringUtils.isBlank(projectName) || StringUtils.isBlank(username)) {
            throw new MigrateErrorException(0, "Invalid projectName : " + projectName + " , or username : " + username);
        }
        DSSProjectDO project = dssProjectService.getProjectByName(projectName);
        if (null == project) {
            ProjectCreateRequest request = new ProjectCreateRequest();
            request.setName(projectName);
            request.setApplicationArea(DEFAULT_PROJECT_AREA);
            request.setWorkspaceId(workspaceId);
            request.setWorkspaceName(workspace.getWorkspaceName());
            request.setDescription("dss auto test.");

            Map<String, Object> userMap = BDPJettyServerHelper.gson().fromJson(projectUserJson, HashMap.class);
            Set<String> accessUsers = new HashSet<>(1);
            accessUsers.add(username);
            if (userMap.containsKey(PROJECT_ACCESS_USER_KEY)) {
                List<String> acceList = (List<String>) userMap.get(PROJECT_ACCESS_USER_KEY);
                accessUsers.addAll(acceList);
                acceList.clear();
                acceList.addAll(accessUsers);
                request.setAccessUsers(acceList);
            }
            Set<String> editUsers = new HashSet<>(1);
            editUsers.add(username);
            if (userMap.containsKey(PROJECT_EDIT_USER_KEY)) {
                List<String> editList = (List<String>) userMap.get(PROJECT_EDIT_USER_KEY);
                editUsers.addAll(editList);
                editList.clear();
                editList.addAll(editUsers);
                request.setEditUsers(editList);
            }
            Set<String> releaseUsers = new HashSet<>(1);
            releaseUsers.add(username);
            if (userMap.containsKey(PROJECT_RELEASE_USER_KEY)) {
                List<String> releList = (List<String>) userMap.get(PROJECT_RELEASE_USER_KEY);
                releaseUsers.addAll(releList);
                releList.clear();
                releList.addAll(releaseUsers);
            }
            request.setDevProcessList(Lists.newArrayList("dev", "prod"));
            request.setOrchestratorModeList(Lists.newArrayList("pom_work_flow"));
            DSSProjectVo projectVo = dssFrameworkProjectService.createProject(request, username, workspace);
            project = dssProjectService.getProjectByName(projectName);
        }
        return project;
    }

    private DSSOrchestratorVersion getLatestOrchestratorVersion(Long projectId, Long orcId, String userName) {
        RequestOrchestratorVersion requestOrchestratorVersion = new RequestOrchestratorVersion();
        requestOrchestratorVersion.setProjectId(projectId);
        requestOrchestratorVersion.setOrchestratorId(orcId);
        requestOrchestratorVersion.setUsername(userName);
        ResponseOrchetratorVersion orchetratorVersion = (ResponseOrchetratorVersion) orchestratorSender.ask(requestOrchestratorVersion);
        DSSOrchestratorVersion orchestratorLatestVersion = orchetratorVersion.getOrchestratorVersions().stream()
                .filter((v) -> v.getValidFlag() == 1).sorted(new Comparator<DSSOrchestratorVersion>() {
                    @Override
                    public int compare(DSSOrchestratorVersion o1, DSSOrchestratorVersion o2) {
                        // ???????????????
                        if (o1.getVersion().compareToIgnoreCase(o2.getVersion()) < 0) {
                            return 1;
                        } else if (o1.getVersion().compareToIgnoreCase(o2.getVersion()) == 0) {
                            return 0;
                        } else {
                            return -1;
                        }
                    }
                }).findFirst().get();
        return orchestratorLatestVersion;
    }

    private String getLatestFlowBmlVersion(String username, long flowId) {
        RequestQueryWorkFlow requestQueryWorkFlow = new RequestQueryWorkFlow(username, flowId);
        ResponseQueryWorkflow responseQueryWorkflow = (ResponseQueryWorkflow) workflowSender.ask(requestQueryWorkFlow);
        if (null != responseQueryWorkflow && null != responseQueryWorkflow.getDssFlow()) {
            return responseQueryWorkflow.getDssFlow().getBmlVersion();
        } else {
            return null;
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????project server???
     *
     * @param req
     * @param response
     * @return
     * @throws Exception
     */
    @RequestMapping(path = "/exportallflow", method = RequestMethod.POST)
    public Message exportAllFlowInProject(HttpServletRequest req,
                                          HttpServletResponse response,
                                          @RequestParam(name = "workspaceName") String workspaceName,
                                          @RequestParam(name = "projectName") String projectName,
                                          @RequestParam(name = "pathRoot") String pathRoot) throws Exception {
        String username = SecurityFilter.getLoginUsername(req);

        // 1????????????????????????????????????
        // ??????????????????id??????????????????
        List<DSSWorkspace> workspaces = dssWorkspaceService.getWorkspaces(username);
        long workspaceId = 0l;
        DSSWorkspace dssWorkspace = workspaces.stream().filter((w) -> w.getName().equals(workspaceName)).findFirst().get();
        if (null == dssWorkspace) {
            LOG.error("Cannot find workspace {}, please create it first.", workspaceName);
            throw new MigrateErrorException(0, "Cannot find workspace " + workspaceName);
        }
        workspaceId = dssWorkspace.getId();
        Workspace workspace = SSOHelper.setAndGetWorkspace(req, response, workspaceId, workspaceName);
        // ????????????id??????????????????
        if (StringUtils.isBlank(projectName) || StringUtils.isBlank(username)) {
            throw new MigrateErrorException(0, "Invalid projectName : " + projectName + " , or username : " + username);
        }
        DSSProjectDO projectDo = dssProjectService.getProjectByName(projectName);
        if (null == projectDo) {
            throw new MigrateErrorException(0, "cannot find projectName : " + projectName + "  in workspace : " + workspaceName);
        }
        // ?????????????????????
        RequestOrchestratorInfos requestOrchestratorInfos = new RequestOrchestratorInfos();
        requestOrchestratorInfos.setWorkspaceId(workspaceId);
        requestOrchestratorInfos.setProjectId(projectDo.getId());
        // mod???????????????
        Arrays.stream(projectDo.getOrchestratorMode().split(",")).forEach((mode) -> {
            if (StringUtils.isNotBlank(mode)) {
                requestOrchestratorInfos.setOrchestratorMode(mode);
            }
        });
        if (StringUtils.isBlank(requestOrchestratorInfos.getOrchestratorMode())) {
            requestOrchestratorInfos.setOrchestratorMode(DEFAULT_PROJECT_ORCHESTRATOR_MODE);
        }
        ResponseOrchestratorInfos responseOrchestratorInfos = (ResponseOrchestratorInfos) orchestratorSender.ask(requestOrchestratorInfos);
        int count = 0;
        if (CollectionUtils.isNotEmpty(responseOrchestratorInfos.getOrchestratorInfos())) {
            for (DSSOrchestratorInfo orchestratorInfo : responseOrchestratorInfos.getOrchestratorInfos()) {
                List<DSSLabel> devLabels = Lists.newArrayList(new EnvDSSLabel("dev"));
                // ????????????Orchestrator??????
                RequestOrchestratorVersion requestOrchestratorVersion = new RequestOrchestratorVersion();
                requestOrchestratorVersion.setUsername(username);
                requestOrchestratorVersion.setOrchestratorId(orchestratorInfo.getId());
                requestOrchestratorVersion.setProjectId(orchestratorInfo.getProjectId());
                ResponseOrchetratorVersion responseOrchetratorVersion = null;
                try {
                    responseOrchetratorVersion = (ResponseOrchetratorVersion) orchestratorSender.ask(requestOrchestratorVersion);
                } catch (Exception e) {
                    DSSExceptionUtils.dealErrorException(60015, "Ask orchestrotor version failed " + BDPJettyServerHelper.gson().toJson(requestOrchestratorVersion), e,
                            DSSErrorException.class);
                }
                long latestVersionId = responseOrchetratorVersion.getOrchestratorVersions().stream().map((version) -> version.getId()).max(
                        new Comparator<Long>() {
                            @Override
                            public int compare(Long o1, Long o2) {
                                // ???????????????
                                if (o1 < o2) {
                                    return -1;
                                } else if (o1 == o2) {
                                    return 0;
                                } else {
                                    return 1;
                                }
                            }
                        }
                ).get();
                exportFlow(username, workspace, projectName, orchestratorInfo.getId(), latestVersionId,
                        false, devLabels, orchestratorInfo.getName(), pathRoot);
                count += 1;
            }
        }
        Message respMsg = Message.ok();
        respMsg.data("count", count);
        respMsg.data("location", pathRoot);
        respMsg.data("serviceInstance", Sender.getThisInstance());
        return respMsg;
    }

    private void exportFlow(String username, Workspace workspace, String projectName, long orchestratorId,
                            long orchestratorVersionId, boolean addOrcVersion, List<DSSLabel> labels,
                            String flowName, String pathRoot) throws Exception {
        RequestExportOrchestrator exportRequest = new RequestExportOrchestrator(username,
                orchestratorId, orchestratorVersionId,
                projectName, labels, addOrcVersion, workspace);
        ResponseExportOrchestrator exportResponse = null;
        try {
            exportResponse = (ResponseExportOrchestrator) orchestratorSender.ask(exportRequest);
        } catch (Exception e) {
            DSSExceptionUtils.dealErrorException(60015, "export orchestrator ref failed " + BDPJettyServerHelper.gson().toJson(exportRequest), e,
                    DSSErrorException.class);
        }
        LOG.info("End to ask to export orchestrator, responseRef is {}", DSSCommonUtils.COMMON_GSON.toJson(exportResponse));
        if (exportResponse == null) {
            LOG.error("exportResponse is null, it means export is failed");
            DSSExceptionUtils.dealErrorException(63323, "exportResponse is null, it means export is failed", DSSErrorException.class);
        }
        String resourceId = exportResponse.resourceId();
        String version = exportResponse.version();
        bmlService.downloadToLocalPath(username, resourceId, version, pathRoot + "/" + projectName + "/" + flowName + ".zip");
    }


}