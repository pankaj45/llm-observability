package com.llmobservability.platform.inferencegateway.application.service.context;

import java.util.List;

public record ToolPlan(
        boolean requiresTools,
        String reason,
        List<String> categories,
        List<ToolStep> steps
) {
    public static ToolPlan none() {
        return new ToolPlan(false, "No freshness-sensitive tool need detected.", List.of(), List.of());
    }

    public record ToolStep(String toolName, String purpose) {
    }
}
