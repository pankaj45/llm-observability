package com.llmobservability.platform.analyticsquery.domain.model;

import java.util.List;

public record InferenceSummary(
        String tenantId,
        String projectId,
        AnalyticsWindow window,
        InferenceTotals totals,
        List<InferenceTimeSeriesPoint> timeSeries,
        List<DimensionBreakdown> providers,
        List<DimensionBreakdown> models,
        List<DimensionBreakdown> statuses,
        List<ErrorBreakdown> topErrors,
        boolean degraded
) {
    public static InferenceSummary empty(AnalyticsFilter filter, boolean degraded) {
        return new InferenceSummary(
                filter.tenantId(),
                filter.projectId(),
                new AnalyticsWindow(filter.from(), filter.to()),
                InferenceTotals.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                degraded);
    }
}
