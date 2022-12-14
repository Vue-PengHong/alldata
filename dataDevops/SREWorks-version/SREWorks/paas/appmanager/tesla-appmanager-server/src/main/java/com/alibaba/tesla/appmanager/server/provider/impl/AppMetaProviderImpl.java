package com.alibaba.tesla.appmanager.server.provider.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.tesla.appmanager.api.provider.AppMetaProvider;
import com.alibaba.tesla.appmanager.common.constants.DefaultConstant;
import com.alibaba.tesla.appmanager.common.enums.AppOptionUpdateModeEnum;
import com.alibaba.tesla.appmanager.common.exception.AppErrorCode;
import com.alibaba.tesla.appmanager.common.exception.AppException;
import com.alibaba.tesla.appmanager.common.pagination.Pagination;
import com.alibaba.tesla.appmanager.common.util.EnvUtil;
import com.alibaba.tesla.appmanager.common.util.RequestUtil;
import com.alibaba.tesla.appmanager.domain.dto.AppDeployEnvironmentDTO;
import com.alibaba.tesla.appmanager.domain.dto.AppMetaDTO;
import com.alibaba.tesla.appmanager.domain.req.AppMetaDeleteReq;
import com.alibaba.tesla.appmanager.domain.req.AppMetaQueryReq;
import com.alibaba.tesla.appmanager.domain.req.AppMetaUpdateReq;
import com.alibaba.tesla.appmanager.meta.k8smicroservice.repository.condition.K8sMicroserviceMetaQueryCondition;
import com.alibaba.tesla.appmanager.meta.k8smicroservice.service.K8sMicroserviceMetaService;
import com.alibaba.tesla.appmanager.server.assembly.AppMetaDtoConvert;
import com.alibaba.tesla.appmanager.server.repository.condition.*;
import com.alibaba.tesla.appmanager.server.repository.domain.AppAddonDO;
import com.alibaba.tesla.appmanager.server.repository.domain.AppMetaDO;
import com.alibaba.tesla.appmanager.server.repository.domain.RtAppInstanceDO;
import com.alibaba.tesla.appmanager.server.service.appaddon.AppAddonService;
import com.alibaba.tesla.appmanager.server.service.appmeta.AppMetaService;
import com.alibaba.tesla.appmanager.server.service.appoption.AppOptionService;
import com.alibaba.tesla.appmanager.server.service.apppackage.AppPackageService;
import com.alibaba.tesla.appmanager.server.service.apppackage.AppPackageTagService;
import com.alibaba.tesla.appmanager.server.service.apppackage.AppPackageTaskService;
import com.alibaba.tesla.appmanager.server.service.componentpackage.ComponentPackageService;
import com.alibaba.tesla.appmanager.server.service.componentpackage.ComponentPackageTaskService;
import com.alibaba.tesla.appmanager.server.service.rtappinstance.RtAppInstanceService;
import com.google.common.base.Enums;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ?????????????????????
 *
 * @author qianmo.zm@alibaba-inc.com
 */
@Slf4j
@Service
public class AppMetaProviderImpl implements AppMetaProvider {

    @Autowired
    private AppMetaService appMetaService;

    @Autowired
    private AppMetaDtoConvert appMetaDtoConvert;

    @Autowired
    private AppAddonService appAddonService;

    @Autowired
    private K8sMicroserviceMetaService k8sMicroserviceMetaService;

    @Autowired
    private AppPackageTagService appPackageTagService;

    @Autowired
    private AppPackageService appPackageService;

    @Autowired
    private AppPackageTaskService appPackageTaskService;

    @Autowired
    private ComponentPackageTaskService componentPackageTaskService;

    @Autowired
    private ComponentPackageService componentPackageService;

    @Autowired
    private AppOptionService appOptionService;

    @Autowired
    private RtAppInstanceService rtAppInstanceService;

