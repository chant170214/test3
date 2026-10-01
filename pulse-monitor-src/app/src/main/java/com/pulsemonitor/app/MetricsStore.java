package com.pulsemonitor.app;

public final class MetricsStore {
    private static volatile MetricSnapshot latest;

    private MetricsStore() {}

    public static void setLatest(MetricSnapshot snapshot) {
        latest = snapshot;
    }

    public static MetricSnapshot getLatest() {
        return latest;
    }
}
