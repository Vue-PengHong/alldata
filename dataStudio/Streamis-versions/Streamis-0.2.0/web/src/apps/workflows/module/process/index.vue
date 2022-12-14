<template>
  <div class="page-process" ref="page_process">
    <Card v-if="!checkEditable(processData) && showTip" shadow class="process-readonly-tip-card">
      <div>
        {{$t("message.workflow.workflowItem.readonlyTip")}}
      </div>
      <Icon type="md-close" class="tipClose" @click.stop="closeTip" />
    </Card>
    <div class="process-tabs">
      <div class="process-tab">
        <div v-for="(item, index) in tabs" :key="index" class="process-tab-item" :class="{active: index===active}"
          @click="choose(index)" @mouseenter.self="item.isHover = true" @mouseleave.self="item.isHover = false">
          <div>
            <img class="tab-icon" :class="nodeImg[item.node.type] ? nodeImg[item.node.type].class : ''"
              :src="nodeImg[item.node.type] ? nodeImg[item.node.type].icon: ''" alt />
            <div :title="item.title" class="process-tab-name">{{ item.title }}</div>
            <SvgIcon v-show="!item.isHover && item.node && item.node.isChange && checkEditable(processData)"
              class="process-tab-unsave-icon" icon-class="fi-radio-on2" />
            <Icon v-if="item.isHover && (item.close || processData.product)" type="md-close" @click.stop="remove(index)" />
          </div>
        </div>
      </div>
      <div class="process-container">
        <template v-for="(item, index) in tabs">
          <Process ref="process" v-if="item.type === 'Process'" v-show="index===active" :key="item.key"
            :import-replace="false" :flow-id="item.data.appId" :version="item.data.version" :product="processData.product"
            :readonly="!checkEditable(processData)" :isLatest="processData.isLatest === 'true'" :tabs="tabs" :open-files="openFiles"
            :orchestratorId="item.data.id" :orchestratorVersionId="item.data.orchestratorVersionId"
            @changeMap="changeTitle" @node-dblclick="dblclickNode(index, arguments)"
            @isChange="isChange(index, arguments)" @save-node="saveNode" @check-opened="checkOpened"
            @deleteNode="deleteNode" @saveBaseInfo="saveBaseInfo" @updateWorkflowList="$emit('updateWorkflowList')">
          </Process>
          <Ide v-if="item.type === 'IDE'" v-show="index===active" :key="item.title" :parameters="item.data"
            :node="item.node" :in-flows-index="index" :readonly="!checkEditable(processData)"
            @save="saveIDE(index, arguments)"></Ide>
          <commonIframe v-if="item.type === 'Iframe'" v-show="index===active" :key="item.title" :parametes="item.data"
            :node="item.node" @save="saveNode"></commonIframe>
          <Datasource v-if="item.type === 'Datasource'" v-show="index===active" :key="item.title" :node="item.node"
            :data="item.data" @save="saveNode" style="width:100%; height:100%"></Datasource>
        </template>
      </div>
    </div>
  </div>
