package com.llmobservability.platform.inferencegateway.application.service.context;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ToolNeedRouter {
    public ToolPlan plan(List<StartInferenceCommand.Message> messages) {
        String text = messages.stream()
                .filter(message -> message.role() == MessageRole.USER)
                .map(StartInferenceCommand.Message::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .toLowerCase(Locale.ROOT);

        if (text.isBlank()) {
            return ToolPlan.none();
        }

        List<String> categories = new ArrayList<>();
        List<ToolPlan.ToolStep> steps = new ArrayList<>();
        if (needsMarketData(text)) {
            categories.add("market_data");
            steps.add(new ToolPlan.ToolStep("marketData.lookup", "Fetch current market data and freshness timestamp."));
        }
        if (needsWebSearch(text)) {
            categories.add("web_search");
            steps.add(new ToolPlan.ToolStep("webSearch.search", "Fetch recent source-backed web context."));
        }

        if (steps.isEmpty()) {
            return ToolPlan.none();
        }

        return new ToolPlan(true, "Freshness-sensitive prompt matched deterministic routing policy.", categories, steps);
    }

    private boolean needsMarketData(String text) {
        return containsAny(text, "bitcoin", "btc", "crypto", "cryptocurrency", "stock", "stocks", "equity",
                "share price", "market cap", "all-time high", "all time high", " ath", "price", "exchange rate");
    }

    private boolean needsWebSearch(String text) {
        return containsAny(text, "latest", "current", "today", "yesterday", "recent", "news", "new ",
                "release", "version", "changelog", "cve", "vulnerability", "ceo", "president", "prime minister",
                "law", "regulation", "schedule", "weather", "sports", "available", "availability")
                || needsMarketData(text);
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
