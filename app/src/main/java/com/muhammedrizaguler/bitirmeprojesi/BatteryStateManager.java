package com.muhammedrizaguler.bitirmeprojesi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

public class BatteryStateManager {

    private Context context;
    private TextView batteryStatusText;
    private Handler updateHandler;
    private Runnable updateRunnable;

    private int tabletBatteryLevel = 100;

    private BroadcastReceiver batteryReceiver;
    private boolean isRegistered = false;

    public BatteryStateManager(Context context, TextView batteryStatusText) {
        this.context = context;
        this.batteryStatusText = batteryStatusText;
        this.updateHandler = new Handler(Looper.getMainLooper());

        initializeBatteryReceiver();
        startPeriodicUpdate();
    }

    /**
     * Tablet pil durumunu dinleyen BroadcastReceiver
     */
    private void initializeBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level != -1 && scale != -1) {
                    tabletBatteryLevel = (int) ((level / (float) scale) * 100);
                    updateBatteryDisplay();
                }
            }
        };
    }

    /**
     * Battery receiver'ı kaydet ve ilk pil durumunu al
     */
    public void startMonitoring() {
        if (!isRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(batteryReceiver, filter);
            isRegistered = true;

            // İlk değeri hemen al
            getInitialBatteryLevel();
        }
    }

    /**
     * İlk tablet pil seviyesini al
     */
    private void getInitialBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level != -1 && scale != -1) {
                tabletBatteryLevel = (int) ((level / (float) scale) * 100);
            }
        }

        updateBatteryDisplay();
    }

    /**
     * 3 saniyede bir güncelleme başlat
     */
    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {

                updateBatteryDisplay();

                // 3 saniye sonra tekrar çalıştır
                updateHandler.postDelayed(this, 3000);
            }
        };

        // İlk güncellemeyi hemen yap
        updateHandler.post(updateRunnable);
    }


    /**
     * Ekranda pil durumunu güncelle
     */
    private void updateBatteryDisplay() {
        if (batteryStatusText != null) {
            String displayText = String.format("TABLET ŞARJ DURUMU: %%%d", tabletBatteryLevel);

            batteryStatusText.setText(displayText);
        }
    }


    /**
     * Anlık tablet pil seviyesini al
     */
    public int getTabletBatteryLevel() {
        return tabletBatteryLevel;
    }


    /**
     * Güncellemeyi durdur ve kaynakları temizle
     */
    public void stopMonitoring() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        if (isRegistered && batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
                isRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver zaten unregister edilmiş
            }
        }
    }

    /**
     * Activity destroy edildiğinde çağrılmalı
     */
    public void onDestroy() {
        stopMonitoring();
    }
}