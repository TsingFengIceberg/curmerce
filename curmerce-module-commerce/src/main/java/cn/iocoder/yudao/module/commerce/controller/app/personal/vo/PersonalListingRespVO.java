package cn.iocoder.yudao.module.commerce.controller.app.personal.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PersonalListingRespVO {
    private Long id;
    private Long categoryId;
    private String name;
    private String condition;
    private String mainImageUrl;
    private List<String> imageUrls;
    private String description;
    private Long price;
    private Integer auditStatus;
    private Integer saleStatus;
    private Integer stock;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
