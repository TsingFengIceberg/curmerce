package cn.iocoder.yudao.curmerce.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentAssistReqDTO {
    @NotBlank
    @Size(min = 2, max = 100)
    private String query;
}
