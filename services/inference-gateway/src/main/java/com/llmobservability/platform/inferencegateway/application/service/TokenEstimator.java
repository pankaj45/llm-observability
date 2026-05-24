package com.llmobservability.platform.inferencegateway.application.service;

public final class TokenEstimator {
    private TokenEstimator() {
    }

    public static int estimate(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
