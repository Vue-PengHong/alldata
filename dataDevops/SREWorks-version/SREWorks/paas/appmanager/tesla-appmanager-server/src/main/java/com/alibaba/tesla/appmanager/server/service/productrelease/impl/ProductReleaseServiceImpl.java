package com.alibaba.tesla.appmanager.server.service.productrelease.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.tesla.appmanager.api.provider.AppPackageTaskProvider;
import com.alibaba.tesla.appmanager.common.constants.DefaultConstant;
import com.alibaba.tesla.appmanager.common.enums.ComponentTypeEnum;
import com.alibaba.tesla.appmanager.common.enums.ProductReleaseTaskStatusEnum;
import com.alibaba.tesla.appmanager.common.exception.AppErrorCode;
import com.alibaba.tesla.appmanager.common.exception.AppException;
import com.alibaba.tesla.appmanager.common.service.GitService;
import com.alibaba.tesla.appmanager.common.util.SchemaUtil;
import com.alibaba.tesla.appmanager.domain.req.apppackage.AppPackageTaskCreateReq;
import com.alibaba.tesla.appmanager.domain.req.apppackage.ComponentBinder;
import com.alibaba.tesla.appmanager.domain.req.git.GitFetchFileReq;
import com.alibaba.tesla.appmanager.domain.req.productrelease.*;
import com.alibaba.tesla.appmanager.domain.res.apppackage.AppPackageTaskCreateRes;
import com.alibaba.tesla.appmanager.domain.res.productrelease.CheckProductReleaseTaskRes;
import com.alibaba.tesla.appmanager.domain.res.productrelease.CreateAppPackageTaskInProductReleaseTaskRes;
import com.alibaba.tesla.appmanager.domain.res.productrelease.CreateProductReleaseTaskRes;
import com.alibaba.tesla.appmanager.server.repository.*;
import com.alibaba.tesla.appmanager.server.repository.condition.*;
import com.alibaba.tesla.appmanager.server.repository.domain.*;
import com.alibaba.tesla.appmanager.server.service.productrelease.ProductReleaseService;
import com.alibaba.tesla.appmanager.server.service.productrelease.business.ProductReleaseBO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ????????????????????????
 *
 * @author yaoxing.gyx@alibaba-inc.com
 */
@Slf4j
@Service
public class ProductReleaseServiceImpl implements ProductReleaseService {

    @Autowired
    private ProductReleaseSchedulerRepository productReleaseSchedulerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private ProductReleaseRelRepository productReleaseRelRepository;

    @Autowired
    private ProductReleaseAppRelRepository productReleaseAppRelRepository;

    @Autowired
    private ProductReleaseTaskRepository productReleaseTaskRepository;

    @Autowired
    private ProductReleaseTaskAppPackageTaskRelRepository productReleaseTaskAppPackageTaskRelRepository;

    @Autowired
    private GitService gitService;

    /**
     * ?????????????????????????????? Schduler ??????
     *
     * @return Scheduler List ??????
     */
    @Override
    public List<ProductReleaseSchedulerDO> listScheduler() {
        ProductReleaseSchedulerQueryCondition condition = ProductReleaseSchedulerQueryCondition.builder().build();
        return productReleaseSchedulerRepository.selectByCondition(condition);
    }

    /**
     * ?????????????????????????????? Scheduler
     *
     * @param productId ?????? ID
     * @param releaseId ???????????? ID
     * @return Scheduler ??????
     */
    @Override
    public ProductReleaseSchedulerDO getScheduler(String productId, String releaseId) {
        ProductReleaseSchedulerQueryCondition condition = ProductReleaseSchedulerQueryCondition.builder()
                .productId(productId)
                .releaseId(releaseId)
                .build();
        return productReleaseSchedulerRepository.getByCondition(condition);
    }

    /**
     * ?????? productId ??? releaseId ???????????????????????????????????????????????????????????????
     *
     * @param productId ?????? ID
     * @param releaseId ???????????? ID
     * @return ????????????
     */
    @Override
    public ProductReleaseBO get(String productId, String releaseId) {
        ProductDO product = productRepository.getByCondition(ProductQueryCondition.builder()
                .productId(productId).build());
        ReleaseDO release = releaseRepository.getByCondition(ReleaseQueryCondition.builder()
                .releaseId(releaseId).build());
        ProductReleaseRelDO productReleaseRel = productReleaseRelRepository
                .getByCondition(ProductReleaseRelQueryCondition.builder()
                        .productId(productId)
                        .releaseId(releaseId)
                        .build());
        List<ProductReleaseAppRelDO> appRelList = productReleaseAppRelRepository
                .selectByCondition(ProductReleaseAppRelQueryCondition.builder()
                        .productId(productId)
                        .releaseId(releaseId)
                        .build());

        // ???????????????????????????????????????
        if (product == null || release == null || productReleaseRel == null || CollectionUtils.isEmpty(appRelList)) {
            return null;
        }
        return ProductReleaseBO.builder()
                .product(product)
                .release(release)
                .productReleaseRel(productReleaseRel)
                .appRelList(appRelList)
                .build();
    }

