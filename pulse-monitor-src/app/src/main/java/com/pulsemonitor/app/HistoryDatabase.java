package com.pulsemonitor.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class HistoryDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "metrics.db";
    private static final int DB_VERSION = 1;
    private static final long RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;

    public HistoryDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE samples (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp INTEGER NOT NULL," +
                "ram_percent REAL NOT NULL," +
                "app_cpu REAL NOT NULL," +
                "system_cpu REAL NOT NULL," +
                "battery_percent INTEGER NOT NULL," +
                "battery_temp REAL NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_samples_timestamp ON samples(timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS samples");
        onCreate(db);
    }

    public void insert(MetricSnapshot s) {
        ContentValues values = new ContentValues();
        values.put("timestamp", s.timestampMs);
        values.put("ram_percent", s.ramPercent);
        values.put("app_cpu", s.appCpuPercent);
        values.put("system_cpu", s.systemCpuPercent);
        values.put("battery_percent", s.batteryPercent);
        values.put("battery_temp", Double.isNaN(s.batteryTempC) ? -1.0 : s.batteryTempC);
        getWritableDatabase().insert("samples", null, values);
    }

    public void prune() {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        getWritableDatabase().delete("samples", "timestamp < ?", new String[]{Long.toString(cutoff)});
    }

    public void clearAll() {
        getWritableDatabase().delete("samples", null, null);
    }

    public List<GraphPoint> query(long sinceMs, int maxPoints) {
        long now = System.currentTimeMillis();
        long duration = Math.max(1L, now - sinceMs);
        long bucketMs = Math.max(1_000L, duration / Math.max(40, maxPoints));

        String sql = "SELECT ((timestamp / ?) * ?) AS bucket," +
                " AVG(ram_percent)," +
                " AVG(app_cpu)," +
                " AVG(CASE WHEN system_cpu >= 0 THEN system_cpu END)" +
                " FROM samples WHERE timestamp >= ?" +
                " GROUP BY bucket ORDER BY bucket ASC";

        List<GraphPoint> points = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                sql,
                new String[]{Long.toString(bucketMs), Long.toString(bucketMs), Long.toString(sinceMs)})) {
            while (c.moveToNext()) {
                long timestamp = c.getLong(0);
                double ram = c.getDouble(1);
                double appCpu = c.getDouble(2);
                double systemCpu = c.isNull(3) ? -1.0 : c.getDouble(3);
                points.add(new GraphPoint(timestamp, ram, appCpu, systemCpu));
            }
        }
        return points;
    }

    public static final class GraphPoint {
        public final long timestamp;
        public final double ram;
        public final double appCpu;
        public final double systemCpu;

        public GraphPoint(long timestamp, double ram, double appCpu, double systemCpu) {
            this.timestamp = timestamp;
            this.ram = ram;
            this.appCpu = appCpu;
            this.systemCpu = systemCpu;
        }
    }
}
