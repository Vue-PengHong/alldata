package com.platform.mall.dto.admin;

import java.io.Serializable;

/**
 * 查询单个产品进行修改时返回的结果
 * Created by wulinhao on 2020/3/26.
 */
public class PmsProductResult extends PmsProductParam implements Serializable {
    //商品所选分类的父id
    private Long cateParentId;

    public Long getCateParentId() {
        return cateParentId;
    }

    public void setCateParentId(Long cateParentId) {
        this.cateParentId = cateParentId;
    }
}
