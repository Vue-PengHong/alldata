<template>
  <div class="layout-body">
    <layout-header
      @clear-session="clearSession"/>
    <router-view/>
    <layout-footer/>
  </div>
</template>
<script>
import headerModule from '../module/header';
import footerModule from '@/dss/module/footer';
import layoutMixin from '@/common/service/layoutMixin.js';
export default {
  components: {
    layoutFooter: footerModule.component,
    layoutHeader: headerModule.component,
  },
  computed: {
  },
  mixins: [layoutMixin]
};
</script>
<style lang="scss" scoed>
.hide-header {
  padding: 0;
}
</style>