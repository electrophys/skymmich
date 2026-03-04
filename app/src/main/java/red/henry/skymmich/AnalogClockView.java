package red.henry.skymmich;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

public class AnalogClockView extends View {

    private TimeZone timeZone = TimeZone.getDefault();
    private final Paint dialPaint;
    private final Paint hourPaint;
    private final Paint minutePaint;
    private final Paint secondPaint;
    private final Paint centrePaint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (running) handler.postDelayed(this, 1000);
        }
    };

    public AnalogClockView(Context context) {
        this(context, null);
    }

    public AnalogClockView(Context context, AttributeSet attrs) {
        super(context, attrs);

        dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setColor(0xAAFFFFFF);
        dialPaint.setStrokeWidth(2f);

        hourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hourPaint.setStyle(Paint.Style.STROKE);
        hourPaint.setColor(0xFFFFFFFF);
        hourPaint.setStrokeWidth(6f);
        hourPaint.setStrokeCap(Paint.Cap.ROUND);

        minutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minutePaint.setStyle(Paint.Style.STROKE);
        minutePaint.setColor(0xFFFFFFFF);
        minutePaint.setStrokeWidth(4f);
        minutePaint.setStrokeCap(Paint.Cap.ROUND);

        secondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        secondPaint.setStyle(Paint.Style.STROKE);
        secondPaint.setColor(0xFF4488FF);
        secondPaint.setStrokeWidth(2f);
        secondPaint.setStrokeCap(Paint.Cap.ROUND);

        centrePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centrePaint.setStyle(Paint.Style.FILL);
        centrePaint.setColor(0xFFFFFFFF);
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(tickRunnable);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tickRunnable);
    }

    public void setTimeZone(String tz) {
        this.timeZone = TimeZone.getTimeZone(tz);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 4f;

        // Dial circle
        canvas.drawCircle(cx, cy, radius, dialPaint);

        // Hour tick marks
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90);
            float inner = radius * 0.85f;
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius,
                    dialPaint);
        }

        Calendar cal = Calendar.getInstance(timeZone);
        int hour = cal.get(Calendar.HOUR);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);

        // Hour hand
        float hourAngle = (float) Math.toRadians((hour * 30 + minute * 0.5f) - 90);
        float hourLen = radius * 0.55f;
        canvas.drawLine(cx, cy,
                cx + (float) Math.cos(hourAngle) * hourLen,
                cy + (float) Math.sin(hourAngle) * hourLen,
                hourPaint);

        // Minute hand
        float minAngle = (float) Math.toRadians((minute * 6 + second * 0.1f) - 90);
        float minLen = radius * 0.78f;
        canvas.drawLine(cx, cy,
                cx + (float) Math.cos(minAngle) * minLen,
                cy + (float) Math.sin(minAngle) * minLen,
                minutePaint);

        // Second hand
        float secAngle = (float) Math.toRadians(second * 6 - 90);
        float secLen = radius * 0.85f;
        canvas.drawLine(cx, cy,
                cx + (float) Math.cos(secAngle) * secLen,
                cy + (float) Math.sin(secAngle) * secLen,
                secondPaint);

        // Centre dot
        canvas.drawCircle(cx, cy, 5f, centrePaint);
    }
}
