package cn.iocoder.yudao.module.commerce.controller.admin.refund.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundAuditReqVO {
    @NotNull
    private Long id;
    @Size(max = 255, message = "审核备注不能超过 255 个字符")
    private String remark;
}
