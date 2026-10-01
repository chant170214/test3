package com.pulsemonitor.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 1001;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private MetricsCollector localCollector;
    private HistoryDatabase historyDb;

    private TextView statusText;
    private Button monitorButton;

    private TextView cpuValue;
    private TextView cpuDetail;
    private TextView ramValue;
    private TextView ramDetail;
    private TextView appRamValue;

    private TextView storageValue;
    private TextView storageDetail;

    private TextView batteryValue;
    private TextView batteryDetail;
    private TextView batteryCurrentValue;
    private TextView batteryCurrentDetail;

    private TextView thermalValue;
    private TextView uptimeValue;

    private TextView networkDownValue;
    private TextView networkDownDetail;
    private TextView networkUpValue;
    private TextView networkUpDetail;

    private TextView swapValue;
    private TextView swapDetail;
    private TextView cacheValue;
    private TextView cacheDetail;

    private TextView deviceValue;
    private TextView deviceDetail;

    private HistoryGraphView graphView;
    private Spinner rangeSpinner;
    private TextView cpuLegend;

    private boolean pendingStartAfterPermission;
    private long lastGraphRefresh = 0L;

    private int background;
    private int card;
    private int primaryText;
    private int secondaryText;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshMetrics();
            uiHandler.postDelayed(this, 2_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chooseColors();
        localCollector = new MetricsCollector(this);
        historyDb = new HistoryDatabase(this);
        setContentView(buildUi());
        updateRunningUi();
        refreshMetrics();
        refreshHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(refreshRunnable);
        uiHandler.post(refreshRunnable);
        updateRunningUi();
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(refreshRunnable);
        dbExecutor.shutdownNow();
        historyDb.close();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Pulse Monitor", 30, primaryText, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = text(
                "CPU・RAM・通信・バッテリーを軽量リアルタイム監視",
                14,
                secondaryText,
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.topMargin = dp(4);
        root.addView(subtitle, subLp);

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams statusLp = lpMatchWrap();
        statusLp.topMargin = dp(18);
        root.addView(statusCard, statusLp);

        statusText = text("停止中", 15, secondaryText, Typeface.BOLD);
        statusCard.addView(statusText);

        TextView statusHelp = text(
                "Android 16では左上にLive Updateチップ（CPUxx% / RAMxx%）を表示します。",
                12,
                secondaryText,
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams helpLp = lpMatchWrap();
        helpLp.topMargin = dp(4);
        statusCard.addView(statusHelp, helpLp);

        monitorButton = new Button(this);
        monitorButton.setAllCaps(false);
        monitorButton.setTextSize(16);
        monitorButton.setMinHeight(dp(50));
        monitorButton.setOnClickListener(v -> toggleMonitoring());
        LinearLayout.LayoutParams buttonLp = lpMatchWrap();
        buttonLp.topMargin = dp(12);
        statusCard.addView(monitorButton, buttonLp);

        if (Build.VERSION.SDK_INT >= 36) {
            Button liveSettingsButton = new Button(this);
            liveSettingsButton.setAllCaps(false);
            liveSettingsButton.setText("Live Updateの設定");
            liveSettingsButton.setOnClickListener(v -> openLiveUpdateSettings());
            LinearLayout.LayoutParams liveSettingsLp = lpMatchWrap();
            liveSettingsLp.topMargin = dp(6);
            statusCard.addView(liveSettingsButton, liveSettingsLp);
        }

        TextView liveHeading = section("リアルタイム");
        LinearLayout.LayoutParams headingLp = lpMatchWrap();
        headingLp.topMargin = dp(24);
        root.addView(liveHeading, headingLp);

        LinearLayout row1 = horizontalRow();
        root.addView(row1, rowLp());
        LinearLayout cpuCard = metricCard("CPU", row1);
        cpuValue = metricValue(cpuCard);
        cpuDetail = metricDetail(cpuCard);
        LinearLayout ramCard = metricCard("RAM", row1);
        ramValue = metricValue(ramCard);
        ramDetail = metricDetail(ramCard);

        LinearLayout row2 = horizontalRow();
        root.addView(row2, rowLp());
        LinearLayout appRamCard = metricCard("Monitor RAM", row2);
        appRamValue = metricValue(appRamCard);
        metricDetail(appRamCard).setText("このアプリのPSS");
        LinearLayout storageCard = metricCard("ストレージ", row2);
        storageValue = metricValue(storageCard);
        storageDetail = metricDetail(storageCard);

        LinearLayout row3 = horizontalRow();
        root.addView(row3, rowLp());
        LinearLayout downCard = metricCard("ダウンロード", row3);
        networkDownValue = metricValue(downCard);
        networkDownDetail = metricDetail(downCard);
        LinearLayout upCard = metricCard("アップロード", row3);
        networkUpValue = metricValue(upCard);
        networkUpDetail = metricDetail(upCard);

        LinearLayout row4 = horizontalRow();
        root.addView(row4, rowLp());
        LinearLayout batteryCard = metricCard("バッテリー", row4);
        batteryValue = metricValue(batteryCard);
        batteryDetail = metricDetail(batteryCard);
        LinearLayout currentCard = metricCard("バッテリー電流", row4);
        batteryCurrentValue = metricValue(currentCard);
        batteryCurrentDetail = metricDetail(currentCard);

        LinearLayout row5 = horizontalRow();
        root.addView(row5, rowLp());
        LinearLayout swapCard = metricCard("Swap / ZRAM", row5);
        swapValue = metricValue(swapCard);
        swapDetail = metricDetail(swapCard);
        LinearLayout cacheCard = metricCard("RAMキャッシュ", row5);
        cacheValue = metricValue(cacheCard);
        cacheDetail = metricDetail(cacheCard);

        LinearLayout row6 = horizontalRow();
        root.addView(row6, rowLp());
        LinearLayout thermalCard = metricCard("熱状態", row6);
        thermalValue = metricValue(thermalCard);
        metricDetail(thermalCard).setText("Android Thermal API");
        LinearLayout uptimeCard = metricCard("端末稼働時間", row6);
        uptimeValue = metricValue(uptimeCard);
        metricDetail(uptimeCard).setText("最終起動から");

        LinearLayout deviceCard = card();
        LinearLayout.LayoutParams deviceLp = lpMatchWrap();
        deviceLp.topMargin = dp(10);
        root.addView(deviceCard, deviceLp);
        TextView deviceLabel = text("端末情報", 13, secondaryText, Typeface.BOLD);
        deviceCard.addView(deviceLabel);
        deviceValue = text("--", 18, primaryText, Typeface.BOLD);
        LinearLayout.LayoutParams deviceValueLp = lpMatchWrap();
        deviceValueLp.topMargin = dp(6);
        deviceCard.addView(deviceValue, deviceValueLp);
        deviceDetail = text("--", 12, secondaryText, Typeface.NORMAL);
        LinearLayout.LayoutParams deviceDetailLp = lpMatchWrap();
        deviceDetailLp.topMargin = dp(4);
        deviceCard.addView(deviceDetail, deviceDetailLp);

        TextView historyHeading = section("使用履歴");
        LinearLayout.LayoutParams histHeadLp = lpMatchWrap();
        histHeadLp.topMargin = dp(26);
        root.addView(historyHeading, histHeadLp);

        LinearLayout historyCard = card();
        LinearLayout.LayoutParams histCardLp = lpMatchWrap();
        histCardLp.topMargin = dp(8);
        root.addView(historyCard, histCardLp);

        rangeSpinner = new Spinner(this);
        String[] ranges = {"過去1時間", "過去6時間", "過去24時間", "過去7日"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ranges
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rangeSpinner.setAdapter(adapter);
        rangeSpinner.setSelection(2);
        rangeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshHistory();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        historyCard.addView(rangeSpinner, lpMatchWrap());

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams legendLp = lpMatchWrap();
        legendLp.topMargin = dp(10);
        historyCard.addView(legend, legendLp);

        TextView ramLegend = text("● RAM", 12, 0xFF5E5CE6, Typeface.BOLD);
        legend.addView(ramLegend);

        cpuLegend = text("● CPU", 12, 0xFF30B0C7, Typeface.BOLD);
        LinearLayout.LayoutParams cpuLegendLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        cpuLegendLp.leftMargin = dp(14);
        legend.addView(cpuLegend, cpuLegendLp);

        graphView = new HistoryGraphView(this);
        LinearLayout.LayoutParams graphLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(230)
        );
        graphLp.topMargin = dp(8);
        historyCard.addView(graphView, graphLp);

        TextView historyNote = text(
                "履歴は15秒ごとに保存し、7日を超えたデータは自動削除します。",
                12,
                secondaryText,
                Typeface.NORMAL
        );
        historyNote.setLineSpacing(0f, 1.15f);
        historyCard.addView(historyNote, lpMatchWrap());

        Button clearButton = new Button(this);
        clearButton.setAllCaps(false);
        clearButton.setText("履歴を消去");
        clearButton.setOnClickListener(v -> confirmClearHistory());
        LinearLayout.LayoutParams clearLp = lpMatchWrap();
        clearLp.topMargin = dp(8);
        historyCard.addView(clearButton, clearLp);

        TextView noteHeading = section("取得制限");
        LinearLayout.LayoutParams noteHeadLp = lpMatchWrap();
        noteHeadLp.topMargin = dp(24);
        root.addView(noteHeading, noteHeadLp);

        TextView cpuNote = text(
                "Androidの通常アプリは端末全体CPUや一部センサーへのアクセスが制限されています。取得できる値は端末全体を表示し、取得できない項目は「--」またはMonitor自身の値へ自動フォールバックします。",
                13,
                secondaryText,
                Typeface.NORMAL
        );
        cpuNote.setLineSpacing(dp(2), 1.12f);
        LinearLayout.LayoutParams noteLp = lpMatchWrap();
        noteLp.topMargin = dp(6);
        root.addView(cpuNote, noteLp);

        return scroll;
    }

    private void refreshMetrics() {
        boolean running = isMonitoringRunning();
        MetricSnapshot snapshot = running ? MetricsStore.getLatest() : null;
        if (snapshot == null) snapshot = localCollector.sample();
        applySnapshot(snapshot);
        updateRunningUi();

        long now = System.currentTimeMillis();
        if (running && now - lastGraphRefresh >= 10_000L) {
            lastGraphRefresh = now;
            refreshHistory();
        }
    }

    private void applySnapshot(MetricSnapshot s) {
        if (s.hasSystemCpu()) {
            cpuValue.setText(FormatUtils.percent(s.systemCpuPercent));
            cpuLegend.setText("● 端末CPU");
        } else {
            cpuValue.setText(FormatUtils.percent(s.appCpuPercent));
            cpuLegend.setText("● Monitor CPU");
        }

        String freq = s.cpuAvgFreqMhz >= 0
                ? FormatUtils.mhz(s.cpuAvgFreqMhz)
                : "--";
        cpuDetail.setText(s.cpuCores + "コア · 平均 " + freq);

        ramValue.setText(FormatUtils.percent(s.ramPercent));
        ramDetail.setText(
                FormatUtils.bytes(s.ramUsedBytes)
                        + " / "
                        + FormatUtils.bytes(s.ramTotalBytes)
                        + " · 空き "
                        + FormatUtils.bytes(s.ramAvailableBytes)
        );

        appRamValue.setText(FormatUtils.bytes(s.appPssBytes));

        storageValue.setText(FormatUtils.percent(s.storagePercent));
        storageDetail.setText(
                FormatUtils.bytes(s.storageUsedBytes)
                        + " / "
                        + FormatUtils.bytes(s.storageTotalBytes)
        );

        networkDownValue.setText(FormatUtils.rate(s.networkRxBytesPerSec));
        networkDownDetail.setText("起動後合計 " + FormatUtils.bytes(s.networkRxBytes));

        networkUpValue.setText(FormatUtils.rate(s.networkTxBytesPerSec));
        networkUpDetail.setText("起動後合計 " + FormatUtils.bytes(s.networkTxBytes));

        batteryValue.setText(s.batteryPercent >= 0 ? s.batteryPercent + "%" : "--");
        String temp = Double.isNaN(s.batteryTempC)
                ? "--"
                : String.format(Locale.US, "%.1f°C", s.batteryTempC);
        String voltage = s.batteryVoltageMv > 0
                ? String.format(Locale.US, "%.3f V", s.batteryVoltageMv / 1000.0)
                : "--";
        batteryDetail.setText(temp + " · " + voltage);

        batteryCurrentValue.setText(FormatUtils.currentMa(s.batteryCurrentNowUa));
        String avgCurrent = FormatUtils.currentMa(s.batteryCurrentAvgUa);
        batteryCurrentDetail.setText(
                FormatUtils.batteryStatus(s.batteryStatus)
                        + " · 平均 "
                        + avgCurrent
        );

        if (s.swapTotalBytes > 0L) {
            swapValue.setText(FormatUtils.bytes(s.swapUsedBytes));
            swapDetail.setText(
                    "使用 / "
                            + FormatUtils.bytes(s.swapTotalBytes)
            );
        } else {
            swapValue.setText("--");
            swapDetail.setText("取得不可または未使用");
        }

        cacheValue.setText(FormatUtils.bytes(s.cachedBytes));
        cacheDetail.setText("Cached + SReclaimable");

        thermalValue.setText(FormatUtils.thermalLabel(s.thermalStatus));
        uptimeValue.setText(FormatUtils.duration(s.uptimeMs));

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        deviceValue.setText((manufacturer + " " + model).trim());
        deviceDetail.setText(
                "Android "
                        + Build.VERSION.RELEASE
                        + " · API "
                        + Build.VERSION.SDK_INT
                        + " · Battery "
                        + FormatUtils.batteryHealth(s.batteryHealth)
                        + " · "
                        + FormatUtils.powerSource(s.batteryPlugged)
        );
    }

    private void toggleMonitoring() {
        if (isMonitoringRunning()) {
            stopService(new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_STOP));
            getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(MonitorService.KEY_RUNNING, false)
                    .apply();
            updateRunningUi();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
            return;
        }
        startMonitoring();
    }

    private void startMonitoring() {
        Intent intent = new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_START);
        startForegroundService(intent);
        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(MonitorService.KEY_RUNNING, true)
                .apply();
        updateRunningUi();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && pendingStartAfterPermission) {
            pendingStartAfterPermission = false;
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("通知の許可が必要です")
                        .setMessage("ステータスバーと通知欄にCPU/RAMを常時表示するため、通知を許可してください。")
                        .setPositiveButton("設定を開く", (d, w) -> {
                            Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(settings);
                        })
                        .setNegativeButton("キャンセル", null)
                        .show();
            }
        }
    }

    private void openLiveUpdateSettings() {
        try {
            Intent intent = new Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS");
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(fallback);
        }
    }

    private void updateRunningUi() {
        boolean running = isMonitoringRunning();
        statusText.setText(
                running
                        ? "● 監視中 · 常駐通知を2秒ごとに更新"
                        : "○ 停止中 · 常駐処理なし"
        );
        statusText.setTextColor(running ? 0xFF34C759 : secondaryText);
        monitorButton.setText(running ? "監視を停止" : "常駐モニターを開始");
    }

    private boolean isMonitoringRunning() {
        return getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE)
                .getBoolean(MonitorService.KEY_RUNNING, false);
    }

    private void refreshHistory() {
        if (historyDb == null || rangeSpinner == null || graphView == null) return;

        int selection = rangeSpinner.getSelectedItemPosition();
        long span;
        switch (selection) {
            case 0:
                span = 60L * 60L * 1000L;
                break;
            case 1:
                span = 6L * 60L * 60L * 1000L;
                break;
            case 3:
                span = 7L * 24L * 60L * 60L * 1000L;
                break;
            case 2:
            default:
                span = 24L * 60L * 60L * 1000L;
                break;
        }

        long since = System.currentTimeMillis() - span;
        dbExecutor.execute(() -> {
            List<HistoryDatabase.GraphPoint> points = historyDb.query(since, 260);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    graphView.setPoints(points);
                }
            });
        });
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("履歴を消去しますか？")
                .setMessage("保存された使用履歴をすべて削除します。")
                .setPositiveButton("消去", (dialog, which) -> dbExecutor.execute(() -> {
                    historyDb.clearAll();
                    runOnUiThread(() -> {
                        graphView.setPoints(null);
                        Toast.makeText(
                                this,
                                "履歴を消去しました",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(15), dp(16), dp(15));
        v.setBackground(roundRect(card, 18));
        return v;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout metricCard(String label, LinearLayout row) {
        LinearLayout c = card();
        TextView l = text(label, 12, secondaryText, Typeface.BOLD);
        c.addView(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        if (row.getChildCount() > 0) lp.leftMargin = dp(10);
        row.addView(c, lp);
        return c;
    }

    private TextView metricValue(LinearLayout cardView) {
        TextView value = text("--", 24, primaryText, Typeface.BOLD);
        LinearLayout.LayoutParams lp = lpMatchWrap();
        lp.topMargin = dp(7);
        cardView.addView(value, lp);
        return value;
    }

    private TextView metricDetail(LinearLayout cardView) {
        TextView detail = text("--", 11, secondaryText, Typeface.NORMAL);
        LinearLayout.LayoutParams lp = lpMatchWrap();
        lp.topMargin = dp(3);
        cardView.addView(detail, lp);
        return detail;
    }

    private TextView section(String s) {
        return text(s, 17, primaryText, Typeface.BOLD);
    }

    private TextView text(String value, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", style));
        return t;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = lpMatchWrap();
        lp.topMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void chooseColors() {
        boolean dark =
                (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            background = Color.rgb(14, 14, 16);
            card = Color.rgb(28, 28, 31);
            primaryText = Color.rgb(245, 245, 247);
            secondaryText = Color.rgb(162, 162, 168);
        } else {
            background = Color.rgb(246, 246, 248);
            card = Color.WHITE;
            primaryText = Color.rgb(28, 28, 30);
            secondaryText = Color.rgb(99, 99, 102);
        }
    }
}
