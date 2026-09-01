package cn.iocoder.yudao.curmerce.auction;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** Creates the Auction-owned connection only when the independent store is enabled. */
@Configuration
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
@EnableConfigurationProperties(AuctionDatabaseProperties.class)
public class AuctionDataSourceConfiguration {
    @Bean(destroyMethod = "close")
    DataSource auctionDataSource(AuctionDatabaseProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setMaximumPoolSize(properties.maximumPoolSize());
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("curmerce-auction");
        return dataSource;
    }

    @Bean
    JdbcTemplate auctionJdbcTemplate(DataSource auctionDataSource) {
        return new JdbcTemplate(auctionDataSource);
    }

    @Bean
    PlatformTransactionManager auctionTransactionManager(DataSource auctionDataSource) {
        return new DataSourceTransactionManager(auctionDataSource);
    }
}
