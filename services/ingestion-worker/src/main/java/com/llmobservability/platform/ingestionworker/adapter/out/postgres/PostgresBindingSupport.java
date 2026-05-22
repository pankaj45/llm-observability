package com.llmobservability.platform.ingestionworker.adapter.out.postgres;

import org.springframework.r2dbc.core.DatabaseClient;

final class PostgresBindingSupport {
    private PostgresBindingSupport() {
    }

    static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        if (value == null) {
            return spec.bindNull(name, type);
        }
        return spec.bind(name, value);
    }
}
