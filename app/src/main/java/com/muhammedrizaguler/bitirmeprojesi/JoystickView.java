package com.muhammedrizaguler.bitirmeprojesi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * JoystickView
 *
 * Ekranın sağ alt köşesine yerleştirilen özel joystick bileşeni.
 * - Dış halka: sabit referans çemberi
 * - İç top: parmakla sürüklenen joystick başlığı
 * - Parmak kaldırıldığında merkeze döner (spring-back)
 * - JoystickListener üzerinden x/y değerlerini [-1.0, 1.0] aralığında bildirir
 */
public class JoystickView extends View {

    // ==================== ARABİRİM ====================

    public interface JoystickListener {
        /**
         * @param x  Yatay eksen: -1.0 (tam sol) … 0.0 (merkez) … +1.0 (tam sağ)
         * @param y  Dikey eksen:  -1.0 (tam yukarı) … 0.0 (merkez) … +1.0 (tam aşağı)
         */
        void onJoystickMoved(float x, float y);
    }

    // ==================== SABİTLER ====================

    /** Joystick topunun dış halkaya oranı (0-1) */
    private static final float THUMB_RADIUS_RATIO = 0.20f;

    /** Merkezden sapmanın yumuşatma katsayısı (1.0 = yumuşatma yok) */
    private static final float SMOOTHING = 0.18f;

    // ==================== DEĞİŞKENLER ====================

    private Paint outerRingPaint;
    private Paint outerRingBorderPaint;
    private Paint innerRingPaint;
    private Paint thumbPaint;
    private Paint thumbHighlightPaint;
    private Paint crosshairPaint;
    private Paint directionPaint;

    private float centerX, centerY;
    private float outerRadius;
    private float thumbRadius;

    /** Joystick topunun mevcut ekran koordinatları */
    private float thumbX, thumbY;

    /** Normalize edilmiş çıkış değerleri [-1, 1] */
    private float normX = 0f, normY = 0f;

    private boolean isTouching = false;

    private JoystickListener listener;

    // ==================== CONSTRUCTOR'LAR ====================

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // ==================== BAŞLATMA ====================

    private void init() {
        // Dış halka dolgusu
        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setStyle(Paint.Style.FILL);
        outerRingPaint.setColor(Color.parseColor("#1A2B2E"));
        outerRingPaint.setAlpha(210);

        // Dış halka kenarlığı
        outerRingBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingBorderPaint.setStyle(Paint.Style.STROKE);
        outerRingBorderPaint.setColor(Color.parseColor("#506B70"));
        outerRingBorderPaint.setStrokeWidth(3f);

        // İç referans halkası
        innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerRingPaint.setStyle(Paint.Style.STROKE);
        innerRingPaint.setColor(Color.parseColor("#506B70"));
        innerRingPaint.setAlpha(80);
        innerRingPaint.setStrokeWidth(1.5f);

        // Joystick topu
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.parseColor("#506B70"));

        // Joystick topu parlaması
        thumbHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbHighlightPaint.setStyle(Paint.Style.FILL);
        thumbHighlightPaint.setColor(Color.parseColor("#7FA8AE"));
        thumbHighlightPaint.setAlpha(160);

        // Artı işareti (crosshair)
        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setColor(Color.parseColor("#506B70"));
        crosshairPaint.setAlpha(60);
        crosshairPaint.setStrokeWidth(1f);

        // Yön göstergesi oku
        directionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        directionPaint.setStyle(Paint.Style.FILL);
        directionPaint.setColor(Color.parseColor("#FFFFFF"));
        directionPaint.setAlpha(180);
    }

    // ==================== BOYUT HESAPLAMA ====================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        outerRadius = Math.min(w, h) / 2f - 8f;
        thumbRadius = outerRadius * THUMB_RADIUS_RATIO;
        thumbX = centerX;
        thumbY = centerY;

        // Radial gradient shader'ı boyut belirlendikten sonra oluştur
        RadialGradient gradient = new RadialGradient(
                0, -thumbRadius * 0.3f,
                thumbRadius * 1.2f,
                new int[]{Color.parseColor("#7FA8AE"), Color.parseColor("#506B70"), Color.parseColor("#2E4A50")},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        thumbPaint.setShader(gradient);
    }

    // ==================== ÇİZİM ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Dış halka arka plan
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint);
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingBorderPaint);

        // 2. İç referans halkası (%50 çap)
        canvas.drawCircle(centerX, centerY, outerRadius * 0.5f, innerRingPaint);

        // 3. Crosshair çizgileri
        canvas.drawLine(centerX, centerY - outerRadius + 10, centerX, centerY + outerRadius - 10, crosshairPaint);
        canvas.drawLine(centerX - outerRadius + 10, centerY, centerX + outerRadius - 10, centerY, crosshairPaint);

        // 4. Yön göstergesi: merkeze doğru ok gibi çizgi
        if (isTouching && (Math.abs(normX) > 0.05f || Math.abs(normY) > 0.05f)) {
            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setColor(Color.parseColor("#506B70"));
            linePaint.setAlpha(100);
            linePaint.setStrokeWidth(2f);
            canvas.drawLine(centerX, centerY, thumbX, thumbY, linePaint);
        }

        // 5. Joystick topu
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);

        // 6. Parlama efekti (üst sol)
        canvas.drawCircle(
                thumbX - thumbRadius * 0.25f,
                thumbY - thumbRadius * 0.25f,
                thumbRadius * 0.4f,
                thumbHighlightPaint
        );

        // 7. Joystick topu kenar çizgisi
        Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setColor(Color.parseColor("#8FBFC6"));
        thumbBorderPaint.setStrokeWidth(1.5f);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbBorderPaint);
    }

    // ==================== DOKUNMA İŞLEME ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                isTouching = true;
                handleTouch(touchX, touchY);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                springBack();
                break;
        }
        return true;
    }

    private void handleTouch(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        float maxOffset = outerRadius - thumbRadius;

        if (distance <= maxOffset) {
            thumbX = touchX;
            thumbY = touchY;
        } else {
            // Daire sınırına kilitle
            float angle = (float) Math.atan2(dy, dx);
            thumbX = centerX + maxOffset * (float) Math.cos(angle);
            thumbY = centerY + maxOffset * (float) Math.sin(angle);
        }

        // Normalize et [-1, 1]
        normX = (thumbX - centerX) / maxOffset;
        normY = (thumbY - centerY) / maxOffset;

        // Ölü bölge (dead zone) — küçük titreşimleri filtrele
        if (Math.abs(normX) < 0.04f) normX = 0f;
        if (Math.abs(normY) < 0.04f) normY = 0f;

        if (listener != null) {
            listener.onJoystickMoved(normX, normY);
        }

        invalidate();
    }

    /** Parmak kaldırıldığında joystick'i merkeze döndür */
    private void springBack() {
        thumbX = centerX;
        thumbY = centerY;
        normX = 0f;
        normY = 0f;

        if (listener != null) {
            listener.onJoystickMoved(0f, 0f);
        }
        invalidate();
    }

    // ==================== YARDIMCI METODLAR ====================

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    /** Anlık normalize X değeri (-1.0 sol … +1.0 sağ) */
    public float getNormX() { return normX; }

    /** Anlık normalize Y değeri (-1.0 yukarı … +1.0 aşağı) */
    public float getNormY() { return normY; }
}