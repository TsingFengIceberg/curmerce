package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "curmerce.auction.datasource")
public record AuctionDatabaseProperties(String url, String username, String password, int maximumPoolSize) {
    public AuctionDatabaseProperties {
        url = url == null || url.isBlank()
                ? "jdbc:mysql://127.0.0.1:13306/curmerce_auction?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                : url;
        username = username == null || username.isBlank() ? "curmerce_auction" : username;
        password = password == null ? "" : password;
        maximumPoolSize = maximumPoolSize < 1 ? 8 : Math.min(maximumPoolSize, 32);
    }
}
