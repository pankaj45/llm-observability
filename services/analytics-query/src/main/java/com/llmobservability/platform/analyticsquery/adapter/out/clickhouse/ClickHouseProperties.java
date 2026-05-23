package com.llmobservability.platform.analyticsquery.adapter.out.clickhouse;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm-observability.analytics.clickhouse")
public class ClickHouseProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8123";
    private String username = "default";
    private String password = "";
    private String database = "llm_observability";
    private String lifecycleTable = "inference_lifecycle_fact";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getLifecycleTable() {
        return lifecycleTable;
    }

    public void setLifecycleTable(String lifecycleTable) {
        this.lifecycleTable = lifecycleTable;
    }
}
