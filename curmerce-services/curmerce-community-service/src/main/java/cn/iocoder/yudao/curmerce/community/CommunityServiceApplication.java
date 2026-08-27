package cn.iocoder.yudao.curmerce.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "cn.iocoder.yudao.curmerce.community",
        "cn.iocoder.yudao.module.community"
})
@ConfigurationPropertiesScan("cn.iocoder.yudao.curmerce.community")
public class CommunityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
