package com.llmobservability.platform.analyticsquery.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class CursorCodec {
    private CursorCodec() {
    }

    static String encode(String namespace, String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((namespace + ":" + payload).getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String namespace, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String prefix = namespace + ":";
        if (!decoded.startsWith(prefix)) {
            throw new ApplicationException("VALIDATION_INVALID_REQUEST", org.springframework.http.HttpStatus.BAD_REQUEST, "cursor is invalid");
        }
        return decoded.substring(prefix.length());
    }
}
