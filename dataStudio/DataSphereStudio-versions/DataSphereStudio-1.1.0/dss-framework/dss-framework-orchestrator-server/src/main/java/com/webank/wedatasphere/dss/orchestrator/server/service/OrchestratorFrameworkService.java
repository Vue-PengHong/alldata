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

package com.webank.wedatasphere.dss.orchestrator.server.service;

import com.webank.wedatasphere.dss.orchestrator.server.entity.request.OrchestratorCreateRequest;
import com.webank.wedatasphere.dss.orchestrator.server.entity.request.OrchestratorDeleteRequest;
import com.webank.wedatasphere.dss.orchestrator.server.entity.request.OrchestratorModifyRequest;
import com.webank.wedatasphere.dss.orchestrator.server.entity.vo.CommonOrchestratorVo;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;


public interface OrchestratorFrameworkService {

    CommonOrchestratorVo createOrchestrator(String username, OrchestratorCreateRequest orchestratorCreateRequest, Workspace workspace) throws Exception;

    CommonOrchestratorVo modifyOrchestrator(String username, OrchestratorModifyRequest orchestratorModifyRequest, Workspace workspace) throws Exception;

    CommonOrchestratorVo deleteOrchestrator(String username, OrchestratorDeleteRequest orchestratorDeleteRequest, Workspace workspace) throws Exception;

}
