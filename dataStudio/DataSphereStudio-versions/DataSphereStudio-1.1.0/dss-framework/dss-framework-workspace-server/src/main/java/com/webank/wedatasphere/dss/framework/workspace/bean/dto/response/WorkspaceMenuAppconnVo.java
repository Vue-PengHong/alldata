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
package com.webank.wedatasphere.dss.framework.workspace.bean.dto.response;

import com.webank.wedatasphere.dss.framework.workspace.bean.DSSApplicationBean;

import java.util.List;


public class WorkspaceMenuAppconnVo {
    private Long id;
    private String title;
    private String description;
    private String labels;
    //    private String accessButton;
//    private String accessButtonUrl;
//    private String manualButton;
//    private String manualButtonUrl;
//    private String projectUrl;
    private String name;
    private Boolean isActive;
    private Boolean accessable;
    private String icon;
    private Integer order;
    //    private Map<String, String> nameAndUrls;
    //图片
    private String image;

    private Boolean ifIframe;
    private Boolean isExternal;

    private List<DSSApplicationBean> appInstances;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }
//
//    public String getAccessButton() {
//        return accessButton;
//    }
//
//    public void setAccessButton(String accessButton) {
//        this.accessButton = accessButton;
//    }
//
//    public String getAccessButtonUrl() {
//        return accessButtonUrl;
//    }
//
//    public void setAccessButtonUrl(String accessButtonUrl) {
//        this.accessButtonUrl = accessButtonUrl;
//    }
//
//    public String getManualButton() {
//        return manualButton;
//    }
//
//    public void setManualButton(String manualButton) {
//        this.manualButton = manualButton;
//    }
//
//    public String getManualButtonUrl() {
//        return manualButtonUrl;
//    }
//
//    public void setManualButtonUrl(String manualButtonUrl) {
//        this.manualButtonUrl = manualButtonUrl;
//    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
//
//    public String getProjectUrl() {
//        return projectUrl;
//    }
//
//    public void setProjectUrl(String projectUrl) {
//        this.projectUrl = projectUrl;
//    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

//    public Map<String, String> getNameAndUrls() {
//        return nameAndUrls;
//    }
//
//    public void setNameAndUrls(Map<String, String> nameAndUrls) {
//        this.nameAndUrls = nameAndUrls;
//    }

    public Boolean getAccessable() {
        return accessable;
    }

    public void setAccessable(Boolean accessable) {
        this.accessable = accessable;
    }

    public List<DSSApplicationBean> getAppInstances() {
        return appInstances;
    }

    public void setAppInstances(List<DSSApplicationBean> appInstances) {
        this.appInstances = appInstances;
    }

    public void addAppInstances(DSSApplicationBean dssApplicationBean) {
        this.appInstances.add(dssApplicationBean);
    }

    public Boolean getIfIframe() {
        return ifIframe;
    }

    public void setIfIframe(Boolean ifIframe) {
        this.ifIframe = ifIframe;
    }

    public Boolean getExternal() {
        return isExternal;
    }

    public void setExternal(Boolean external) {
        isExternal = external;
    }
}
