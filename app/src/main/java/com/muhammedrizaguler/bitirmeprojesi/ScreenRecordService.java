package com.muhammedrizaguler.bitirmeprojesi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenRecordService extends Service {

    private static final String CHANNEL_ID = "ScreenRecordChannel";
    private static final int NOTIFICATION_ID = 1001;

    private MediaRecorder mediaRecorder;
    private MediaProjection mediaProjection;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private ExecutorService executorService;

    private String currentVideoPath;
    private boolean isRecording = false;
    private boolean isStopping = false;
    private boolean stopFailed = false;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        ScreenRecordService getService() {
            return ScreenRecordService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executorService = Executors.newSingleThreadExecutor();
        log("Service oluşturuldu");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_RECORDING".equals(action)) {
                int resultCode = intent.getIntExtra("resultCode", 0);
                Intent data = intent.getParcelableExtra("data");
                startRecording(resultCode, data);
            } else if ("STOP_RECORDING".equals(action)) {
                stopRecording();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ekran Kaydı",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Ekran kaydı devam ediyor");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, ScreenRecordService.class);
        stopIntent.setAction("STOP_RECORDING");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ekran Kaydı")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Durdur", stopPendingIntent)
                .build();
    }

    public void startRecording(int resultCode, Intent data) {
        if (isRecording || data == null) return;

        try {
            Notification notification = createNotification("Kayıt devam ediyor...");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            MediaProjectionManager projectionManager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                log("MediaProjection null, kayıt başlatılamadı!");
                stopSelf();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        if (!isStopping && isRecording) stopRecording();
                    }
                }, null);
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File videoFile = new File(getExternalFilesDir(null), "Recording_" + timeStamp + ".mp4");
            currentVideoPath = videoFile.getAbsolutePath();

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            int width = Math.min(metrics.widthPixels, 1280);
            int height = Math.min(metrics.heightPixels, 720);
            int density = metrics.densityDpi;

            mediaRecorder = new MediaRecorder();
            try {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            } catch (Exception e) {
                log("AudioSource ayarlanamadı: " + e.getMessage());
            }

            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(currentVideoPath);
            mediaRecorder.setVideoSize(width, height);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
            mediaRecorder.setVideoFrameRate(30);

            mediaRecorder.prepare();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, null
            );

            Thread.sleep(300); // bazı cihazlarda gerekli

            mediaRecorder.start();
            isRecording = true;
            isStopping = false;
            stopFailed = false;

            log("✅ Kayıt başladı!");
            sendBroadcast(new Intent("RECORDING_STARTED"));
        } catch (Exception e) {
            log("Kayıt başlatılamadı: " + e.getMessage());
            cleanupResources();
            stopSelf();
        }
    }

    public void stopRecording() {
        if (!isRecording || isStopping) return;

        isStopping = true;
        executorService.execute(() -> {
            try {
                Thread.sleep(500);

                if (mediaRecorder != null) {
                    try {
                        mediaRecorder.stop();
                        log("✅ MediaRecorder başarıyla durduruldu");
                    } catch (RuntimeException e) {
                        stopFailed = true;
                        log("MediaRecorder stop hatası: " + e.getMessage());
                    }
                }

                cleanupResources();
                Thread.sleep(300);

                File videoFile = new File(currentVideoPath);
                if (stopFailed || !videoFile.exists() || videoFile.length() < 10240) {
                    log("Video dosyası bozuk veya çok küçük, siliniyor.");
                    videoFile.delete();
                    sendBroadcast(new Intent("RECORDING_ERROR"));
                } else {
                    saveVideoToGallery();
                    sendBroadcast(new Intent("RECORDING_STOPPED"));
                }

            } catch (Exception e) {
                log("stopRecording hatası: " + e.getMessage());
            } finally {
                stopForeground(true);
                stopSelf();
                isRecording = false;
                isStopping = false;
            }
        });
    }

    private void cleanupResources() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            if (mediaRecorder != null) {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            log("cleanupResources hatası: " + e.getMessage());
        }
    }

    private void saveVideoToGallery() {
        try {
            File videoFile = new File(currentVideoPath);
            if (!videoFile.exists()) return;

            long size = videoFile.length();
            if (size < 10240) return;

            ContentValues values = new ContentValues();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "Recording_" + timeStamp + ".mp4";

            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecordings");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;

            try (FileInputStream input = new FileInputStream(videoFile);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            }

            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            videoFile.delete();

            log("✅ Video başarıyla galeriye kaydedildi!");
        } catch (Exception e) {
            log("saveVideoToGallery hatası: " + e.getMessage());
        }
    }

    public boolean isRecording() {
        return isRecording && !isStopping;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupResources();
        executorService.shutdown();
    }

    private void log(String msg) {
        android.util.Log.d("ScreenRecord", msg);
    }
}
