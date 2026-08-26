package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductRespVO {
    private Long id;
    private Long merchantId;
    private String merchantName;
    private Long storeId;
    private String storeName;
    private Integer sellerType;
    private Long sellerUserId;
    private Long categoryId;
    private String categoryName;
    private String code;
    private String name;
    private String condition;
    private String subtitle;
    private String mainImageUrl;
    private List<String> imageUrls;
    private String description;
    private Integer auditStatus;
    private Integer saleStatus;
    private Long reviewerUserId;
    private LocalDateTime reviewTime;
    private String rejectReason;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ProductSkuRespVO> skus;
}
