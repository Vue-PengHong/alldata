<template>
  <div class="workflow-wrap">
    <WorkflowTabList
      :textColor="textColor"
      :tabName="tabName"
      :topTabList="topTabList"
      :buttonText="selectDevprocess"
      :bottomTapList="tabList"
      :modeName="modeName"
      :currentTab="current"
      @bandleTapTab="onTabClick"
      @handleTabRemove="onTabRemove"
      @handleChangeButton="handleChangeButton"
      @selectProject="selectProject"
      @menuHandleChangeButton="menuHandleChangeButton"
    >
      <template v-if="modeOfKey === DEVPROCESS.DEVELOPMENTCENTER">
        <div class="workflowListContainer" v-show="textColor || tabList.length < 1">
          <Workflow
            class="workflowListLeft"
            ref="workflow"
            :projectData="currentProjectData"
            :orchestratorModeList="orchestratorModeList"
            :currentMode="currentMode"
            :selectOrchestratorList="selectOrchestratorList"
            @open-workflow="openWorkflow"
            @publishSuccess="publishSuccess">
            <Tabs class="tabs-content" slot="tagList" v-model="currentMode">
              <TabPane v-for="item in selectOrchestratorList"  :label="item.dicName" :key="item.dicKey" :name="item.dicKey">
              </TabPane>
            </Tabs>
          </Workflow>
        </div>
        <template v-for="(item, index) in tabList.filter((i) => i.type === DEVPROCESS.DEVELOPMENTCENTER)">
          <process
            v-if="item.orchestratorMode === ORCHESTRATORMODES.WORKFLOW"
            :key="item.tabId"
            v-show="(item.version ? (currentVal.name===item.name && currentVal.version === item.version) : currentVal.name===item.name) && !textColor"
            :query="item.query"
            @updateWorkflowList="updateWorkflowList"
            @isChange="isChange(index, arguments)"
          ></process>
          <makeUp
            v-else
            v-show="(item.version ? (currentVal.name===item.name && currentVal.version === item.version) : currentVal.name===item.name) && !textColor"
            :key="item.name"
            :currentVal="currentVal"></makeUp>
        </template>
      </template>
      <template v-else>
      <!-- ?????????????????? -->
      </template>
    </WorkflowTabList>
    <ProjectForm
      ref="projectForm"
      action-type="modify"
      :project-data="currentProjectData"
      :add-project-show="ProjectShow"
      :applicationAreaMap="applicationAreaMap"
      :framework="true"
      :orchestratorModeList="orchestratorModeList"
      @getDevProcessData="getDevProcessData"
      @show="ProjectShowAction"
      @confirm="ProjectConfirm"></ProjectForm>
    <Spin
      v-if="loading"
      size="large"
      fix/>
  </div>
