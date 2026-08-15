package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PublicCategoryNodeRespVO {
    private Long id;
    private Long parentId;
    private String name;
    private String imageUrl;
    private List<PublicCategoryNodeRespVO> children = new ArrayList<>();
}
