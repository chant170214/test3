package com.pulsemonitor.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;

public final class MetricsCollector {
    private final Context appContext;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final int cpuCores;
    private final ProcStatReader procStatReader = new ProcStatReader();

    private long lastProcessCpuMs = -1L;
    private long lastWallMs = -1L;

    private long lastPssSampleMs = -1L;
    private long cachedAppPssBytes = 0L;
    private long lastStorageSampleMs = -1L;
    private long cachedStorageTotalBytes = 1L;
    private long cachedStorageUsedBytes = 0L;
    private double cachedStoragePercent = 0.0;
    private long lastDeviceStateSampleMs = -1L;
    private int cachedBatteryPercent = -1;
    private double cachedBatteryTempC = Double.NaN;
    private int cachedThermalStatus = -1;

    public MetricsCollector(Context context) {
        this.appContext = context.getApplicationContext();
        this.activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        this.cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public synchronized MetricSnapshot sample() {
        final long now = System.currentTimeMillis();

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalRam = Math.max(1L, memoryInfo.totalMem);
        long usedRam = Math.max(0L, totalRam - memoryInfo.availMem);
        double ramPercent = clamp100(usedRam * 100.0 / totalRam);

        long processCpuMs = android.os.Process.getElapsedCpuTime();
        long wallMs = SystemClock.elapsedRealtime();
        double appCpuPercent = 0.0;
        if (lastProcessCpuMs >= 0L && lastWallMs >= 0L) {
            long cpuDelta = Math.max(0L, processCpuMs - lastProcessCpuMs);
            long wallDelta = Math.max(1L, wallMs - lastWallMs);
            appCpuPercent = clamp100((cpuDelta * 100.0) / (wallDelta * cpuCores));
        }
        lastProcessCpuMs = processCpuMs;
        lastWallMs = wallMs;

        double systemCpuPercent = procStatReader.readPercent();

        if (lastPssSampleMs < 0L || wallMs - lastPssSampleMs >= 10_000L) {
            cachedAppPssBytes = Math.max(0L, Debug.getPss()) * 1024L;
            lastPssSampleMs = wallMs;
        }

        if (lastStorageSampleMs < 0L || wallMs - lastStorageSampleMs >= 30_000L) {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            cachedStorageTotalBytes = Math.max(1L, statFs.getTotalBytes());
            long availableStorage = Math.max(0L, statFs.getAvailableBytes());
            cachedStorageUsedBytes = Math.max(0L, cachedStorageTotalBytes - availableStorage);
            cachedStoragePercent = clamp100(cachedStorageUsedBytes * 100.0 / cachedStorageTotalBytes);
            lastStorageSampleMs = wallMs;
        }

        if (lastDeviceStateSampleMs < 0L || wallMs - lastDeviceStateSampleMs >= 10_000L) {
            Intent battery = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra("level", -1);
                int scale = battery.getIntExtra("scale", 100);
                if (level >= 0 && scale > 0) {
                    cachedBatteryPercent = Math.round(level * 100f / scale);
                }
                int tempTenths = battery.getIntExtra("temperature", Integer.MIN_VALUE);
                if (tempTenths != Integer.MIN_VALUE) {
                    cachedBatteryTempC = tempTenths / 10.0;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                cachedThermalStatus = powerManager.getCurrentThermalStatus();
            }
            lastDeviceStateSampleMs = wallMs;
        }

        return new MetricSnapshot(
                now,
                ramPercent,
                usedRam,
                totalRam,
                appCpuPercent,
                systemCpuPercent,
                cachedAppPssBytes,
                cachedStoragePercent,
                cachedStorageUsedBytes,
                cachedStorageTotalBytes,
                cachedBatteryPercent,
                cachedBatteryTempC,
                cachedThermalStatus,
                SystemClock.elapsedRealtime(),
                cpuCores
        );
    }

    private static double clamp100(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static final class ProcStatReader {
        private Boolean available = null;
        private long lastTotal = -1L;
        private long lastIdle = -1L;

        synchronized double readPercent() {
            if (Boolean.FALSE.equals(available)) return -1.0;
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
                String line = reader.readLine();
                if (line == null || !line.startsWith("cpu ")) {
                    available = false;
                    return -1.0;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5) {
                    available = false;
                    return -1.0;
                }

                long user = parse(parts, 1);
                long nice = parse(parts, 2);
                long system = parse(parts, 3);
                long idle = parse(parts, 4);
                long ioWait = parse(parts, 5);
                long irq = parse(parts, 6);
                long softIrq = parse(parts, 7);
                long steal = parse(parts, 8);

                long idleAll = idle + ioWait;
                long total = user + nice + system + idle + ioWait + irq + softIrq + steal;
                available = true;

                if (lastTotal < 0L) {
                    lastTotal = total;
                    lastIdle = idleAll;
                    return -1.0;
                }

                long totalDelta = total - lastTotal;
                long idleDelta = idleAll - lastIdle;
                lastTotal = total;
                lastIdle = idleAll;
                if (totalDelta <= 0L) return -1.0;
                return clamp100((totalDelta - idleDelta) * 100.0 / totalDelta);
            } catch (Throwable ignored) {
                available = false;
                return -1.0;
            }
        }

        private static long parse(String[] parts, int index) {
            if (index >= parts.length) return 0L;
            try {
                return Long.parseLong(parts[index]);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }
}
