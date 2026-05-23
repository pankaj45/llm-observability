package com.llmobservability.platform.analyticsquery.application.port.in;

import java.util.List;

public record PagedResult<T>(List<T> items, String nextCursor) {
}
