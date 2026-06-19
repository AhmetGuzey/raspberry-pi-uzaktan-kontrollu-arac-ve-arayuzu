package com.muhammedrizaguler.bitirmeprojesi;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class MenuBackgroundView extends View {

    private Paint paint;
    private RectF rectF;
    private int menuHeight = 0;
    private String menuTitle = "";
    private float[] linePositions = new float[0];

    public MenuBackgroundView(Context context) {
        super(context);
        init();
    }

    public MenuBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rectF = new RectF();
    }

    public void setMenuTitle(String title) {
        this.menuTitle = title;
        invalidate(); // Yeniden çizim için
    }

    public void setMenuHeight(int height) {
        this.menuHeight = height;
        calculateLinePositions(); // Çizgi pozisyonlarını yeniden hesapla
        invalidate();
    }

    private void calculateLinePositions() {
        float density = getContext().getResources().getDisplayMetrics().density;
        float topY = 57 * density;
        int lineCount = Math.max(3, (int) ((menuHeight - topY) / (25 * density)));

        linePositions = new float[lineCount];
        for (int i = 0; i < lineCount; i++) {
            linePositions[i] = topY + i * 25 * density;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getContext().getResources().getDisplayMetrics().density;
        int width = getWidth();
        int height = getHeight();

        // Ana arka plan - dıştan içe doğru açılan gri gradient
        drawGradientBackground(canvas, width, height, density);

        // Başlık ve altındaki gösterge bölümü
        drawHeaderSection(canvas, width, density);
    }

    private void drawGradientBackground(Canvas canvas, int width, int height, float density) {
        // Dıştan içe doğru açılan gri gradient oluştur
        RadialGradient gradient = new RadialGradient(
                width / 2f,             // Merkez X
                height / 2f,                   // Merkez Y
                Math.max(width, height) / 2f,  // Radius
                new int[]{
                        Color.parseColor("#B1BD08"), // Dış kenar - daha açık gri
                        Color.parseColor("#8F9908"), // Orta ton
                        Color.parseColor("#585E04")  // İç kısım - daha koyu gri
                },
                new float[]{0f, 0.7f, 1f},     // Gradient pozisyonları
                Shader.TileMode.CLAMP
        );
        paint.setAlpha(200);
        paint.setShader(gradient);
        paint.setStyle(Paint.Style.FILL);

        // Köşe radiusu olmadan dikdörtgen çiz
        rectF.set(8 * density, 8 * density, width - 8 * density, height - 8 * density);
        canvas.drawRect(rectF, paint);

        // Shader'ı temizle
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void drawHeaderSection(Canvas canvas, int width, float density) {
        // Başlık metni (varsa)
        if (!menuTitle.isEmpty()) {
            paint.setColor(Color.parseColor("#506B70"));
            paint.setTextSize(19 * density);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            float textWidth = paint.measureText(menuTitle);
            float textX = (width - textWidth) / 2;
            float textY = 32 * density;
            canvas.drawText(menuTitle, textX, textY, paint);
        }
        // Altındaki renkli çizgiler ve bar
        drawColorStripesAndBar(canvas, width, density);
    }

    private void drawColorStripesAndBar(Canvas canvas, int width, float density) {
        float stripeWidth = 13 * density;
        float stripeHeight = 5 * density;
        float spacing = density - 5;
        float radius = 3 * density;

        float topY = 40 * density + 7 * density;
        float startX = 10 * density;

        int[] colors = {
                Color.parseColor("#506B70"), // Turuncu
                Color.WHITE,
                Color.RED
        };

        // Kaydet (canvas rotasyonundan önce)
        canvas.save();

        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);

            // Her bir şeridin X pozisyonu
            float x = startX + i * (stripeWidth + spacing);
            float y = topY;

            // Merkez etrafında döndürmek için önce konuma taşı, sonra rotate et
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(-45); // Yaklaşık çapraz görünüm için -20 derece eğim

            // Yuvarlak köşeli dikdörtgen çiz
            rectF.set(0, 0, stripeWidth, stripeHeight);
            canvas.drawRoundRect(rectF, radius, radius, paint);

            canvas.restore(); // Her çizim sonrası canvas'ı eski haline getir
        }

        canvas.restore(); // Tüm çizimlerin ardından ana canvas'ı geri al

        // Gri bar (şeritlerin sağında)
        float barX = startX + 3 * (stripeWidth + spacing + 2);
        float barTopY = topY - 12 * density;
        float barHeight = stripeHeight - 10;
        paint.setColor(Color.parseColor("#000000"));
        paint.setAlpha(100);
        rectF.set(barX, barTopY + 4, width - 10 * density, topY + barHeight + 6);
        canvas.drawRoundRect(rectF, 4 * density, 4 * density, paint);
        paint.setAlpha(255);
    }

}
