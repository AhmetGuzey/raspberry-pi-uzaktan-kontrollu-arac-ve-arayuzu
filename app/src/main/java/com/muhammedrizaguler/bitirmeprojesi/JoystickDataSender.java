package com.muhammedrizaguler.bitirmeprojesi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JoystickDataSender
 *
 * Joystick verilerini UDP ile Raspberry Pi 5'e gönderir.
 *
 * Hedef : 192.168.1.132:5005
 *
 * PAKET FORMATI (JSON):
 *   {"y": -0.75, "x": 0.30, "ts": 1712345678901}
 *
 *   y  : -1.0 (ileri tam güç) … 0.0 (dur) … +1.0 (geri tam güç)
 *   x  : -1.0 (tam sola) … 0.0 (düz) … +1.0 (tam sağa)
 *   ts : Unix timestamp ms
 */
public class JoystickDataSender {

    private static final String TAG = "JoystickDataSender";

    public static final String DEFAULT_HOST = "192.168.1.132";
    public static final int    DEFAULT_PORT = 5005;

    private static final long  SEND_INTERVAL_MS = 50L;
    private static final float CHANGE_THRESHOLD = 0.01f;

    public interface StatusListener {
        void onStatusChanged(String status);
    }

    private final String host;
    private final int    port;

    private InetAddress    targetAddress;
    private DatagramSocket socket;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final AtomicBoolean   connected   = new AtomicBoolean(false);
    private final AtomicBoolean   destroyed   = new AtomicBoolean(false);
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private StatusListener statusListener;

    private float lastY        = Float.NaN;
    private float lastX        = Float.NaN;
    private long  lastSendTime = 0L;
    private int   packetCount  = 0;

    public JoystickDataSender() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public JoystickDataSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Bağlantı ─────────────────────────────────────────────────────────────

    public void connect() {
        if (destroyed.get()) return;
        executor.execute(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                targetAddress = InetAddress.getByName(host);
                socket = new DatagramSocket();
                connected.set(true);
                packetCount = 0;
                notifyStatus("Baglandi: " + host + ":" + port);
                Log.i(TAG, "UDP socket acildi. Hedef: " + host + ":" + port);
            } catch (Exception e) {
                connected.set(false);
                notifyStatus("Baglanti hatasi: " + e.getMessage());
                Log.e(TAG, "connect() hatasi", e);
            }
        });
    }

    /**
     * Bağlantıyı keser ama executor'u kapatmaz.
     * Tekrar connect() çağrılabilir.
     */
    public void disconnect() {
        sendStopCommand();
        connected.set(false);
        executor.execute(() -> {
            try {
                if (socket != null && !socket.isClosed()) socket.close();
                notifyStatus("Baglanti kesildi.");
                Log.i(TAG, "UDP socket kapatildi. Toplam paket: " + packetCount);
            } catch (Exception e) {
                Log.e(TAG, "disconnect() hatasi", e);
            }
        });
    }

    /**
     * Nesneyi tamamen yok eder. Activity onDestroy()'da çağırın.
     */
    public void destroy() {
        destroyed.set(true);
        disconnect();
        executor.shutdown();
    }

    // ── Veri gönderme ─────────────────────────────────────────────────────────

    /**
     * @param x  -1.0 (tam sola) … 0.0 (düz) … +1.0 (tam sağa)  → servo açısı
     * @param y  -1.0 (ileri) … 0.0 (dur) … +1.0 (geri)           → motor hızı
     */
    public void send(float x, float y) {
        if (!connected.get() || destroyed.get()) return;

        long now = System.currentTimeMillis();

        x = Math.max(-1.0f, Math.min(1.0f, x));
        y = Math.max(-1.0f, Math.min(1.0f, y));

        boolean timeOk       = (now - lastSendTime) >= SEND_INTERVAL_MS;
        boolean valueChanged = Float.isNaN(lastY) || Float.isNaN(lastX)
                || Math.abs(y - lastY) > CHANGE_THRESHOLD
                || Math.abs(x - lastX) > CHANGE_THRESHOLD;
        boolean isStop       = (Math.abs(y) < CHANGE_THRESHOLD && Math.abs(x) < CHANGE_THRESHOLD);

        if (!isStop && !timeOk && !valueChanged) return;

        lastY        = y;
        lastX        = x;
        lastSendTime = now;

        final float fy = y;
        final float fx = x;
        final long  ts = now;

        executor.execute(() -> {
            try {
                if (socket == null || socket.isClosed()) return;
                byte[] data = buildPayload(fx, fy, ts).getBytes("UTF-8");
                socket.send(new DatagramPacket(data, data.length, targetAddress, port));
                packetCount++;
                if (packetCount % 100 == 0)
                    Log.d(TAG, "Paket #" + packetCount + " | y=" + fy + " | x=" + fx);
            } catch (Exception e) {
                Log.e(TAG, "send() hatasi: " + e.getMessage());
                connected.set(false);
                notifyStatus("Baglanti koptu. Yeniden baglaniliyor...");
                reconnect();
            }
        });
    }

    public void sendStopCommand() {
        if (socket == null || socket.isClosed() || destroyed.get()) return;
        executor.execute(() -> {
            try {
                if (socket == null || socket.isClosed()) return;
                // Dur komutunda x=0 (servo ortada), y=0 (motor dur)
                byte[] data = buildPayload(0.0f, 0.0f, System.currentTimeMillis()).getBytes("UTF-8");
                socket.send(new DatagramPacket(data, data.length, targetAddress, port));
                Log.i(TAG, "Dur komutu gonderildi.");
            } catch (Exception e) {
                Log.e(TAG, "sendStopCommand() hatasi: " + e.getMessage());
            }
        });
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    /**
     * JSON paketi oluşturur.
     * x : -1.0 (sola) … 0.0 (düz) … +1.0 (sağa)
     * y : -1.0 (ileri) … 0.0 (dur) … +1.0 (geri)
     */
    private String buildPayload(float x, float y, long ts) {
        return String.format(java.util.Locale.US,
                "{\"y\":%.3f,\"x\":%.3f,\"ts\":%d}", y, x, ts);
    }

    private void reconnect() {
        if (destroyed.get()) return;
        mainHandler.postDelayed(() -> {
            if (!connected.get() && !destroyed.get()) {
                notifyStatus("Yeniden baglaniliyor...");
                connect();
            }
        }, 3000);
    }

    private void notifyStatus(String status) {
        if (statusListener != null)
            mainHandler.post(() -> statusListener.onStatusChanged(status));
    }

    public void setStatusListener(StatusListener l) { this.statusListener = l; }
    public boolean isConnected()    { return connected.get(); }
    public int     getPacketCount() { return packetCount; }
    public String  getHost()        { return host; }
    public int     getPort()        { return port; }
}