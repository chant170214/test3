package com.pulsemonitor.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class HistoryGraphView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cpuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ramPath = new Path();
    private final Path cpuPath = new Path();
    private List<HistoryDatabase.GraphPoint> points = new ArrayList<>();

    public HistoryGraphView(Context context) {
        super(context);
        init();
    }

    public HistoryGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(0x335F6368);

        labelPaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setColor(0xFF8E8E93);

        ramPaint.setStyle(Paint.Style.STROKE);
        ramPaint.setStrokeWidth(2.2f * density);
        ramPaint.setStrokeCap(Paint.Cap.ROUND);
        ramPaint.setStrokeJoin(Paint.Join.ROUND);
        ramPaint.setColor(0xFF5E5CE6);

        cpuPaint.setStyle(Paint.Style.STROKE);
        cpuPaint.setStrokeWidth(2.2f * density);
        cpuPaint.setStrokeCap(Paint.Cap.ROUND);
        cpuPaint.setStrokeJoin(Paint.Join.ROUND);
        cpuPaint.setColor(0xFF30B0C7);

        emptyPaint.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
        emptyPaint.setColor(0xFF8E8E93);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPoints(List<HistoryDatabase.GraphPoint> data) {
        points = data == null ? new ArrayList<>() : new ArrayList<>(data);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 38f * density;
        float right = getWidth() - 10f * density;
        float top = 12f * density;
        float bottom = getHeight() - 24f * density;

        if (right <= left || bottom <= top) return;

        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            int value = 100 - i * 25;
            canvas.drawText(value + "%", 4f * density, y + 4f * density, labelPaint);
        }

        if (points.size() < 2) {
            canvas.drawText("履歴がたまるとここにグラフが表示されます", getWidth() / 2f, (top + bottom) / 2f, emptyPaint);
            return;
        }

        long minTime = points.get(0).timestamp;
        long maxTime = points.get(points.size() - 1).timestamp;
        if (maxTime <= minTime) maxTime = minTime + 1L;

        ramPath.reset();
        cpuPath.reset();
        boolean ramStarted = false;
        boolean cpuStarted = false;

        for (HistoryDatabase.GraphPoint p : points) {
            float x = left + (right - left) * (p.timestamp - minTime) / (float) (maxTime - minTime);
            float ramY = bottom - (bottom - top) * clamp(p.ram) / 100f;
            double cpuValue = p.systemCpu >= 0.0 ? p.systemCpu : p.appCpu;
            float cpuY = bottom - (bottom - top) * clamp(cpuValue) / 100f;

            if (!ramStarted) {
                ramPath.moveTo(x, ramY);
                ramStarted = true;
            } else {
                ramPath.lineTo(x, ramY);
            }

            if (!cpuStarted) {
                cpuPath.moveTo(x, cpuY);
                cpuStarted = true;
            } else {
                cpuPath.lineTo(x, cpuY);
            }
        }

        canvas.drawPath(ramPath, ramPaint);
        canvas.drawPath(cpuPath, cpuPaint);
    }

    private float clamp(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0f;
        return (float) Math.max(0.0, Math.min(100.0, v));
    }
}
