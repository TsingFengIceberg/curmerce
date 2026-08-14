package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductCategoryTreeRespVO {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String imageUrl;
    private Integer sort;
    private Integer status;
    private List<ProductCategoryTreeRespVO> children = new ArrayList<>();
}