    /**
     * ?????????????????????
     */
    @Override
    public Pagination<AppMetaDTO> list(AppMetaQueryReq request, String operator, boolean ignorePermission) {
        // ????????????????????????
        AppMetaQueryCondition condition = AppMetaQueryCondition.builder()
                .appId(request.getAppId())
                .appIdLike(request.getAppIdLike())
                .optionKey(request.getOptionKey())
                .optionValue(request.getOptionValue())
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .pagination(false)  // ????????????
                .withBlobs(request.isWithBlobs())
                .build();
        Pagination<AppMetaDO> metaList = appMetaService.list(condition);

        // ??????????????????????????????
        Pagination<AppMetaDO> permittedMetaList;
        if (!ignorePermission) {
            Set<String> userPermittedApps = new HashSet<>(appMetaService.listUserPermittedApp(operator));
            permittedMetaList = Pagination.valueOf(metaList.getItems().stream()
                    .filter(item -> userPermittedApps.contains(item.getAppId()))
                    .collect(Collectors.toList()), Function.identity());
        } else {
            permittedMetaList = metaList;
        }

        // ???????????????????????????
        return Pagination.transform(permittedMetaList, item -> {
            AppMetaDTO dto = appMetaDtoConvert.to(item);
            dto.setOptions(appOptionService.getOptionMap(item.getAppId()));
            dto.setEnvironments(rtAppInstanceService.list(
                            RtAppInstanceQueryCondition.builder()
                                    .appId(item.getAppId())
                                    .pageSize(DefaultConstant.UNLIMITED_PAGE_SIZE)
                                    .build())
                    .getItems()
                    .stream()
                    .map(instance -> AppDeployEnvironmentDTO.builder()
                            .clusterId(instance.getClusterId())
                            .namespaceId(instance.getNamespaceId())
                            .stageId(instance.getStageId())
                            .build())
                    .collect(Collectors.toList()));
            return dto;
        });
    }

    /**
     * ???????????? ID ?????????????????????
     */
    @Override
    public AppMetaDTO get(String appId, String operator) {
        Pagination<AppMetaDTO> results = list(AppMetaQueryReq.builder().appId(appId).build(), operator, true);
        if (results.isEmpty()) {
            return null;
        } else if (results.getTotal() > 1) {
            throw new AppException(AppErrorCode.INVALID_USER_ARGS, "multiple app found with appId " + appId);
        }
        return results.getItems().get(0);
    }

    /**
     * ?????????????????????????????????
     *
     * @param appId    ?????? ID
     * @param operator Operator
     * @return version v1 or v2
     */
    @Override
    public String getFrontendVersion(String appId, String operator) {
        AppMetaDO record = appMetaService.get(AppMetaQueryCondition.builder()
                .appId(appId)
                .build());
        if (record == null) {
            return "v1";
        }
        JSONObject appOptions = appOptionService.getOptionMap(appId);
        if (appOptions == null) {
            return "v1";
        }
        String version = appOptions.getString("version");
        if (StringUtils.isEmpty(version)) {
            return "v1";
        }
        return version;
    }

    /**
     * ???????????? ID ?????????????????????
     */
    @Override
    public boolean delete(AppMetaDeleteReq request, String operator) {
        String appId = request.getAppId();
        if (StringUtils.isEmpty(appId)) {
            return true;
        }

        deleteAppMeta(appId);
        log.info("action=appMetaProvider|deleteAppMeta SUCCESS|appId={}", appId);

        deleteAppAddon(appId);
        log.info("action=appMetaProvider|deleteAppAddon SUCCESS|appId={}", appId);

        deleteK8sMicroServiceMeta(appId);
        log.info("action=appMetaProvider|deleteK8sMicroServiceMeta SUCCESS|appId={}", appId);

        deleteAppPackageTag(appId);
        log.info("action=appMetaProvider|deleteAppPackageTag SUCCESS|appId={}", appId);

        deleteComponentPackageTask(appId);
        log.info("action=appMetaProvider|deleteComponentPackageTask SUCCESS|appId={}", appId);

        deleteComponentPackage(appId);
        log.info("action=appMetaProvider|deleteComponentPackage SUCCESS|appId={}", appId);

        deleteAppPackageTask(appId);
        log.info("action=appMetaProvider|deleteAppPackageTask SUCCESS|appId={}", appId);

        deleteAppPackage(appId);
        log.info("action=appMetaProvider|deleteAppPackage SUCCESS|appId={}", appId);

        if (request.getRemoveAllInstances() != null && request.getRemoveAllInstances()) {
            log.info("action=appMetaProvider|prepare to remove all app instances|appId={}", appId);
            Pagination<RtAppInstanceDO> records = rtAppInstanceService.list(RtAppInstanceQueryCondition.builder()
                    .appId(appId)
                    .build());
            if (!records.isEmpty()) {
                records.getItems().forEach(item -> {
                    rtAppInstanceService.delete(item.getAppInstanceId());
                    log.info("action=appMetaProvider|deleteAppInstance SUCCESS|appId={}|appInstanceId={}",
                            appId, item.getAppInstanceId());
                });
            }
        }
        log.info("action=appMetaProvider|deleteAppInstance SUCCESS|all succeed|appId={}", appId);

        return true;
    }

