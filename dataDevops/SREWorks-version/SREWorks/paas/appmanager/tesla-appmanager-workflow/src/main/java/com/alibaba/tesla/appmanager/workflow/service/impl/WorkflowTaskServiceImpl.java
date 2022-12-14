package com.alibaba.tesla.appmanager.workflow.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.tesla.appmanager.autoconfig.ThreadPoolProperties;
import com.alibaba.tesla.appmanager.common.enums.DynamicScriptKindEnum;
import com.alibaba.tesla.appmanager.common.enums.WorkflowTaskStateEnum;
import com.alibaba.tesla.appmanager.common.exception.AppErrorCode;
import com.alibaba.tesla.appmanager.common.exception.AppException;
import com.alibaba.tesla.appmanager.common.pagination.Pagination;
import com.alibaba.tesla.appmanager.common.util.SchemaUtil;
import com.alibaba.tesla.appmanager.domain.req.UpdateWorkflowSnapshotReq;
import com.alibaba.tesla.appmanager.domain.req.workflow.ExecuteWorkflowHandlerReq;
import com.alibaba.tesla.appmanager.domain.res.workflow.ExecuteWorkflowHandlerRes;
import com.alibaba.tesla.appmanager.domain.schema.DeployAppSchema;
import com.alibaba.tesla.appmanager.dynamicscript.core.GroovyHandlerFactory;
import com.alibaba.tesla.appmanager.workflow.dynamicscript.WorkflowHandler;
import com.alibaba.tesla.appmanager.workflow.repository.WorkflowTaskRepository;
import com.alibaba.tesla.appmanager.workflow.repository.condition.WorkflowTaskQueryCondition;
import com.alibaba.tesla.appmanager.workflow.repository.domain.WorkflowInstanceDO;
import com.alibaba.tesla.appmanager.workflow.repository.domain.WorkflowSnapshotDO;
import com.alibaba.tesla.appmanager.workflow.repository.domain.WorkflowTaskDO;
import com.alibaba.tesla.appmanager.workflow.service.WorkflowSnapshotService;
import com.alibaba.tesla.appmanager.workflow.service.WorkflowTaskService;
import com.alibaba.tesla.appmanager.workflow.service.thread.ExecuteWorkflowTaskResult;
import com.alibaba.tesla.appmanager.workflow.service.thread.ExecuteWorkflowTaskWaitingObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Workflow Task ????????????
 *
 * @author yaoxing.gyx@alibaba-inc.com
 */
@Service
@Slf4j
public class WorkflowTaskServiceImpl implements WorkflowTaskService {

    @Autowired
    private WorkflowSnapshotService workflowSnapshotService;

    @Autowired
    private WorkflowTaskRepository workflowTaskRepository;

    @Autowired
    private GroovyHandlerFactory groovyHandlerFactory;

    @Autowired
    private ThreadPoolProperties threadPoolProperties;

    /**
     * Workflow Task ?????????
     */
    private ThreadPoolExecutor threadPoolExecutor;

    private final Object threadPoolExecutorLock = new Object();

