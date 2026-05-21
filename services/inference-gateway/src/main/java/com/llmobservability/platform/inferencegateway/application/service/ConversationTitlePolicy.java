package com.llmobservability.platform.inferencegateway.application.service;

import com.llmobservability.platform.inferencegateway.application.port.in.StartInferenceCommand;
import com.llmobservability.platform.inferencegateway.domain.model.MessageRole;
import com.llmobservability.platform.inferencegateway.domain.model.TitleSource;

public class ConversationTitlePolicy {
    private static final int MAX_TITLE_CHARS = 72;

    public Title title(StartInferenceCommand command) {
        return command.messages().stream()
                .filter(message -> message.role() == MessageRole.USER)
                .map(StartInferenceCommand.Message::content)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .findFirst()
                .map(value -> new Title(truncate(value), TitleSource.FIRST_USER_MESSAGE))
                .orElseGet(() -> new Title("New conversation", TitleSource.FALLBACK));
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value) {
        if (value.length() <= MAX_TITLE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_TITLE_CHARS - 1).trim();
    }

    public record Title(String value, String source) {
    }
}

