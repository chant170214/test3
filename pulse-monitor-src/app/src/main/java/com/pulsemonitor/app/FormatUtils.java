package com.pulsemonitor.app;

import java.util.Locale;

public final class FormatUtils {
    private FormatUtils() {}

    public static String bytes(long bytes) {
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

    public static String percent(double value) {
        if (value < 0) return "--";
        return String.format(Locale.US, "%.1f%%", value);
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
}
