package com.llmobservability.platform.inferencegateway.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class PostgresBindingSupport {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private PostgresBindingSupport() {
    }

    static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        if (value == null) {
            return spec.bindNull(name, type);
        }
        return spec.bind(name, value);
    }

    static String json(ObjectMapper objectMapper, Map<?, ?> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON value", e);
        }
    }

    static Map<String, String> stringMap(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, STRING_MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialize JSON value", e);
        }
    }

    static UUID uuid(Row row, String column) {
        return row.get(column, UUID.class);
    }

    static String string(Row row, String column) {
        return row.get(column, String.class);
    }

    static Integer integer(Row row, String column) {
        return row.get(column, Integer.class);
    }

    static Boolean bool(Row row, String column) {
        return row.get(column, Boolean.class);
    }

    static Instant instant(Row row, String column) {
        return row.get(column, Instant.class);
    }

    static BigDecimal decimal(Row row, String column) {
        return row.get(column, BigDecimal.class);
    }
}