    private void deleteAppMeta(String appId) {
        AppMetaQueryCondition condition = AppMetaQueryCondition.builder().appId(appId).build();
        appMetaService.delete(condition);
        appOptionService.deleteOptions(appId);
    }

    private void deleteAppAddon(String appId) {
        AppAddonQueryCondition condition = AppAddonQueryCondition.builder().appId(appId).build();
        Pagination<AppAddonDO> records = appAddonService.list(condition);
        records.getItems().forEach(item -> appAddonService.delete(AppAddonQueryCondition.builder()
                .appId(item.getAppId())
                .addonTypeList(Collections.singletonList(item.getAddonType()))
                .addonId(item.getAddonId())
                .addonName(item.getName())
                .build()));
    }

    private void deleteK8sMicroServiceMeta(String appId) {
        K8sMicroserviceMetaQueryCondition condition = K8sMicroserviceMetaQueryCondition.builder()
                .appId(appId)
                .build();
        k8sMicroserviceMetaService.delete(condition);
    }

    private void deleteComponentPackageTask(String appId) {
        ComponentPackageTaskQueryCondition condition = ComponentPackageTaskQueryCondition.builder().appId(appId)
                .build();
        componentPackageTaskService.delete(condition);
    }

    private void deleteAppPackage(String appId) {
        AppPackageQueryCondition condition = AppPackageQueryCondition.builder().appId(appId).build();
        appPackageService.delete(condition);
    }

    private void deleteAppPackageTask(String appId) {
        AppPackageTaskQueryCondition condition = AppPackageTaskQueryCondition.builder().appId(appId).build();
        appPackageTaskService.delete(condition);
    }

    private void deleteComponentPackage(String appId) {
        ComponentPackageQueryCondition condition = ComponentPackageQueryCondition.builder().appId(appId).build();
        componentPackageService.delete(condition);
    }

    private void deleteAppPackageTag(String appId) {
        AppPackageTagQueryCondition condition = AppPackageTagQueryCondition.builder().appId(appId).build();
        appPackageTagService.delete(condition);
    }

    /**
     * ?????????????????????
     */
    @Override
    public AppMetaDTO save(AppMetaUpdateReq request, String operator) {
        String appId = request.getAppId();
        JSONObject options = request.getOptions();

        // ??????????????????????????????????????????????????????
        AppMetaQueryCondition condition = AppMetaQueryCondition.builder().appId(appId).build();
        if (appMetaService.get(condition) == null) {
            appMetaService.create(AppMetaDO.builder().appId(appId).build());
        }

        // ?????? Options
        AppOptionUpdateModeEnum mode = Enums.getIfPresent(AppOptionUpdateModeEnum.class,
                request.getMode().toUpperCase()).orNull();
        if (mode == null) {
            throw new AppException(AppErrorCode.INVALID_USER_ARGS, "invalid parameter mode " + request.getMode());
        }
        appOptionService.updateOptions(appId, options, mode);

        // ??????????????????
        if (!EnvUtil.isSreworks()) {
            try {
                JSONObject body = new JSONObject();
                body.put("admins", new JSONArray());
                body.put("appId", appId);
                body.put("options", new JSONObject());
                body.put("templateName", "blank_app");
                body.put("version", 0);
                body.put("environments", new JSONArray());
                body.getJSONArray("environments").add("default,daily");
                JSONObject headers = new JSONObject();
                headers.put("X-BizTenant", "alibaba");
                headers.put("X-EmpId", operator);
                headers.put("X-Biz-App", String.format("%s,default,daily", appId));
                String bodyStr = JSONObject.toJSONString(body);
                String response = RequestUtil.post("http://productops.internal.tesla.alibaba-inc.com/apps/init",
                        new JSONObject(), bodyStr, headers);
                log.info("create app init response|operator={}|appId={}|body={}|headers={}|response={}",
                        operator, appId, bodyStr, JSONObject.toJSONString(headers), response);
            } catch (Exception e) {
                log.warn("init frontend failed, skip|appId={}|exception={}", appId, ExceptionUtils.getStackTrace(e));
            }
        }
        return get(appId, operator);
    }
}
