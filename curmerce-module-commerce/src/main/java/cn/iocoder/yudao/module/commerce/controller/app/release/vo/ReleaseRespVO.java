package cn.iocoder.yudao.module.commerce.controller.app.release.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReleaseRespVO {
    private Long id;
    private String name;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer perUserLimit;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Long productId;
        private Long skuId;
        private Long campaignPrice;
        private Integer stock;
        private Integer soldCount;
    }
}
