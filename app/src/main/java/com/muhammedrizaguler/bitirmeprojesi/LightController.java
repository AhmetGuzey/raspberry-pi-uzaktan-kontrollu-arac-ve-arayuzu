package com.muhammedrizaguler.bitirmeprojesi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raspberry Pi 5'e TCP üzerinden ışık komutları gönderir.
 *
 * Protokol: Her komut tek satır JSON + '\n'
 *   {"light":"front","state":"on"}
 *   {"light":"selector","state":"on"}
 *   {"light":"front","state":"off"}
 *   {"light":"selector","state":"off"}
 *
 * Raspberry Pi: 192.168.1.132 : 8556
 */
public class LightController {

    private static final String TAG = "LightController";
    private static final String RASPBERRY_IP   = "192.168.1.132";
    private static final int    RASPBERRY_PORT  = 8556;
    private static final int    CONNECT_TIMEOUT = 3000; // ms

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Callback arayüzü — UI thread'den çağrılır
    public interface LightCommandCallback {
        void onSuccess(String light, String state);
        void onError(String light, String state, String errorMessage);
    }

    /**
     * Ön lamba komutunu gönderir.
     * @param on    true → "on", false → "off"
     * @param cb    sonuç callback (null olabilir)
     */
    public void sendFrontLight(boolean on, LightCommandCallback cb) {
        sendCommand("front", on ? "on" : "off", cb);
    }

    /**
     * Selektör komutunu gönderir.
     * @param on    true → "on" (1sn aralıklı yanıp sönme), false → "off"
     * @param cb    sonuç callback (null olabilir)
     */
    public void sendSelectorLight(boolean on, LightCommandCallback cb) {
        sendCommand("selector", on ? "on" : "off", cb);
    }

    // ================================================================
    // İç metodlar
    // ================================================================

    private void sendCommand(String light, String state, LightCommandCallback cb) {
        executor.execute(() -> {
            try {
                String json = buildJson(light, state);
                transmit(json);
                Log.d(TAG, "Gönderildi → " + json);
                if (cb != null) {
                    mainHandler.post(() -> cb.onSuccess(light, state));
                }
            } catch (Exception e) {
                Log.e(TAG, "Gönderim hatası: " + e.getMessage());
                if (cb != null) {
                    mainHandler.post(() -> cb.onError(light, state, e.getMessage()));
                }
            }
        });
    }

    private String buildJson(String light, String state) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("light", light);
        obj.put("state", state);
        return obj.toString();
    }

    private void transmit(String json) throws IOException {
        // Her komut için yeni bir bağlantı açılır (fire-and-forget)
        try (Socket socket = new Socket()) {
            socket.connect(
                    new java.net.InetSocketAddress(RASPBERRY_IP, RASPBERRY_PORT),
                    CONNECT_TIMEOUT
            );
            OutputStream out = socket.getOutputStream();
            // Satır sonu ekliyoruz — Raspberry tarafı readline() ile okur
            out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /** Activity destroy edildiğinde çağırın */
    public void shutdown() {
        executor.shutdownNow();
    }
}