</template>
<script>
import {
  isEmpty,
  isArguments
} from "lodash";
import api from "@/common/service/api";
import util from "@/common/util";
import Process from "./module.vue";
import qs from 'qs';
import Ide from "@/apps/workflows/module/ide";
import Datasource from "@/apps/streamis/view/dataSource/index";
import commonModule from "@/apps/workflows/module/common";
import {
  NODETYPE,
  NODEICON
} from "@/apps/workflows/service/nodeType";
export default {
  components: {
    Process,
    Ide: Ide.component,
    Datasource,
    commonIframe: commonModule.component.iframe
  },
  props: {
    query: {
      type: Object,
      default: () => {}
    }
  },
  data() {
    let processData = this.query || qs.parse(location.search)
    processData.priv = processData.priv - 0
    return {
      processData,
      tabs: [{
        title: this.$t("message.workflow.process.index.BJMS"),
        type: "Process",
        close: false,
        data: processData,
        node: {
          isChange: false,
          type: "workflow.subflow"
        },
        key: "?????????",
        isHover: false
      }],
      active: 0,
      setIntervalID: "",
      setTime: 40,
      showTip: true,
      openFiles: {},
      nodeImg: NODEICON
    };
  },
  mounted() {
    this.getCache().then(tabs => {
      if (tabs) {
        this.tabs = tabs;
      }
    });
    this.updateProjectCacheByActive();
    this.changeTitle(false);
  },
  methods: {
    // ??????????????????????????????
    // ??????????????????????????????????????????
    checkEditable(item) {
      // ????????????????????????priv???????????????1-????????? 2-????????? 3-??????
      if ([2, 3].includes(item.priv) && this.processData.readonly !== 'true') {
        return true
      } else {
        return false
      }
    },
    gotoAction(back = -1) {
      if (back) {
        this.$router.go(back);
      }
    },
    // ??????????????????
    closeTip() {
      this.showTip = false;
    },
    choose(index) {
      this.active = index;
      this.updateProjectCacheByActive();
    },
    remove(index) {
      // ???????????????????????????tab?????????????????????
      if (index === 0) {
        this.$router.go(-1);
      }
      // ???????????????????????????????????????????????????
      const currentTab = this.tabs[index];
      // ????????????????????????????????????
      const subArray = this.openFiles[currentTab.key] || [];
      const changeList = this.tabs.filter(item => {
        return subArray.includes(item.key) && item.node.isChange;
      });
      // ?????????????????????????????????????????????????????????????????????????????????
      if (changeList.length > 0 && currentTab.node.type === NODETYPE.FLOW) {
        let text = `<p>${this.$t("message.workflow.process.index.WBCSFGB")}</p>`;
        if (currentTab.node.isChange) {
          text = `<p>${this.$t("message.workflow.process.index.GGZLWBC")}</p>`;
        }
        this.$Modal.confirm({
          title: this.$t("message.workflow.process.index.GBTS"),
          content: text,
          okText: this.$t("message.workflow.process.index.QRGB"),
          cancelText: this.$t("message.workflow.process.index.QX"),
          onOk: () => {
            // ?????????????????????????????????????????????????????????tab??????????????????????????????tab???????????????????????????????????????tab
            if (this.active === index) {
              // ??????????????????????????????
              this.tabs.splice(index, 1);
              this.choose(this.tabs.length - 1);
            } else {
              this.tabs.splice(index, 1);
              // this.choose(this.tabs.length - 1);
            }
            this.updateProjectCacheByTab();
          },
          onCancel: () => {}
        });
      } else {
        // ?????????????????????????????????????????????????????????tab??????????????????????????????tab???????????????????????????????????????tab
        if (this.active === index) {
          // ??????????????????????????????
          this.tabs.splice(index, 1);
          this.choose(this.tabs.length - 1);
        } else {
          this.tabs.splice(index, 1);
          // this.choose(this.tabs.length - 1);
        }
        this.updateProjectCacheByTab();
      }
    },
    check(node) {
      if (node) {
        let boolean = true;
        this.tabs.map(item => {
          if (node.key === item.key) {
            boolean = true;
          } else {
            if (this.tabs.length > 10) {
              boolean = false;
              return;
            }
            boolean = true;
          }
        });
        if (!boolean) {
          this.$Message.warning(this.$t("message.workflow.process.index.CCXE"));
        }
        return boolean;
      } else {
        if (this.tabs.length > 10) {
          this.$Message.warning(this.$t("message.workflow.process.index.CCXE"));
          return false;
        }
        return true;
      }
    },
    dblclickNode(index, args) {
      if (!this.check(args[0][0])) {
        return;
      }
      const node = args[0][0];
      // ?????????????????????????????????
      for (let i = 0; i < this.tabs.length; i++) {
        if (this.tabs[i].key === node.key) return this.choose(i);
      }
      // ????????????????????????supportJump???true???????????????url,????????????????????????
      if (node.supportJump && !node.shouldCreationBeforeNode && !node.jumpUrl) {
        const len = node.resources ? node.resources.length : 0;
        if (len && node.jobContent && node.jobContent.script) {
          // ??????????????????????????????
          const resourceId = node.resources[0].resourceId;
          const fileName = node.resources[0].fileName;
          const version = node.resources[0].version;
          let config = {
            method: "get"
          };
          if (this.processData.product) {
            config.headers = {
              "Token-User": this.getUserName()
            };
          }
          api.fetch(this.processData.product ? "/filesystem/product/openScriptFromBML" : "/filesystem/openScriptFromBML", {
            fileName,
            resourceId,
            version,
            creator: node.creator || "",
            projectName: this.processData.projectName || ""
          }, config).then(res => {
            let content = res.scriptContent;
            let params = {};
            params.variable = this.convertSettingParamsVariable(res.metadata);
            params.configuration = !node.params || isEmpty(node.params.configuration) ? {
              special: {},
              runtime: {},
              startup: {}
            } : {
              special: node.params.configuration.special || {},
              runtime: node.params.configuration.runtime || {},
              startup: node.params.configuration.startup || {}
            };
            params.configuration.runtime.contextID = node.contextID;
            params.configuration.runtime.nodeName = node.title;
            this.getTabsAndChoose({
              type: "IDE",
              node,
              data: {
                content,
                params
              }
            });
          });
        } else {
          // ?????????????????????????????????????????????????????????
          let content = node.jobContent && node.jobContent.code ? node.jobContent.code : "";
          let params = {};
          params.variable = this.convertSettingParamsVariable({});
          params.configuration = !node.params || isEmpty(node.params.configuration) ? {
            special: {},
            runtime: {},
            startup: {}
          } : {
            special: node.params.configuration.special || {},
            runtime: node.params.configuration.runtime || {},
            startup: node.params.configuration.startup || {}
          };
          params.configuration.runtime.contextID = node.contextID;
          params.configuration.runtime.nodeName = node.title;
          this.getTabsAndChoose({
            type: "IDE",
            node,
            data: {
              content,
              params
            }
          });
        }
        return;
      }
      if (node.type == NODETYPE.FLOW) {
        // ????????????????????????, ??????????????????
        let flowId = node.jobContent ? node.jobContent.embeddedFlowId : "";
        let {
          orchestratorVersionId,
          id
        } = {
          ...this.processData
        }
        this.getTabsAndChoose({
          type: "Process",
          node,
          data: {
            appId: flowId,
            orchestratorVersionId,
            id
          }
        });
        return;
      }
      // node.jumpUrl ???????????????http?????????iframe??????????????????????????????????????????
      if (node.supportJump && node.jumpUrl) {
        if (node.jumpUrl.startsWith('http')) {
          let id = node.jobContent ? node.jobContent.id : "";
          this.getTabsAndChoose({
            type: "Iframe",
            node,
            data: {
              id
            }
          });
        } else {
          this.getTabsAndChoose({
            type: node.jumpUrl,
            node,
            data: {
              processData: this.processData
            }
          });
        }
      }
    },
    getTabsAndChoose({
      type,
      node,
      data
    }) {
      this.$set(node, "isChange", false);
      this.tabs.push({
        type,
        key: node.key,
        title: node.title,
        close: true,
        // ??????????????????????????????
        node,
        data,
        isHover: false
      });
      // ???????????????tab???????????????
      this.openFileAction(node);
      this.choose(this.tabs.length - 1);
      this.updateProjectCacheByTab();
    },
    openFileAction(node) {
      // ???????????????????????????????????????????????????????????????????????????s
      const currnentTab = this.tabs[this.active];
      if (Object.keys(this.openFiles).includes(currnentTab.key)) {
        Object.keys(this.openFiles).map(key => {
          // ???????????????????????????????????????????????????
          if (key == currnentTab.key) {
            if (!this.openFiles[key].includes(node.key)) {
              this.openFiles[key].push(node.key);
            }
          }
        });
      } else {
        this.openFiles[currnentTab.key] = [node.key];
      }
    },
    saveIDE(index, args) {
      if (!this.check()) {
        return;
      }
      if (args[0].data) {
        this.tabs[index].data = args[0].data;
      }
      // ????????????
      let node = this.tabs[index].node;
      this.saveNode(args, node, true);
    },
    saveNode(args, node, scriptisSave = true) {
      // scriptisSave?????????????????????????????????????????????????????????
      // ???????????????????????????????????????scriptis???????????????qualitis???????????????????????????????????????????????????args????????????scriptis?????????arguments, qualitis????????????????????????????????????????????????
      let resource = args;
      let currentNode = node;
      if (isArguments(args)) {
        resource = args[0].resource;
        currentNode = args[0].node;
      }
      if (!node.jobContent) {
        node.jobContent = {};
      }
      if (!currentNode || node.key !== currentNode.key) return;
      node.jobContent.script = resource.fileName;
      delete node.jobContent.code; // code????????????script????????????code
      if (!node.resources) {
        node.resources = [];
      }
      // qualitis ?????????????????????, ??????????????????????????????
      if (Object.keys(resource).length > 0) {
        if (
          node.resources.length > 0 &&
          node.resources[0].resourceId === resource.resourceId
        ) {
          // ?????????????????????????????????????????????????????????
          node.resources[0] = resource;
        } else {
          node.resources.unshift(resource);
        }
      }
      this.$refs.process.forEach(item => {
        item.json.nodes.forEach(subitem => {
          if (subitem.key === currentNode.key) {
            // ??????????????????originalData???????????????????????????????????????????????????????????????
            item.updateOriginData(node, scriptisSave);
          }
        });
      });
      // ???????????????????????????????????????????????????tabs
      this.updateProjectCacheByTab();
    },
    convertSettingParamsVariable(params = {}) {
      const variable = isEmpty(params.variable) ? [] : util.convertObjectToArray(params.variable);
      return variable;
    },
    saveTip(cb, cancel) {
      this.$Modal.confirm({
        title: this.$t("message.workflow.process.index.FHTX"),
        content: `<p>${this.$t("message.workflow.process.index.WBCSFBC")}</p>`,
        okText: this.$t("message.workflow.process.index.BC"),
        cancelText: this.$t("message.workflow.process.index.QX"),
        onOk: cb,
        onCancel: cancel
      });
    },
    isChange(index, val) {
      if (this.tabs[index].node) {
        this.tabs[index].node.isChange = val[0];
        this.$emit("isChange", val[0]);
      }
    },
    beforeLeaveHook() {},
    checkOpened(node, cb) {
      const isOpened = this.tabs.find(item => item.key === node.key);
      cb(!!isOpened);
    },
    deleteNode(node) {
      let index = null;
      for (let i = 0; i < this.tabs.length; i++) {
        if (this.tabs[i].key === node.key) {
          index = i;
        }
      }
      if (index) {
        this.remove(index);
      }
    },
    saveBaseInfo(node) {
      this.tabs = this.tabs.map(item => {
        if (item.key === node.key) {
          item.title = node.title;
        }
        return item;
      });
    },
    updateProjectCacheByTab() {
      this.dispatch("workflowIndexedDB:updateProjectCache", {
        projectID: this.processData.projectID,
        key: "tabList",
        value: {
          tab: this.tabs,
          token: "flowId",
          sKey: "tab",
          sValue: this.processData.flowId
        },
        isDeep: true
      });
    },
    updateProjectCacheByActive() {
      this.dispatch("workflowIndexedDB:updateProjectCache", {
        projectID: this.processData.projectID,
        key: "tabList",
        value: {
          active: this.active,
          token: "flowId",
          sKey: "active",
          sValue: this.processData.flowId
        },
        isDeep: true
      });
    },
    getCache() {
      return new Promise(resolve => {
        this.dispatch("workflowIndexedDB:getProjectCache", {
          projectID: this.processData.projectID,
          cb: cache => {
            const list = (cache && cache.tabList) || [];
            let tabs = null;
            list.forEach(item => {
              if (+item.flowId === +this.processData.flowId) {
                tabs = item.tab;
                this.active = item.active || 0;
              }
            });
            resolve(tabs);
          }
        });
      });
    },
    changeTitle(val) {
      // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
      if (val) {
        this.tabs[0].title = this.$t("message.workflow.process.index.DTMS");
      } else {
        if (this.processData.readonly === "true") {
          this.tabs[0].title = this.$t("message.workflow.process.index.ZDMS");
        } else {
          this.tabs[0].title = this.$t("message.workflow.process.index.BJMS");
        }
      }
    }
  },
};
</script>
<style lang="scss" src="@/apps/workflows/assets/styles/process.scss"></style>