    @PostConstruct
    public void init() {
        synchronized (threadPoolExecutorLock) {
            threadPoolExecutor = new ThreadPoolExecutor(
                    threadPoolProperties.getWorkflowTaskCoreSize(),
                    threadPoolProperties.getWorkflowTaskMaxSize(),
                    threadPoolProperties.getWorkflowTaskKeepAlive(), TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(threadPoolProperties.getWorkflowTaskQueueCapacity()),
                    r -> new Thread(r, "workflow-task-" + r.hashCode()),
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
    }

    /**
     * ?????? WorkflowTaskID ??????????????? WorkflowTask ??????
     *
     * @param workflowTaskId WorkflowTaskID
     * @param withExt        ????????????????????????
     * @return WorkflowTask ??????????????????????????? null
     */
    @Override
    public WorkflowTaskDO get(Long workflowTaskId, boolean withExt) {
        WorkflowTaskQueryCondition condition = WorkflowTaskQueryCondition.builder()
                .taskId(workflowTaskId)
                .withBlobs(withExt)
                .build();
        return workflowTaskRepository.getByCondition(condition);
    }

    /**
     * ?????????????????? Workflow ????????????
     *
     * @param condition ????????????
     * @return List of WorkflowTask
     */
    @Override
    public Pagination<WorkflowTaskDO> list(WorkflowTaskQueryCondition condition) {
        List<WorkflowTaskDO> result = workflowTaskRepository.selectByCondition(condition);
        return Pagination.valueOf(result, Function.identity());
    }

    /**
     * ?????????????????????????????????????????? workflow task
     *
     * @return List or WorkflowTaskDO
     */
    @Override
    public List<WorkflowTaskDO> listRunningRemoteTask() {
        return workflowTaskRepository.listRunningRemoteTask();
    }

    /**
     * ??????????????? Workflow ????????????
     *
     * @param task Workflow ????????????
     * @return ????????????
     */
    @Override
    public int update(WorkflowTaskDO task) {
        log.info("action=updateWorkflowTask|workflowTaskId={}|workflowInstanceId={}|appId={}|status={}",
                task.getId(), task.getWorkflowInstanceId(), task.getAppId(), task.getTaskStatus());
        return workflowTaskRepository.updateByPrimaryKey(task);
    }

    /**
     * ???????????? Workflow Task ?????? (?????????, ??? PENDING ??????)
     *
     * @param task Workflow ????????????
     * @return ???????????? WorkflowTask ??????
     */
    @Override
    public WorkflowTaskDO create(WorkflowTaskDO task) {
        workflowTaskRepository.insert(task);
        return get(task.getId(), true);
    }

    /**
     * ?????????????????? Workflow Task ??????????????????????????? (PENDING -> RUNNING)
     *
     * @param instance Workflow ??????
     * @param task     Workflow ??????
     * @param context  ???????????????
     * @return ????????????????????? WorkflowTaskDO ?????? (?????????????????? DO ?????? events ???????????????)
     */
    @Override
    public WorkflowTaskDO execute(WorkflowInstanceDO instance, WorkflowTaskDO task, JSONObject context) {
        synchronized (threadPoolExecutorLock) {
            if (threadPoolExecutor == null) {
                throw new AppException(AppErrorCode.NOT_READY, "system not ready");
            }
        }

        DeployAppSchema configuration = SchemaUtil.toSchema(DeployAppSchema.class, instance.getWorkflowConfiguration());
        ExecuteWorkflowTaskWaitingObject waitingObject = ExecuteWorkflowTaskWaitingObject.create(task.getId());
        threadPoolExecutor.submit(() -> {
            WorkflowHandler handler;
            try {
                handler = groovyHandlerFactory.get(WorkflowHandler.class,
                        DynamicScriptKindEnum.WORKFLOW.toString(), task.getTaskType());
            } catch (Exception e) {
                log.warn("cannot find workflow handler by taskType {}|workflowInstanceId={}|workflowTaskId={}",
                        task.getTaskType(), task.getWorkflowInstanceId(), task.getId());
                ExecuteWorkflowTaskWaitingObject.triggerFinished(
                        task.getId(),
                        ExecuteWorkflowTaskResult.builder().success(false).extMessage(e.toString()).build());
                return;
            }
            ExecuteWorkflowHandlerReq req = ExecuteWorkflowHandlerReq.builder()
                    .appId(task.getAppId())
                    .instanceId(task.getWorkflowInstanceId())
                    .taskId(task.getId())
                    .taskType(task.getTaskType())
                    .taskStage(task.getTaskStage())
                    .taskProperties(JSONObject.parseObject(task.getTaskProperties()))
                    .context(context)
                    .configuration(configuration)
                    .build();
            ExecuteWorkflowHandlerRes res;
            try {
                res = handler.execute(req);
            } catch (InterruptedException e) {
                ExecuteWorkflowTaskWaitingObject.triggerTerminated(task.getId(), "terminated by InterruptedException");
                return;
            } catch (Throwable e) {
                ExecuteWorkflowTaskWaitingObject.triggerFinished(
                        task.getId(),
                        ExecuteWorkflowTaskResult.builder().task(task).success(false).extMessage(e.toString()).build());
                return;
            }
            ExecuteWorkflowTaskWaitingObject.triggerFinished(
                    task.getId(),
                    ExecuteWorkflowTaskResult.builder().task(task).success(true).output(res).build());
        });
        ExecuteWorkflowTaskResult result;

        // ?????? Task ??????????????????
        try {
            result = waitingObject.wait(() -> {
                // ????????????
                WorkflowTaskDO current = get(task.getId(), false);
                int count = workflowTaskRepository.updateByPrimaryKey(current);
                if (count == 0) {
                    log.warn("failed to report workflow task heartbeat because of lock version expired|" +
                            "workflowTaskId={}", task.getId());
                } else {
                    log.info("workflow task has been reported heartbeat|workflowTaskId={}", task.getId());
                }
            }, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return markAbnormalWorkflowTask(task.getId(), WorkflowTaskStateEnum.EXCEPTION, e.toString());
        }

        // ????????????????????????????????????????????????????????? workflow task ???
        if (result.isTerminated()) {
            return markAbnormalWorkflowTask(task.getId(), WorkflowTaskStateEnum.TERMINATED, result.getExtMessage());
        } else if (result.isPaused()) {
            return markAbnormalWorkflowTask(task.getId(), WorkflowTaskStateEnum.RUNNING_SUSPEND, result.getExtMessage());
        } else if (!result.isSuccess()) {
            return markAbnormalWorkflowTask(task.getId(), WorkflowTaskStateEnum.FAILURE, result.getExtMessage());
        }

        // ??????????????????????????? workflow task ?????????????????? suspend??????????????????????????? WAITING_SUSPEND
        ExecuteWorkflowHandlerRes output = result.getOutput();
        WorkflowTaskDO returnedTask = get(task.getId(), true);
        if (output.isSuspend()) {
            returnedTask.setTaskStatus(WorkflowTaskStateEnum.WAITING_SUSPEND.toString());
        } else {
            returnedTask.setTaskStatus(WorkflowTaskStateEnum.SUCCESS.toString());
        }
        returnedTask.setTaskErrorMessage("");
        if (output.getDeployAppId() != null && output.getDeployAppId() > 0) {
            returnedTask.setDeployAppId(output.getDeployAppId());
            returnedTask.setDeployAppUnitId(output.getDeployAppUnitId());
            returnedTask.setDeployAppNamespaceId(output.getDeployAppNamespaceId());
            returnedTask.setDeployAppStageId(output.getDeployAppStageId());
        }

        // ?????? Workflow ??????
        WorkflowSnapshotDO snapshot = workflowSnapshotService.update(UpdateWorkflowSnapshotReq.builder()
                .workflowTaskId(task.getId())
                .workflowInstanceId(task.getWorkflowInstanceId())
                .context(output.getContext())
                .configuration(output.getConfiguration())
                .build());
        log.info("workflow snapshot has updated|workflowInstanceId={}|workflowTaskId={}|workflowSnapshotId={}|" +
                        "context={}", snapshot.getWorkflowInstanceId(), snapshot.getWorkflowTaskId(), snapshot.getId(),
                JSONObject.toJSONString(output.getContext()));
        return returnedTask;
    }

    /**
     * ???????????? Workflow ?????? (x -> TERMINATED)
     *
     * @param workflowTaskId WorkflowTaskID
     * @param extMessage     ???????????????????????????????????????
     */
    @Override
    public boolean terminate(Long workflowTaskId, String extMessage) {
        return ExecuteWorkflowTaskWaitingObject.triggerTerminated(workflowTaskId, extMessage);
    }

    /**
     * ???????????? Workflow ?????? (RUNNING -> RUNNING_SUSPEND)
     *
     * @param workflowTaskId WorkflowTaskID
     * @param extMessage     ???????????????????????????????????????
     */
    @Override
    public void suspend(Long workflowTaskId, String extMessage) {
        ExecuteWorkflowTaskWaitingObject.triggerSuspend(workflowTaskId, extMessage);
    }

    /**
     * ???????????? workflow task ?????? (?????????????????? FAILURE/EXCEPTION/TERMINATED)
     *
     * @param workflowTaskId Workflow ?????? ID
     * @param status         ??????
     * @param errorMessage   ????????????
     */
    private WorkflowTaskDO markAbnormalWorkflowTask(
            Long workflowTaskId, WorkflowTaskStateEnum status, String errorMessage) {
        WorkflowTaskDO task = get(workflowTaskId, true);
        task.setTaskStatus(status.toString());
        task.setTaskErrorMessage(errorMessage);
        return task;
    }
}
