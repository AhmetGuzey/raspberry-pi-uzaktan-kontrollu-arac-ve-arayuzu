package com.muhammedrizaguler.bitirmeprojesi;

import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.view.animation.DecelerateInterpolator;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MenuActionHandler {

    private Context context;
    private View rootView;
    private FrameLayout menuContainer;
    private boolean isMenuVisible;

    private RTSPCameraActivity rtspFragment;

    // Seri çekim değişkenleri
    private android.os.Handler burstHandler;
    private Runnable burstRunnable;
    private boolean isBurstMode = false;
    private int burstCount = 0;
    private static final int MAX_BURST_SHOTS = 10;

    // Video kayıt değişkenleri
    private boolean isRecording = false;
    private static final int VIDEO_REQUEST_CODE = 1000;

    // Ses yöneticisi referansı
    private AudioManager audioManager;
    private LightController lightController;

    // Callback interface
    public interface MenuActionCallback {
        void onMenuVisibilityChange(boolean isVisible);
        boolean isMenuCurrentlyVisible();
        void hideMenu();
        void requestMediaProjection();
    }

    private MenuActionCallback callback;

    public MenuActionHandler(Context context, View rootView, FrameLayout menuContainer, MenuActionCallback callback) {
        this.context = context;
        this.rootView = rootView;
        this.menuContainer = menuContainer;
        this.callback = callback;
        this.burstHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        this.lightController = new LightController();
    }

    /**
     * AudioManager referansını ayarla
     */
    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * RTSP Fragment referansını ayarla
     */
    public void setRTSPFragment(RTSPCameraActivity fragment) {
        this.rtspFragment = fragment;
    }

    // ==================== BUTON İŞLEMLERİ ====================

    public void handleButtonClick(String itemId, String menuType, int sliderValue) {
        switch (menuType) {
            case "sound":
                handleSoundButton(itemId);
                break;
            case "record":
                handleRecordButton(itemId);
                break;
            case "screenshot":
                handleScreenshotButton(itemId, sliderValue);
                break;
            case "power":
                handlePowerButton(itemId);
                break;
        }
    }

    private void handleSoundButton(String buttonId) {
        if (audioManager == null) {
            Toast.makeText(context, "❌ AudioManager bulunamadı!", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (buttonId) {
            case "sound_test1":
                // Raspberry'den ses al (Raspberry mikrofon → Tablet hoparlör)
                if (audioManager.isReceiving()) {
                    audioManager.stopReceiving();
                    Toast.makeText(context, "🔇 Raspberry'den ses alma durduruldu", Toast.LENGTH_SHORT).show();
                } else {
                    audioManager.startReceiving();
                    Toast.makeText(context, "🔊 Raspberry'den ses alınıyor...", Toast.LENGTH_SHORT).show();
                }
                break;

            case "sound_test2":
                // Raspberry'e ses gönder (Tablet mikrofon → Raspberry hoparlör)
                if (audioManager.isSending()) {
                    audioManager.stopSending();
                    Toast.makeText(context, "🔇 Raspberry'e ses gönderme durduruldu", Toast.LENGTH_SHORT).show();
                } else {
                    audioManager.startSending();
                    Toast.makeText(context, "🎤 Tablet'ten Raspberry'e ses gönderiliyor...", Toast.LENGTH_SHORT).show();
                }
                break;

            case "sound_test3":
                // Şu an devre dışı
                Toast.makeText(context, "⚠ Bu özellik şu an devre dışı", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void handleRecordButton(String buttonId) {
        switch (buttonId) {
            case "record_start":
                if (!isRecording) {
                    if (callback != null) {
                        callback.requestMediaProjection();
                    }
                } else {
                    Toast.makeText(context, "⚠ Kayıt zaten devam ediyor", Toast.LENGTH_SHORT).show();
                }
                break;
            case "record_stop":
                if (isRecording) {
                    stopRecording();
                } else {
                    Intent stopIntent = new Intent(context, ScreenRecordService.class);
                    stopIntent.setAction("STOP_RECORDING");
                    context.startService(stopIntent);
                    Toast.makeText(context, "⏹ Kayıt durduruluyor (istek gönderildi)", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void handleScreenshotButton(String buttonId, int intervalSeconds) {
        switch (buttonId) {
            case "screenshot_take":
                takeScreenshotWithEffect();
                break;
            case "screenshot_burst_start":
                startBurstMode(intervalSeconds);
                break;
        }
    }

    private void handleEmergencyButton(String buttonId) {
        switch (buttonId) {
            case "emergency_stop":
                stopBurstMode();

                // Ses işlemlerini durdur
                if (audioManager != null) {
                    audioManager.stopAll();
                }

                if (callback != null) {
                    callback.hideMenu();
                }
                Toast.makeText(context, "🛑 Acil Durdurma! Tüm işlemler durduruldu", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void handlePowerButton(String buttonId) {
        switch (buttonId) {
            case "power_shutdown":
                shutdownApplication();
                break;
            case "power_restart":
                restartApplication();
                break;
        }
    }

    // ==================== SLIDER İŞLEMLERİ ====================

    public void handleSliderChange(String sliderId, int value) {
        switch (sliderId) {
            case "screenshot_interval":
                Toast.makeText(context, "⏱ Aralık: " + value + " saniye", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ==================== SWITCH İŞLEMLERİ ====================

    public void handleSwitchChange(String switchId, boolean isChecked) {
        switch (switchId) {

            case "lights_front":
                lightController.sendFrontLight(isChecked, new LightController.LightCommandCallback() {
                    @Override
                    public void onSuccess(String light, String state) {
                        String msg = isChecked ? "Ön Lamba Açık ✓" : "Ön Lamba Kapalı ✓";
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String light, String state, String errorMessage) {
                        Toast.makeText(context,
                                "⚠ Ön lamba komutu gönderilemedi: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            case "lights_back":
                lightController.sendSelectorLight(isChecked, new LightController.LightCommandCallback() {
                    @Override
                    public void onSuccess(String light, String state) {
                        String msg = isChecked ? "Selektör Açık ✓" : "Selektör Kapalı ✓";
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String light, String state, String errorMessage) {
                        Toast.makeText(context,
                                "⚠ Selektör komutu gönderilemedi: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                break;
        }
    }

    // ==================== Uygulamayı kapatır ====================

    private void shutdownApplication() {
        Toast.makeText(context, "⚡ Uygulama Kapatılıyor", Toast.LENGTH_SHORT).show();
        onDestroy();

        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            activity.finishAffinity();
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    // ==================== Uygulamayı yeniden başlatır ====================

    private void restartApplication() {
        Toast.makeText(context, "🔄 Uygulama Yeniden Başlatılıyor", Toast.LENGTH_SHORT).show();
        onDestroy();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                Intent intent = new Intent(context, activity.getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
                activity.finish();
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 1000);
    }

    // ==================== VİDEO KAYIT FONKSİYONLARI ====================

    public void handleMediaProjectionResult(int resultCode, Intent data) {
        if (resultCode == android.app.Activity.RESULT_OK) {
            startRecordingService(resultCode, data);
            isRecording = true;
        } else {
            Toast.makeText(context, "❌ Ekran kayıt izni reddedildi", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecordingService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(context, ScreenRecordService.class);
        serviceIntent.setAction("START_RECORDING");
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        isRecording = true;
        Toast.makeText(context, "🔴 Video kaydı başladı", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        Intent serviceIntent = new Intent(context, ScreenRecordService.class);
        serviceIntent.setAction("STOP_RECORDING");
        context.startService(serviceIntent);

        isRecording = false;
        Toast.makeText(context, "⏹ Kayıt durduruldu, galeriye kaydediliyor...", Toast.LENGTH_SHORT).show();
    }

    // ==================== EKRAN GÖRÜNTÜSÜ FONKSİYONLARI ====================

    private void takeScreenshotWithEffect() {
        boolean wasVisible = callback != null && callback.isMenuCurrentlyVisible();
        if (wasVisible && menuContainer != null) {
            menuContainer.setVisibility(View.GONE);
        }

        burstHandler.postDelayed(() -> {
            captureScreenDirectly(wasVisible);
        }, 100);
    }

    private void captureScreenDirectly(boolean restoreMenu) {
        if (!(context instanceof android.app.Activity)) {
            Toast.makeText(context, "✗ Activity bulunamadı!", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.Activity activity = (android.app.Activity) context;
        Window window = activity.getWindow();

        Bitmap bitmap = Bitmap.createBitmap(
                rootView.getWidth(),
                rootView.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.PixelCopy.request(
                    window,
                    bitmap,
                    (copyResult) -> {
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            saveScreenshotToGallery(bitmap);
                            showScreenshotFlashEffect();
                            Toast.makeText(context, "✓ Ekran görüntüsü kaydedildi!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "✗ Ekran görüntüsü alınamadı (kod: " + copyResult + ")", Toast.LENGTH_SHORT).show();
                        }

                        if (restoreMenu && menuContainer != null) {
                            menuContainer.setVisibility(View.VISIBLE);
                        }
                    },
                    new Handler(Looper.getMainLooper())
            );
        } else {
            try {
                Canvas canvas = new Canvas(bitmap);
                rootView.draw(canvas);

                saveScreenshotToGallery(bitmap);
                showScreenshotFlashEffect();
                Toast.makeText(context, "✓ Ekran görüntüsü kaydedildi!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(context, "✗ Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            if (restoreMenu && menuContainer != null) {
                menuContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    // ==================== SERİ ÇEKİM FONKSİYONLARI ====================

    private void startBurstMode(int intervalSeconds) {
        if (isBurstMode) {
            stopBurstMode();
            Toast.makeText(context, "🛑 Seri çekim durduruldu", Toast.LENGTH_SHORT).show();
            return;
        }

        if (intervalSeconds < 1) {
            intervalSeconds = 3;
        }

        final int intervalMillis = intervalSeconds * 1000;
        isBurstMode = true;
        burstCount = 0;

        if (callback != null && callback.isMenuCurrentlyVisible() && menuContainer != null) {
            menuContainer.setVisibility(View.GONE);
        }

        Toast.makeText(context, "📸 Seri çekim başlatıldı (" + intervalSeconds + "sn aralıkla)",
                Toast.LENGTH_LONG).show();

        takeBurstScreenshot();

        burstRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBurstMode && burstCount < MAX_BURST_SHOTS) {
                    takeBurstScreenshot();
                    burstHandler.postDelayed(this, intervalMillis);
                } else if (burstCount >= MAX_BURST_SHOTS) {
                    stopBurstMode();
                    Toast.makeText(context, "✓ Seri çekim tamamlandı (" + burstCount + " görüntü)",
                            Toast.LENGTH_LONG).show();
                }
            }
        };

        burstHandler.postDelayed(burstRunnable, intervalMillis);
    }

    public void stopBurstMode() {
        if (burstRunnable != null) {
            burstHandler.removeCallbacks(burstRunnable);
            burstRunnable = null;
        }
        isBurstMode = false;

        if (menuContainer != null) {
            menuContainer.setVisibility(View.VISIBLE);
        }
    }

    private void takeBurstScreenshot() {
        if (!(context instanceof android.app.Activity)) {
            stopBurstMode();
            return;
        }

        android.app.Activity activity = (android.app.Activity) context;
        Window window = activity.getWindow();

        Bitmap bitmap = Bitmap.createBitmap(
                rootView.getWidth(),
                rootView.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.PixelCopy.request(
                    window,
                    bitmap,
                    (copyResult) -> {
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            burstCount++;
                            saveScreenshotToGallery(bitmap);
                            showBurstFlashEffect();

                            if (isBurstMode) {
                                Toast.makeText(context, "📸 " + burstCount + "/" + MAX_BURST_SHOTS,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    },
                    new Handler(Looper.getMainLooper())
            );
        } else {
            try {
                Canvas canvas = new Canvas(bitmap);
                rootView.draw(canvas);

                burstCount++;
                saveScreenshotToGallery(bitmap);
                showBurstFlashEffect();

                if (isBurstMode) {
                    Toast.makeText(context, "📸 " + burstCount + "/" + MAX_BURST_SHOTS,
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== GÖRSEL EFEKTLER ====================

    private void showScreenshotFlashEffect() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            activity.runOnUiThread(() -> {
                View flashView = new View(context);
                flashView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                flashView.setBackgroundColor(Color.WHITE);
                flashView.setAlpha(0f);

                ViewGroup rootViewGroup = (ViewGroup) rootView;
                rootViewGroup.addView(flashView);

                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(flashView, "alpha", 0f, 0.8f);
                fadeIn.setDuration(100);
                fadeIn.setInterpolator(new DecelerateInterpolator());

                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(flashView, "alpha", 0.8f, 0f);
                fadeOut.setDuration(200);
                fadeOut.setInterpolator(new DecelerateInterpolator());
                fadeOut.setStartDelay(100);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playSequentially(fadeIn, fadeOut);

                animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        rootViewGroup.removeView(flashView);
                    }
                });

                animatorSet.start();
                playShutterSound();
            });
        }
    }

    private void showBurstFlashEffect() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            activity.runOnUiThread(() -> {
                View flashView = new View(context);
                flashView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                flashView.setBackgroundColor(Color.WHITE);
                flashView.setAlpha(0f);

                ViewGroup rootViewGroup = (ViewGroup) rootView;
                rootViewGroup.addView(flashView);

                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(flashView, "alpha", 0f, 0.4f);
                fadeIn.setDuration(50);

                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(flashView, "alpha", 0.4f, 0f);
                fadeOut.setDuration(100);
                fadeOut.setStartDelay(50);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playSequentially(fadeIn, fadeOut);

                animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        rootViewGroup.removeView(flashView);
                    }
                });

                animatorSet.start();
                playShutterSound();
            });
        }
    }

    private void playShutterSound() {
        try {
            android.media.MediaActionSound sound = new android.media.MediaActionSound();
            sound.play(android.media.MediaActionSound.SHUTTER_CLICK);
        } catch (Exception e) {
            // Ses çalınamazsa sessizce devam et
        }
    }

    // ==================== YARDIMCI FONKSİYONLAR ====================

    private void saveScreenshotToGallery(Bitmap bitmap) {
        OutputStream fos;
        ContentValues contentValues = new ContentValues();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Screenshot_" + timeStamp + ".jpg";

        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots");
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        try {
            Uri imageUri = context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
            );

            if (imageUri != null) {
                fos = context.getContentResolver().openOutputStream(imageUri);
                if (fos != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    fos.flush();
                    fos.close();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear();
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    context.getContentResolver().update(imageUri, contentValues, null, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Kaydetme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public boolean isBurstModeActive() {
        return isBurstMode;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void onDestroy() {
        stopBurstMode();
        if (burstHandler != null) {
            burstHandler.removeCallbacksAndMessages(null);
        }
        if (isRecording) {
            stopRecording();
        }

        if (audioManager != null) {
            audioManager.stopAll();
        }
        if (lightController != null) {
            lightController.shutdown();
        }
    }
}