</template>
<script>
import projectDb from '@/apps/workflows/service/db/project.js';
import Workflow from '@/apps/workflows/module/workflow';
import Process from '@/apps/workflows/module/process';
import storage from '@/common/helper/storage';
import WorkflowTabList from '@/apps/workflows/module/common/tabList/index.vue';
import MakeUp from '@/apps/workflows/module/makeUp'
import ProjectForm from '@/components/projectForm/index.js'
import api from '@/common/service/api';
import mixin from '@/common/service/mixin';
import { DEVPROCESS, ORCHESTRATORMODES } from '@/common/config/const.js';
import { GetDicSecondList, GetAreaMap } from '@/common/service/apiCommonMethod.js';
export default {
  components: {
    Workflow: Workflow.component,
    process: Process.component,
    WorkflowTabList,
    makeUp: MakeUp.component,
    ProjectForm
  },
  mixins: [mixin],
  data() {
    return {
      textColor: '#2D8CF0',
      indexLabel: h => {
        return h("span", {}, "??????");
      },
      tabList: [],
      currentVal: { name: "index", version: "index" },
      lastVal: null,
      current: null,
      modeOfKey: '',
      modeName: "???????????????",
      tabName: '',
      topTabList: undefined,
      currentMode: null,
      ProjectShow: false,
      applicationAreaMap: [],
      orchestratorModeList: {},
      currentProjectData: {
        name: '',
        description: '',
        business: '',
        applicationArea: '',
        product: '',
        editUsers: [],
        accessUsers: [],
        devProcessList: [],
        orchestratorModeList: [],
      },
      selectOrchestratorList: [],
      devProcessBase: [],
      selectDevprocess: [],
      DEVPROCESS,
      ORCHESTRATORMODES,
      loading: false
    };
  },
  watch: {
    currentVal(val, oldVal) {
      this.lastVal = oldVal;
      this.currentVal = val;
      if(this.modeOfKey === 'dev') this.updateProjectCache();
    },
    "$route.query.projectID"() {
      this.tabList = [];
      this.lastVal = null;
      this.getAreaMap();
      this.getProjectData();
    },
    selectOrchestratorList(val) {
      if (val.length > 0) {
        this.currentMode = val[0].dicKey;
      }
    }
  },
  created() {
    this.getAreaMap();
    this.getDicSecondList();
  },
  mounted() {
    // this.getCache();
    let workspaceId = this.$route.query.workspaceId;

    let currentWorkspaceName = this.getCurrentWorkspaceName();
    let projectName = this.$route.query.projectName;
    this.topTabList = [
      { name: currentWorkspaceName, url: `/workspaceHome?workspaceId=${workspaceId}` },
      { name: projectName, url: `` }
    ]
    if (this.$route.query && this.$route.query.flowId) {
      this.openWorkflow(this.$route.query)
    }
  },
  computed: {
  },
  methods: {
    // ??????????????????????????????
    getDevProcessData(data) {
      this.devProcessBase = data;
      this.getSelectDevProcess();
    },
    // ?????????????????????????????????
    getSelectDevProcess() {
      this.selectDevprocess = this.devProcessBase ? this.devProcessBase.filter((item) => this.currentProjectData.devProcessList.includes(item.dicValue)) : []
    },
    // ??????????????????
    getSelectOrchestratorList() {
      this.selectOrchestratorList = this.orchestratorModeList.list ? this.orchestratorModeList.list.filter((item) => this.currentProjectData.orchestratorModeList.includes(item.dicKey)) : [];
    },
    // ?????????????????????????????????
    getDicSecondList() {
      GetDicSecondList(this.$route.query.workspaceId).then((res) => {
        this.orchestratorModeList = res.list
        this.getProjectData();
      })
    },
    // ???????????????????????????
    getAreaMap() {
      GetAreaMap().then(res => {
        this.applicationAreaMap = res.applicationAreas;
      });
    },
    // ?????????????????????
    getProjectData() {
      api.fetch(`${this.$API_PATH.PROJECT_PATH}getAllProjects`, {
        workspaceId: +this.$route.query.workspaceId,
        id: +this.$route.query.projectID
      }, 'post').then((res) => {
        this.currentProjectData = res.projects[0];
        this.getSelectDevProcess();
        this.getSelectOrchestratorList();
        this.loading = false;
      })
    },
    // ?????????????????? || ????????????
    ProjectConfirm(projectData) {
      this.currentProjectData = {...projectData};
      this.getSelectDevProcess();
      this.getSelectOrchestratorList();
      projectData.workspaceId = +this.$route.query.workspaceId;
      this.loading = true;
      const projectParams = {
        name: projectData.name,
        id: projectData.id,
        applicationArea: projectData.applicationArea,
        business: projectData.business,
        editUsers: projectData.editUsers,
        accessUsers: projectData.accessUsers,
        releaseUsers: projectData.releaseUsers,
        description: projectData.description,
        product: projectData.product,
        workspaceId: projectData.workspaceId,
        devProcessList: projectData.devProcessList,
        orchestratorModeList: projectData.orchestratorModeList
      }
      api.fetch(`${this.$API_PATH.PROJECT_PATH}modifyProject`, projectParams, 'post').then(() => {
        this.getProjectData();
        this.$Message.success(this.$t('message.workflow.projectDetail.eidtorProjectSuccess', { name: projectData.name }));
      }).catch(() => {
        this.loading = false;
        this.currentProjectData.business = this.$refs.projectForm.originBusiness;
      });
    },
    ProjectShowAction(val) {
      this.ProjectShow = val;
    },
    // ???????????????????????????
    menuHandleChangeButton() {
      // ????????????????????????????????????????????????
      // ????????????????????????
      if (!this.checkEditable(this.currentProjectData, this.getUserName())) return this.$Message.warning(this.$t('message.orchestratorModes.noEditPermission'))
      this.ProjectShow = true;
    },
    /**
     * ????????????????????????????????????????????????tab??????
     * parama ??????????????????????????????
     */
    openWorkflow(params) {
      if(this.loading) return;
      // ?????????????????????????????????????????????????????????????????????tab??????
      const isIn = this.tabList.find(item => item.id === params.id && item.version === params.version);
      if (!isIn || isIn === -1) {
        if (this.tabList.length >= 10) return this.$Message.warning(this.$t('message.orchestratorModes.openTabTen'));

        // ??????????????????????????????????????????????????????????????????
        if (params.readonly && params.readonly === 'true') {
          this.openItemAction(params);
        } else {
          // ???????????????????????????????????????????????????????????????
          this.loading = true;
          api.fetch(`${this.$API_PATH.ORCHESTRATOR_PATH}openOrchestrator`, {
            orchestratorId: params.id,
            labels: {route: this.modeOfKey},
            workspaceName: this.getCurrentWorkspaceName()
          }, 'post')
            .then((openOrchestrator) => {
              this.loading = false;
              if (openOrchestrator) {
                params.appId = openOrchestrator.OrchestratorVo.dssOrchestratorVersion.appId;// ??????id??????????????????id
                // ???????????????id
                params.orchestratorVersionId = openOrchestrator.OrchestratorVo.dssOrchestratorVersion.id;
                this.openItemAction(params);
              } else {
                return this.$Message.warning(this.$t('message.orchestratorModes.FailedOpenOrchestrator'))
              }
            });
        }
      } else {// ??????????????????
        this.lastVal = this.currentVal;
        this.currentVal = isIn;
        this.tabName = params.id + params.version;
        // ??????????????????textColor????????????tab???????????????
        this.textColor = '';
      }

    },
    openItemAction(params) {
      params.workspaceId = this.getCurrentWorkspaceId()
      params.workspaceName = this.getCurrentWorkspaceName()
      this.current = {
        version: params.version,// ????????????
        id: params.id,// ??????id
        name: params.name,// ??????tab??????
        query: params,
        tabId: params.id + params.version,// ???????????????????????????
        isChange: false,
        orchestratorMode: params.orchestratorMode, // ???????????????????????????????????????????????????
        type: DEVPROCESS.DEVELOPMENTCENTER,
      };
      this.tabList.push(this.current);
      this.lastVal = this.currentVal;
      this.currentVal = this.current;
      this.updateProjectCache(params.projectID, true);
      this.tabName = params.id + params.version;
      // ??????????????????textColor????????????tab???????????????
      this.textColor = '';
    },
    onTabRemove(tabId) {
      let index = '';
      const removeData = this.tabList.filter((item, i) => {
        if(item.tabId === tabId) {
          index = i;
          return true
        }
      } )[0];
      // ?????????????????????????????????????????????
      const removeAction = () => {
        // ???????????????????????????????????????
        if (removeData.tabId === this.currentVal.tabId) {
          // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
          if(this.tabList.length > 1 && index < this.tabList.length -1) {
            this.current = this.currentVal = this.lastVal = this.tabList[index + 1];
          } else if(this.tabList.length > 1 && index === this.tabList.length -1) {
            this.current = this.currentVal = this.lastVal = this.tabList[index - 1]
          } else {
            this.current = this.currentVal = this.lastVal = {};
            // ???????????????????????????????????????????????????
            this.textColor = '#2D8CF0';
          }

        }
        this.tabList.splice(index, 1);
        this.tabName = (this.currentVal.id + this.currentVal.version) || '';
        this.updateProjectCache(null, true);
      }
      if (this.tabList[index].isChange) {
        this.$Modal.confirm({
          title: this.$t('message.workflow.process.index.GBTS'),
          content: this.$t('message.workflow.process.index.WBCSFGB'),
          onOk: () => {
            removeAction();
          }
        })
      } else {
        removeAction();
      }
    },
    // ??????????????????
    handleChangeButton(item) {
      // ???????????????value
      this.modeOfKey = item.dicValue;
      // ????????????????????????????????????????????????
      storage.set('currentDssLabels', this.modeOfKey);
      this.tabList = [];
      this.textColor = '#2D8CF0';
      this.lastVal = null;
      this.currentVal = {};
      this.current = null;
      this.tabId = '';
    },
    // ????????????
    selectProject() {
      this.textColor= "#2D8CF0"
    },
    /**
     * ??????tab
     * tabId???tab?????????????????????
     */
    onTabClick(tabId) {
      // ????????????????????????????????????
      const currenTab = this.tabList.filter( item => item.tabId === tabId )[0];
      this.lastVal = this.currentVal;
      this.currentVal = {
        ...currenTab,
        tabId: currenTab.tabId,
      };
      this.current = currenTab;
      this.tabName = currenTab.id + currenTab.version;
      // ??????????????????textColor????????????tab???????????????
      this.textColor = '';
    },
    async getCache() {
      const query = this.$route.query;
      if (query && query.projectID) {
        const cache = await projectDb.getProjectCache({
          projectID: query.projectID
        });
        if (cache && Number(query.projectID) === Number(cache.projectID)) {
          this.lastVal = cache.lastVal;
          if (cache.tabList) {
            this.currentVal = cache.currentVal || {
              name: "index",
              version: "index"
            };
            this.tabList = cache.tabList;
            this.current = cache.tabList.find(item => {
              if (this.currentVal.version && item.version) {
                return (
                  item.name === this.currentVal.name &&
                  item.version == this.currentVal.version
                );
              } else {
                return item.name === this.currentVal.name;
              }
            });
          } else {
            this.current = [];
          }
        }
      }
    },
    updateProjectCache(projectID, wantUpadateList) {
      const value = {
        currentVal: this.currentVal,
        lastVal: this.lastVal
      };
      if (wantUpadateList) {
        value.tabList = this.tabList;
      }
      projectID = projectID || this.$route.query.projectID;
      projectDb.updateProjectCache({
        projectID,
        value
      });
    },
    updateWorkflowList() {
      this.$refs.workflow.fetchFlowData();
    },
    isChange(index, val) {
      if (this.tabList[index]) {
        this.tabList[index].isChange = val[0];
      }
    },
    checkEditable(item, name) {
      // ????????????????????????
      if (item.editUsers && item.editUsers.length > 0) {
        return item.editUsers.some(e => e === name);
      } else {
        return false;
      }
    },
    getUserName() {
      return storage.get("baseInfo", 'local') ? storage.get("baseInfo", 'local').username : null;
    },
    publishSuccess(currentOrchetratorData) {
      this.tabList = this.tabList.filter((i) => i.tabId !== (String(currentOrchetratorData.orchestratorId) + currentOrchetratorData.orchestratorVersionId));
    }
  }
};
</script>
<style lang="scss" scoped>
  @import "@/apps/workflows/assets/styles/workflow.scss";
  .item-header {
    font-size: $font-size-base;
    margin: 10px 25px;
    font-weight: bold;
    padding-left: 5px;
    border-left: 3px solid $primary-color;
  }
  .rightCardContainer {
    will-change: auto;
    padding: 20px;
    .cardItem {
      box-shadow: 0 0 6px $shadow-color;
      position: relative;
      padding: 10px;
      line-height: 1;
      display: flex;
      align-items: center;
      &:not(:last-child) {
        margin-bottom: 50px;
      }
      .ios-arrow-round-down {
        position: absolute;
        bottom: -45px;
        left: 50%;
        color: $primary-color;
        font-size: 40px;
        transform: translateX(-50%)
      }
      .cardItemText {
        margin-left: 10px;
        font-size: $font-size-base;
        font-weight: 700;
      }
    }
  }
</style>



