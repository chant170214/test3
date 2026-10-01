package com.pulsemonitor.app;

public final class MetricSnapshot {
    public final long timestampMs;
    public final double ramPercent;
    public final long ramUsedBytes;
    public final long ramTotalBytes;
    public final double appCpuPercent;
    public final double systemCpuPercent;
    public final long appPssBytes;
    public final double storagePercent;
    public final long storageUsedBytes;
    public final long storageTotalBytes;
    public final int batteryPercent;
    public final double batteryTempC;
    public final int thermalStatus;
    public final long uptimeMs;
    public final int cpuCores;

    public MetricSnapshot(
            long timestampMs,
            double ramPercent,
            long ramUsedBytes,
            long ramTotalBytes,
            double appCpuPercent,
            double systemCpuPercent,
            long appPssBytes,
            double storagePercent,
            long storageUsedBytes,
            long storageTotalBytes,
            int batteryPercent,
            double batteryTempC,
            int thermalStatus,
            long uptimeMs,
            int cpuCores) {
        this.timestampMs = timestampMs;
        this.ramPercent = ramPercent;
        this.ramUsedBytes = ramUsedBytes;
        this.ramTotalBytes = ramTotalBytes;
        this.appCpuPercent = appCpuPercent;
        this.systemCpuPercent = systemCpuPercent;
        this.appPssBytes = appPssBytes;
        this.storagePercent = storagePercent;
        this.storageUsedBytes = storageUsedBytes;
        this.storageTotalBytes = storageTotalBytes;
        this.batteryPercent = batteryPercent;
        this.batteryTempC = batteryTempC;
        this.thermalStatus = thermalStatus;
        this.uptimeMs = uptimeMs;
        this.cpuCores = cpuCores;
    }

    public boolean hasSystemCpu() {
        return systemCpuPercent >= 0.0;
    }
}
