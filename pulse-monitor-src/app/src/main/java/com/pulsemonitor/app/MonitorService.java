package com.pulsemonitor.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitorService extends Service {
    public static final String ACTION_START = "com.pulsemonitor.app.action.START";
    public static final String ACTION_STOP = "com.pulsemonitor.app.action.STOP";
    public static final String PREFS = "monitor_prefs";
    public static final String KEY_RUNNING = "running";

    private static final String CHANNEL_ID = "monitoring";
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
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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

        String text;
        String bigText;
        if (s == null) {
            text = "計測を開始しています…";
            bigText = text;
        } else {
            String cpu = s.hasSystemCpu()
                    ? "CPU " + FormatUtils.percent(s.systemCpuPercent)
                    : "CPU(アプリ) " + FormatUtils.percent(s.appCpuPercent);
            String temp = Double.isNaN(s.batteryTempC)
                    ? ""
                    : String.format(Locale.US, " · %.1f°C", s.batteryTempC);
            text = cpu + " · RAM " + FormatUtils.percent(s.ramPercent) + temp;
            bigText = text + "\n" +
                    "RAM " + FormatUtils.bytes(s.ramUsedBytes) + " / " + FormatUtils.bytes(s.ramTotalBytes) +
                    " · Battery " + (s.batteryPercent >= 0 ? s.batteryPercent + "%" : "--") +
                    " · 2秒更新";
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitor)
                .setContentTitle("Pulse Monitor")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, "停止", stopPending).build())
                .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "リアルタイム監視",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("CPU・RAMなどの端末状態を継続表示します");
        channel.setSound(null, null);
        channel.enableVibration(false);
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
