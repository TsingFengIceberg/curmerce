package cn.iocoder.yudao.curmerce.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GatewayInfoController {
    @GetMapping("/")
    public Map<String, Object> info() {
        return Map.of("name", "Curmerce Gateway", "status", "UP", "version", "0.2.0-SNAPSHOT");
    }
}
