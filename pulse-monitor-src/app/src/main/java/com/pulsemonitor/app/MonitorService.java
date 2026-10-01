package com.pulsemonitor.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitorService extends Service {
    public static final String ACTION_START = "com.pulsemonitor.app.action.START";
    public static final String ACTION_STOP = "com.pulsemonitor.app.action.STOP";
    public static final String PREFS = "monitor_prefs";
    public static final String KEY_RUNNING = "running";

    // V2 uses a new channel because Android doesn't let an app raise the
    // importance of an existing channel after it has been created.
    private static final String CHANNEL_ID = "monitoring_live_v3";
    private static final int NOTIFICATION_ID = 4107;
    private static final long HISTORY_INTERVAL_MS = 15_000L;
    private static final long PRUNE_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private ScheduledExecutorService executor;
    private MetricsCollector collector;
    private HistoryDatabase historyDb;
    private NotificationManager notificationManager;
    private long lastHistoryWrite = 0L;
    private long lastPrune = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        collector = new MetricsCollector(this);
        historyDb = new HistoryDatabase(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply();

        Notification first = buildNotification(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    first,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, first);
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PulseMonitorSampler");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.scheduleAtFixedRate(this::sampleAndPublish, 0L, 2L, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void sampleAndPublish() {
        try {
            MetricSnapshot snapshot = collector.sample();
            MetricsStore.setLatest(snapshot);

            long now = System.currentTimeMillis();
            if (now - lastHistoryWrite >= HISTORY_INTERVAL_MS) {
                historyDb.insert(snapshot);
                lastHistoryWrite = now;
            }
            if (now - lastPrune >= PRUNE_INTERVAL_MS) {
                historyDb.prune();
                lastPrune = now;
            }

            notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot));
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification(MetricSnapshot s) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title;
        String text;
        String bigText;

        if (s == null) {
            title = "Pulse Monitor · 監視中";
            text = "端末状態を計測しています…";
            bigText = text;
        } else {
            String cpuValue = s.hasSystemCpu()
                    ? FormatUtils.percent(s.systemCpuPercent)
                    : FormatUtils.percent(s.appCpuPercent);
            String cpuLabel = s.hasSystemCpu() ? "CPU" : "APP CPU";
            String temp = Double.isNaN(s.batteryTempC)
                    ? "--"
                    : String.format(Locale.US, "%.1f°C", s.batteryTempC);

            title = cpuLabel + " " + cpuValue + "  ·  RAM " + FormatUtils.percent(s.ramPercent);
            text = "↓ " + FormatUtils.rate(s.networkRxBytesPerSec)
                    + "  ↑ " + FormatUtils.rate(s.networkTxBytesPerSec)
                    + "  ·  " + temp;

            String freq = s.cpuAvgFreqMhz >= 0
                    ? FormatUtils.mhz(s.cpuAvgFreqMhz)
                    : "--";

            String batteryCurrent = FormatUtils.currentMa(s.batteryCurrentNowUa);

            bigText =
                    cpuLabel + " " + cpuValue
                            + " · " + s.cpuCores + " cores"
                            + " · " + freq + "\n"
                            + "RAM " + FormatUtils.percent(s.ramPercent)
                            + " · " + FormatUtils.bytes(s.ramUsedBytes)
                            + " / " + FormatUtils.bytes(s.ramTotalBytes) + "\n"
                            + "NET ↓ " + FormatUtils.rate(s.networkRxBytesPerSec)
                            + " · ↑ " + FormatUtils.rate(s.networkTxBytesPerSec) + "\n"
                            + "BAT " + (s.batteryPercent >= 0 ? s.batteryPercent + "%" : "--")
                            + " · " + temp
                            + " · " + batteryCurrent
                            + " · " + FormatUtils.powerSource(s.batteryPlugged);
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitor)
                .setColor(Color.rgb(94, 92, 230))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, "開く", openPending).build())
                .addAction(new Notification.Action.Builder(null, "停止", stopPending).build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        if (Build.VERSION.SDK_INT >= 36) {
            Bundle liveExtras = new Bundle();
            liveExtras.putBoolean("android.requestPromotedOngoing", true);
            liveExtras.putString("android.shortCriticalText", buildChipText(s));
            builder.addExtras(liveExtras);
        }

        return builder.build();
    }

    private String buildChipText(MetricSnapshot s) {
        if (s == null) return "LIVE";
        if (s.hasSystemCpu()) {
            return "CPU" + Math.round(s.systemCpuPercent) + "%";
        }
        return "RAM" + Math.round(s.ramPercent) + "%";
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ライブモニター",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Android 16のLive Updateチップと通知欄にCPU・RAMなどを常時表示します");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
    }

    private void stopMonitoring() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply();
        if (executor != null) executor.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply();
        if (executor != null) executor.shutdownNow();
        if (historyDb != null) historyDb.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
