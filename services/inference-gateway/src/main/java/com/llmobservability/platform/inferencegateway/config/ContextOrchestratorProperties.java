package com.llmobservability.platform.inferencegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm-observability.context")
public class ContextOrchestratorProperties {
    private boolean enabled = true;
    private String timezone = "Asia/Kolkata";
    private Duration toolTimeout = Duration.ofSeconds(5);
    private Duration marketDataCacheTtl = Duration.ofSeconds(60);
    private Duration webSearchCacheTtl = Duration.ofMinutes(10);
    private int webSearchMaxResults = 5;
    private final Tavily tavily = new Tavily();
    private final CoinGecko coinGecko = new CoinGecko();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Duration getToolTimeout() {
        return toolTimeout;
    }

    public void setToolTimeout(Duration toolTimeout) {
        this.toolTimeout = toolTimeout;
    }

    public Duration getMarketDataCacheTtl() {
        return marketDataCacheTtl;
    }

    public void setMarketDataCacheTtl(Duration marketDataCacheTtl) {
        this.marketDataCacheTtl = marketDataCacheTtl;
    }

    public Duration getWebSearchCacheTtl() {
        return webSearchCacheTtl;
    }

    public void setWebSearchCacheTtl(Duration webSearchCacheTtl) {
        this.webSearchCacheTtl = webSearchCacheTtl;
    }

    public int getWebSearchMaxResults() {
        return webSearchMaxResults;
    }

    public void setWebSearchMaxResults(int webSearchMaxResults) {
        this.webSearchMaxResults = webSearchMaxResults;
    }

    public Tavily getTavily() {
        return tavily;
    }

    public CoinGecko getCoinGecko() {
        return coinGecko;
    }

    public static class Tavily {
        private boolean enabled = true;
        private String apiKey = "";
        private String baseUrl = "https://api.tavily.com";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class CoinGecko {
        private boolean enabled = true;
        private String baseUrl = "https://api.coingecko.com";

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
    }
}
