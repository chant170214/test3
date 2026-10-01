package com.pulsemonitor.app;

import android.os.BatteryManager;

import java.util.Locale;

public final class FormatUtils {
    private FormatUtils() {}

    public static String bytes(long bytes) {
        if (bytes < 0) return "--";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (value >= 1024.0 && i < units.length - 1) {
            value /= 1024.0;
            i++;
        }
        if (i <= 1) return String.format(Locale.US, "%.0f %s", value, units[i]);
        return String.format(Locale.US, "%.1f %s", value, units[i]);
    }

    public static String rate(double bytesPerSec) {
        if (Double.isNaN(bytesPerSec) || Double.isInfinite(bytesPerSec) || bytesPerSec < 0.0) {
            return "--";
        }
        return bytes((long) bytesPerSec) + "/s";
    }

    public static String percent(double value) {
        if (value < 0) return "--";
        return String.format(Locale.US, "%.1f%%", value);
    }

    public static String mhz(double value) {
        if (value < 0.0) return "--";
        if (value >= 1000.0) return String.format(Locale.US, "%.2f GHz", value / 1000.0);
        return String.format(Locale.US, "%.0f MHz", value);
    }

    public static String currentMa(int microAmps) {
        if (microAmps == Integer.MIN_VALUE) return "--";
        return String.format(Locale.US, "%+.0f mA", microAmps / 1000.0);
    }

    public static String duration(long ms) {
        long totalSeconds = ms / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0) return days + "日 " + hours + "時間";
        if (hours > 0) return hours + "時間 " + minutes + "分";
        return minutes + "分";
    }

    public static String thermalLabel(int status) {
        switch (status) {
            case 0: return "問題なし";
            case 1: return "軽度";
            case 2: return "中程度";
            case 3: return "高温";
            case 4: return "重大";
            case 5: return "緊急";
            case 6: return "停止レベル";
            default: return "不明";
        }
    }

    public static String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充電中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放電中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "充電停止";
            case BatteryManager.BATTERY_STATUS_FULL: return "満充電";
            default: return "不明";
        }
    }

    public static String batteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "高温";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "劣化";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "過電圧";
            case BatteryManager.BATTERY_HEALTH_COLD: return "低温";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "異常";
            default: return "不明";
        }
    }

    public static String powerSource(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "AC";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "ワイヤレス";
        if (android.os.Build.VERSION.SDK_INT >= 33
                && (plugged & BatteryManager.BATTERY_PLUGGED_DOCK) != 0) return "ドック";
        return "バッテリー";
    }
}
