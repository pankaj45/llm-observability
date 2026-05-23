package com.llmobservability.platform.analyticsquery;

import com.llmobservability.platform.analyticsquery.adapter.out.clickhouse.ClickHouseProperties;
import com.llmobservability.platform.analyticsquery.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ClickHouseProperties.class, SecurityProperties.class})
public class AnalyticsQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsQueryApplication.class, args);
    }
}
