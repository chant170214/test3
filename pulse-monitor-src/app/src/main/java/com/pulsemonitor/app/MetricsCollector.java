package com.pulsemonitor.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public final class MetricsCollector {
    private final Context appContext;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final BatteryManager batteryManager;
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

    private long lastSlowSampleMs = -1L;
    private long cachedCachedBytes = 0L;
    private long cachedSwapUsedBytes = 0L;
    private long cachedSwapTotalBytes = 0L;
    private double cachedCpuAvgFreqMhz = -1.0;
    private double cachedCpuMaxFreqMhz = -1.0;

    private int cachedBatteryPercent = -1;
    private double cachedBatteryTempC = Double.NaN;
    private int cachedBatteryVoltageMv = -1;
    private int cachedBatteryCurrentNowUa = Integer.MIN_VALUE;
    private int cachedBatteryCurrentAvgUa = Integer.MIN_VALUE;
    private int cachedBatteryChargeCounterUah = Integer.MIN_VALUE;
    private int cachedBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int cachedBatteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN;
    private int cachedBatteryPlugged = 0;
    private int cachedThermalStatus = -1;

    private long lastNetworkRx = -1L;
    private long lastNetworkTx = -1L;
    private long lastNetworkWallMs = -1L;
    private double networkRxBytesPerSec = 0.0;
    private double networkTxBytesPerSec = 0.0;

    public MetricsCollector(Context context) {
        this.appContext = context.getApplicationContext();
        this.activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        this.batteryManager = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
        this.cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public synchronized MetricSnapshot sample() {
        final long now = System.currentTimeMillis();
        final long wallMs = SystemClock.elapsedRealtime();

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalRam = Math.max(1L, memoryInfo.totalMem);
        long availableRam = Math.max(0L, memoryInfo.availMem);
        long usedRam = Math.max(0L, totalRam - availableRam);
        double ramPercent = clamp100(usedRam * 100.0 / totalRam);

        long processCpuMs = android.os.Process.getElapsedCpuTime();
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

        if (lastSlowSampleMs < 0L || wallMs - lastSlowSampleMs >= 5_000L) {
            readMemoryExtras();
            readCpuFrequencies();
            readBatteryAndThermal();
            lastSlowSampleMs = wallMs;
        }

        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx == TrafficStats.UNSUPPORTED) rx = -1L;
        if (tx == TrafficStats.UNSUPPORTED) tx = -1L;

        if (lastNetworkWallMs >= 0L && wallMs > lastNetworkWallMs) {
            double seconds = (wallMs - lastNetworkWallMs) / 1000.0;
            if (rx >= 0L && lastNetworkRx >= 0L && rx >= lastNetworkRx) {
                networkRxBytesPerSec = (rx - lastNetworkRx) / seconds;
            }
            if (tx >= 0L && lastNetworkTx >= 0L && tx >= lastNetworkTx) {
                networkTxBytesPerSec = (tx - lastNetworkTx) / seconds;
            }
        }
        lastNetworkRx = rx;
        lastNetworkTx = tx;
        lastNetworkWallMs = wallMs;

        return new MetricSnapshot(
                now,
                ramPercent,
                usedRam,
                availableRam,
                totalRam,
                cachedCachedBytes,
                cachedSwapUsedBytes,
                cachedSwapTotalBytes,
                appCpuPercent,
                systemCpuPercent,
                cpuCores,
                cachedCpuAvgFreqMhz,
                cachedCpuMaxFreqMhz,
                cachedAppPssBytes,
                cachedStoragePercent,
                cachedStorageUsedBytes,
                cachedStorageTotalBytes,
                cachedBatteryPercent,
                cachedBatteryTempC,
                cachedBatteryVoltageMv,
                cachedBatteryCurrentNowUa,
                cachedBatteryCurrentAvgUa,
                cachedBatteryChargeCounterUah,
                cachedBatteryStatus,
                cachedBatteryHealth,
                cachedBatteryPlugged,
                cachedThermalStatus,
                wallMs,
                rx,
                tx,
                networkRxBytesPerSec,
                networkTxBytesPerSec
        );
    }

    private void readMemoryExtras() {
        Map<String, Long> values = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon);
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length == 0) continue;
                try {
                    values.put(key, Long.parseLong(parts[0]) * 1024L);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Throwable ignored) {
            return;
        }

        cachedCachedBytes = Math.max(0L,
                values.getOrDefault("Cached", 0L) + values.getOrDefault("SReclaimable", 0L));
        cachedSwapTotalBytes = Math.max(0L, values.getOrDefault("SwapTotal", 0L));
        long swapFree = Math.max(0L, values.getOrDefault("SwapFree", 0L));
        cachedSwapUsedBytes = Math.max(0L, cachedSwapTotalBytes - swapFree);
    }

    private void readCpuFrequencies() {
        double sumMhz = 0.0;
        double maxMhz = -1.0;
        int count = 0;

        for (int i = 0; i < cpuCores; i++) {
            File file = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                if (line == null) continue;
                double khz = Double.parseDouble(line.trim());
                double mhz = khz / 1000.0;
                if (mhz > 0.0) {
                    sumMhz += mhz;
                    maxMhz = Math.max(maxMhz, mhz);
                    count++;
                }
            } catch (Throwable ignored) {
            }
        }

        cachedCpuAvgFreqMhz = count > 0 ? sumMhz / count : -1.0;
        cachedCpuMaxFreqMhz = maxMhz;
    }

    private void readBatteryAndThermal() {
        Intent battery = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) {
                cachedBatteryPercent = Math.round(level * 100f / scale);
            }

            int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            cachedBatteryTempC = tempTenths == Integer.MIN_VALUE ? Double.NaN : tempTenths / 10.0;
            cachedBatteryVoltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            cachedBatteryStatus = battery.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            cachedBatteryHealth = battery.getIntExtra(
                    BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            cachedBatteryPlugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }

        if (batteryManager != null) {
            cachedBatteryCurrentNowUa = safeBatteryInt(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            cachedBatteryCurrentAvgUa = safeBatteryInt(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            cachedBatteryChargeCounterUah = safeBatteryInt(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            cachedThermalStatus = powerManager.getCurrentThermalStatus();
        }
    }

    private int safeBatteryInt(int property) {
        try {
            return batteryManager.getIntProperty(property);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
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
