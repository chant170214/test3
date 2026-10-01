package com.pulsemonitor.app;

public final class MetricSnapshot {
    public final long timestampMs;

    public final double ramPercent;
    public final long ramUsedBytes;
    public final long ramAvailableBytes;
    public final long ramTotalBytes;
    public final long cachedBytes;
    public final long swapUsedBytes;
    public final long swapTotalBytes;

    public final double appCpuPercent;
    public final double systemCpuPercent;
    public final int cpuCores;
    public final double cpuAvgFreqMhz;
    public final double cpuMaxFreqMhz;
    public final long appPssBytes;

    public final double storagePercent;
    public final long storageUsedBytes;
    public final long storageTotalBytes;

    public final int batteryPercent;
    public final double batteryTempC;
    public final int batteryVoltageMv;
    public final int batteryCurrentNowUa;
    public final int batteryCurrentAvgUa;
    public final int batteryChargeCounterUah;
    public final int batteryStatus;
    public final int batteryHealth;
    public final int batteryPlugged;

    public final int thermalStatus;
    public final long uptimeMs;

    public final long networkRxBytes;
    public final long networkTxBytes;
    public final double networkRxBytesPerSec;
    public final double networkTxBytesPerSec;

    public MetricSnapshot(
            long timestampMs,
            double ramPercent,
            long ramUsedBytes,
            long ramAvailableBytes,
            long ramTotalBytes,
            long cachedBytes,
            long swapUsedBytes,
            long swapTotalBytes,
            double appCpuPercent,
            double systemCpuPercent,
            int cpuCores,
            double cpuAvgFreqMhz,
            double cpuMaxFreqMhz,
            long appPssBytes,
            double storagePercent,
            long storageUsedBytes,
            long storageTotalBytes,
            int batteryPercent,
            double batteryTempC,
            int batteryVoltageMv,
            int batteryCurrentNowUa,
            int batteryCurrentAvgUa,
            int batteryChargeCounterUah,
            int batteryStatus,
            int batteryHealth,
            int batteryPlugged,
            int thermalStatus,
            long uptimeMs,
            long networkRxBytes,
            long networkTxBytes,
            double networkRxBytesPerSec,
            double networkTxBytesPerSec) {
        this.timestampMs = timestampMs;
        this.ramPercent = ramPercent;
        this.ramUsedBytes = ramUsedBytes;
        this.ramAvailableBytes = ramAvailableBytes;
        this.ramTotalBytes = ramTotalBytes;
        this.cachedBytes = cachedBytes;
        this.swapUsedBytes = swapUsedBytes;
        this.swapTotalBytes = swapTotalBytes;
        this.appCpuPercent = appCpuPercent;
        this.systemCpuPercent = systemCpuPercent;
        this.cpuCores = cpuCores;
        this.cpuAvgFreqMhz = cpuAvgFreqMhz;
        this.cpuMaxFreqMhz = cpuMaxFreqMhz;
        this.appPssBytes = appPssBytes;
        this.storagePercent = storagePercent;
        this.storageUsedBytes = storageUsedBytes;
        this.storageTotalBytes = storageTotalBytes;
        this.batteryPercent = batteryPercent;
        this.batteryTempC = batteryTempC;
        this.batteryVoltageMv = batteryVoltageMv;
        this.batteryCurrentNowUa = batteryCurrentNowUa;
        this.batteryCurrentAvgUa = batteryCurrentAvgUa;
        this.batteryChargeCounterUah = batteryChargeCounterUah;
        this.batteryStatus = batteryStatus;
        this.batteryHealth = batteryHealth;
        this.batteryPlugged = batteryPlugged;
        this.thermalStatus = thermalStatus;
        this.uptimeMs = uptimeMs;
        this.networkRxBytes = networkRxBytes;
        this.networkTxBytes = networkTxBytes;
        this.networkRxBytesPerSec = networkRxBytesPerSec;
        this.networkTxBytesPerSec = networkTxBytesPerSec;
    }

    public boolean hasSystemCpu() {
        return systemCpuPercent >= 0.0;
    }
}
