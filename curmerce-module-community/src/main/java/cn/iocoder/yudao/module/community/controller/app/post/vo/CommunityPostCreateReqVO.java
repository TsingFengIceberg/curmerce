package cn.iocoder.yudao.module.community.controller.app.post.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommunityPostCreateReqVO {
    @NotBlank @Size(min = 2, max = 120) private String title;
    @NotBlank @Size(min = 2, max = 100000) private String content;
    @Size(max = 20) private List<@Size(max = 1024) String> mediaUrls;
    @Size(max = 10) private List<@Size(max = 120) String> topics;
    @Size(max = 10) private List<Long> productIds;
}
