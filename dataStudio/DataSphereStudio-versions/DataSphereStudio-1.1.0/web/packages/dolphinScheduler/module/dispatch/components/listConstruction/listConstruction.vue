/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<template>
  <div class="home-main list-construction-model">
    <div class="content-title">
      <span>{{title}}</span>
      <slot name="operation"></slot>
    </div>
    <div class="conditions-box">
      <slot name="conditions">
      </slot>
    </div>
    <div class="list-box">
      <slot name="content">
      </slot>
    </div>
  </div>
</template>
<script>
export default {
  name: 'list-construction',
  data () {
    return {}
  },
  props: {
    title: String
  }
}
</script>

<style lang="scss" rel="stylesheet/scss" scoped>
@import '@dataspherestudio/shared/common/style/variables.scss';
  .content-title {
    padding-bottom: 17px;
    font-size: 16px;
    @include font-color($workspace-title-color, $dark-workspace-title-color);
    line-height: 22px;
    font-weight: bolder;
  }
</style>