    /**
     * ???????????? productId + releaseId + appId ??? launch yaml ????????????
     *
     * @param productId ?????? ID
     * @param releaseId ???????????? ID
     * @param appId     ?????? ID
     * @return launch yaml ????????????
     */
    @Override
    public String getLaunchYaml(String productId, String releaseId, String appId) {
        ProductDO product = productRepository.getByCondition(ProductQueryCondition.builder()
                .productId(productId)
                .build());
        ProductReleaseAppRelDO appRel = productReleaseAppRelRepository.getByCondition(
                ProductReleaseAppRelQueryCondition.builder()
                        .productId(productId)
                        .releaseId(releaseId)
                        .appId(appId)
                        .build());
        if (product == null || appRel == null) {
            throw new AppException(AppErrorCode.INVALID_USER_ARGS,
                    String.format("cannot find launch yaml|productId=%s|releaseId=%s|appId=%s",
                            productId, releaseId, appId));
        }
        String repo = product.getBaselineGitAddress();
        String ciAccount = product.getBaselineGitUser();
        String ciToken = product.getBaselineGitToken();
        String launchPath = appRel.getBaselineLaunchPath();
        String branch = appRel.getBaselineGitBranch();
        StringBuilder logString = new StringBuilder();
        return gitService.fetchFile(logString, GitFetchFileReq.builder()
                .repo(repo)
                .ciAccount(ciAccount)
                .ciToken(ciToken)
                .branch(branch)
                .filePath(launchPath)
                .build());
    }

    /**
     * ????????????????????????????????????
     *
     * @param request ????????????
     * @return ?????? ID ???????????? ID ??????
     */
    @Override
    public CreateProductReleaseTaskRes createProductReleaseTask(CreateProductReleaseTaskReq request) {
        // ????????????
        String taskId = UUID.randomUUID().toString().replaceAll("-", "");
        ProductReleaseTaskDO taskDO = ProductReleaseTaskDO.builder()
                .productId(request.getProductId())
                .releaseId(request.getReleaseId())
                .taskId(taskId)
                .schedulerType(request.getSchedulerType())
                .schedulerValue(request.getSchedulerValue())
                .status(request.getStatus().toString())
                .build();
        int count = productReleaseTaskRepository.insert(taskDO);
        if (count == 0) {
            throw new AppException(AppErrorCode.UNKNOWN_ERROR, "cannot insert product release task, count=0|task={}",
                    JSONObject.toJSONString(taskDO));
        }
        log.info("product release task has created|taskId={}|request={}", taskId, JSONObject.toJSONString(request));
        return CreateProductReleaseTaskRes.builder()
                .taskId(taskId)
                .build();
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param request ????????????
     * @return ????????????
     */
    @Override
    public CheckProductReleaseTaskRes checkProductReleaseTask(CheckProductReleaseTaskReq request) {
        ProductReleaseTaskQueryCondition condition = ProductReleaseTaskQueryCondition.builder()
                .productId(request.getProductId())
                .releaseId(request.getReleaseId())
                .status(Arrays.asList(
                        ProductReleaseTaskStatusEnum.RUNNING.toString(),
                        ProductReleaseTaskStatusEnum.PENDING.toString()))
                .build();
        List<ProductReleaseTaskDO> tasks = productReleaseTaskRepository.selectByCondition(condition);
        if (tasks.size() == 0) {
            return CheckProductReleaseTaskRes.builder().exists(false).build();
        }
        return CheckProductReleaseTaskRes.builder()
                .exists(true)
                .conflict(tasks.stream().map(ProductReleaseTaskDO::getTaskId).collect(Collectors.toList()))
                .build();
    }

    /**
     * ??????????????????????????????????????????????????????
     *
     * @param request ????????????
     * @return ??????????????????
     */
    @Override
    public List<ProductReleaseTaskDO> listProductReleaseTask(ListProductReleaseTaskReq request) {
        ProductReleaseTaskQueryCondition condition = ProductReleaseTaskQueryCondition.builder()
                .productId(request.getProductId())
                .releaseId(request.getReleaseId())
                .status(request.getStatus())
                .build();
        return productReleaseTaskRepository.selectByCondition(condition);
    }

    /**
     * ?????? ProductRelease Task
     *
     * @param task ??????
     * @return ??????????????????
     */
    @Override
    public int updateProductReleaseTask(ProductReleaseTaskDO task) {
        return productReleaseTaskRepository.updateByCondition(task, ProductReleaseTaskQueryCondition.builder()
                .taskId(task.getTaskId())
                .build());
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????
     *
     * @param request ????????????
     * @return ??????????????????
     */
    @Override
    public List<ProductReleaseTaskAppPackageTaskRelDO> listProductReleaseTaskAppPackageTask(
            ListProductReleaseTaskAppPackageTaskReq request) {
        ProductReleaseTaskAppPackageTaskRelQueryCondition condition =
                ProductReleaseTaskAppPackageTaskRelQueryCondition.builder()
                        .taskId(request.getTaskId())
                        .build();
        return productReleaseTaskAppPackageTaskRelRepository.selectByCondition(condition);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param taskId     ?????? ID
     * @param fromStatus From ??????
     * @param toStatus   To ??????
     */
    @Override
    public void markProductReleaseTaskStatus(
            String taskId, ProductReleaseTaskStatusEnum fromStatus, ProductReleaseTaskStatusEnum toStatus) {
        ProductReleaseTaskQueryCondition condition;
        if (fromStatus != null) {
            condition = ProductReleaseTaskQueryCondition.builder()
                    .taskId(taskId)
                    .status(Collections.singletonList(fromStatus.toString()))
                    .build();
        } else {
            condition = ProductReleaseTaskQueryCondition.builder().taskId(taskId).build();
        }
        ProductReleaseTaskDO task = productReleaseTaskRepository.getByCondition(condition);
        if (task == null) {
            log.info("cannot find product release task by taskId {} and fromStatus {}, skip", taskId, fromStatus);
            return;
        }

        task.setStatus(toStatus.toString());
        int count = productReleaseTaskRepository.updateByCondition(task, condition);
        if (count == 0) {
            log.info("mark product release task failed, skip|taskId={}|count=0|status={}", taskId, toStatus);
        } else {
            log.info("mark product release task success|taskId={}|count=1|status={}", taskId, toStatus);
        }
    }
}
