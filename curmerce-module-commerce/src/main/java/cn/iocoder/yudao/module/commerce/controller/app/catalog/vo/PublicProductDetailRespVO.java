package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublicProductDetailRespVO extends PublicProductSummaryRespVO {
    private List<String> imageUrls;
    private String description;
    private List<PublicProductSkuRespVO> skus;
}
