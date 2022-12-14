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

package com.webank.wedatasphere.dss.appconn.scheduler;

import com.webank.wedatasphere.dss.appconn.core.ext.OnlySSOAppConn;
import com.webank.wedatasphere.dss.appconn.core.ext.OnlyStructureAppConn;
import com.webank.wedatasphere.dss.orchestrator.converter.standard.ConversionIntegrationStandard;
import com.webank.wedatasphere.dss.standard.app.structure.StructureIntegrationStandard;
import com.webank.wedatasphere.dss.workflow.conversion.WorkflowConversionIntegrationStandard;

public interface SchedulerAppConn extends OnlySSOAppConn, OnlyStructureAppConn {

    /**
     * 默认将提供 ProjectConversionIntegrationStandard
     * @return
     */
    ConversionIntegrationStandard getOrCreateConversionStandard();

    @Override
    SchedulerStructureIntegrationStandard getOrCreateStructureStandard();
}
