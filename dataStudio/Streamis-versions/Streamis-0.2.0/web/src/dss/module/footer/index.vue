<template>
  <div
    ref="footerChannel"
    class="layout-footer"
    @mousedown.prevent.stop="onMouseDown"
    @mouseup.prevent.stop="oMouseUp"
    @click.prevent.stop="toast">
    <resource-simple
      ref="resourceSimple"
      @update-job="updateJob">
    </resource-simple>
    <div
      :title="msg"
      class="footer-channel">
      <Icon
        class="footer-channel-job"
        type="ios-swap"/>
      <span class="footer-channel-job-num">{{ num }}</span>
    </div>
  </div>
</template>
<script>
import resourceSimpleModule from '@/dss/module/resourceSimple';
import api from '@/common/service/api';
export default {
  components: {
    resourceSimple: resourceSimpleModule.component,
  },
  data() {
    return {
      num: 0,
      msg: '',
      moveX: null,
      moveY: null,
      isMouseDrop: false,
      isMouseMove: false,
    };
  },
  created() {
    // 让其它接口请求保持在getBasicInfo接口后面请求
    setTimeout(() => {
      this.getRunningJob();
    }, 500);
  },
  watch: {
    '$route'() {
      this.resetChannelPosition();
    }
  },
  methods: {
    getRunningJob() {
      api.fetch('/jobhistory/list', {
        pageSize: 100,
        status: 'Running,Inited,Scheduled',
      }, 'get').then((rst) => {
        // 剔除requestApplicationName为 "nodeexecution" 的task
        let tasks = rst.tasks.filter(item => item.requestApplicationName !== "nodeexecution")
        this.num = tasks.length;
      });
    },
    'Footer:updateRunningJob'(num) {
      this.num = num;
    },
    'Footer:getRunningJob'(cb) {
      cb(this.num);
    },
    updateJob(num) {
      const method = 'Footer:updateRunningJob';
      this[method](num);
    },
    toast() {
      // 取消拖动后自动点击事件
      if (this.isMouseMove) {
        return;
      }
      this.$refs.resourceSimple.open();
    },
    onMouseDown(e) {
      e = e || window.event;
      const footerChannel = this.$refs.footerChannel;
      this.moveX = e.clientX - footerChannel.offsetLeft;
      this.moveY = e.clientY - footerChannel.offsetTop;
      this.isMouseDrop = true;
      this.isMouseMove = false;
      // 阻止拖拽过程中选中文本
      document.onselectstart = () => {
        return false;
      }
      // 这里无法在元素上使用@mousemove，否则拖动会有卡顿
      // 使用setTimeout是防止点击的同时会触发move事件，这时正常的点击事件是不会触发的。
      setTimeout(() => {
        document.onmousemove = (e) => {

          if (this.isMouseDrop) {
            this.isMouseMove = true;
            const footerChannel = this.$refs.footerChannel;
            let x = e.clientX - this.moveX;
            let y = e.clientY - this.moveY;
            // 限制拖动范围
            let maxX = document.documentElement.clientWidth - 120;
            let maxY = document.documentElement.clientHeight - 60;
            if (this.moveX <= 0) {
              maxX = document.documentElement.scrollWidth - 120;
            }
            if (this.moveY <= 0) {
              maxY = document.documentElement.scrollHeight - 60;
            }
            x = Math.min(maxX, Math.max(0, x));
            y = Math.min(maxY, Math.max(0, y));
            if(e.clientX > maxX+120 || e.clientY > maxY+60 || e.clientX < 0 || e.clientY < 0){
              this.oMouseUp()
            }
            footerChannel.style.left = x + 'px';
            footerChannel.style.top = y + 'px';
          }
        }
      }, 0)
    },
    oMouseUp() {
      // 清空onmousemove方法
      document.onmousemove = null;
      this.isMouseDrop = false;
      setTimeout(() => {
        this.isMouseMove = false;
      }, 200)
      // 恢复document的文本选中功能
      document.onselectstart = () => {
        return true;
      }
    },
    resetChannelPosition() {
      const footerChannel = this.$refs.footerChannel;
      footerChannel.style.left = document.documentElement.clientWidth - 120 + 'px';
      footerChannel.style.top = document.documentElement.clientHeight - 60 + 'px';
    }
  },
};
</script>
<style src="./index.scss" lang="scss"></style>
