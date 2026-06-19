package com.muhammedrizaguler.bitirmeprojesi;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Python kodu ile TAM UYUMLU ses yöneticisi
 */
public class AudioManager {

    private Context context;
    private Handler uiHandler;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int FRAMES = 1024;
    private static final int BUFFER_SIZE_BYTES = FRAMES * 2;

    private static final String RASPBERRY_IP = "192.168.1.132";

    private static final int TABLET_RECEIVE_PORT = 8554;
    private static final int RASPBERRY_RECEIVE_PORT = 8555;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;

    private Thread sendThread;
    private Thread receiveThread;

    private volatile boolean isSending = false;
    private volatile boolean isReceiving = false;

    public AudioManager(Context context) {
        this.context = context;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * SOUND_TEST1: Raspberry mikrofon -> Tablet hoparlör
     */
    public void startReceiving() {
        if (isReceiving) {
            showToast("Ses alma zaten aktif");
            return;
        }

        try {
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);
            int playBufferSize = Math.max(minBufferSize, BUFFER_SIZE_BYTES * 4);

            audioTrack = new AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    playBufferSize,
                    AudioTrack.MODE_STREAM
            );

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                showError("Hoparlör başlatılamadı!");
                return;
            }

            receiveSocket = new DatagramSocket(TABLET_RECEIVE_PORT);
            receiveSocket.setReceiveBufferSize(BUFFER_SIZE_BYTES * 8);
            receiveSocket.setSoTimeout(100);

            isReceiving = true;

            receiveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                    audioTrack.play();
                    byte[] buffer = new byte[BUFFER_SIZE_BYTES * 2];
                    int packetsReceived = 0;
                    int timeouts = 0;
                    long lastLog = System.currentTimeMillis();

                    try {
                        while (isReceiving) {
                            try {
                                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                                receiveSocket.receive(packet);

                                if (packet.getLength() > 0) {
                                    audioTrack.write(packet.getData(), 0, packet.getLength());
                                    packetsReceived++;
                                }
                            } catch (java.net.SocketTimeoutException e) {
                                timeouts++;
                            }

                            if (System.currentTimeMillis() - lastLog > 5000) {
                                System.out.println("[RASP->TABLET] " + packetsReceived + " paket alindi <- Port " + TABLET_RECEIVE_PORT + " (timeout: " + timeouts + ")");
                                packetsReceived = 0;
                                timeouts = 0;
                                lastLog = System.currentTimeMillis();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (isReceiving) {
                            showError("Ses alma hatası: " + e.getMessage());
                        }
                    } finally {
                        if (audioTrack != null) {
                            try {
                                audioTrack.stop();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            receiveThread.start();
            showToast("Raspberry'den ses dinleniyor (Port " + TABLET_RECEIVE_PORT + ")");

        } catch (Exception e) {
            e.printStackTrace();
            isReceiving = false;
            showError("Hoparlör başlatılamadı: " + e.getMessage());
        }
    }

    /**
     * SOUND_TEST2: Tablet mikrofon -> Raspberry hoparlör
     */
    public void startSending() {
        if (isSending) {
            showToast("Ses gönderimi zaten aktif");
            return;
        }

        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
            int recordBufferSize = Math.max(minBufferSize, BUFFER_SIZE_BYTES * 4);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_IN,
                    AUDIO_FORMAT,
                    recordBufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                showError("Mikrofon başlatılamadı!");
                return;
            }

            sendSocket = new DatagramSocket();
            sendSocket.setSendBufferSize(BUFFER_SIZE_BYTES * 8);

            isSending = true;

            sendThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                    audioRecord.startRecording();
                    byte[] buffer = new byte[BUFFER_SIZE_BYTES];

                    try {
                        InetAddress address = InetAddress.getByName(RASPBERRY_IP);
                        int packetsSent = 0;
                        long lastLog = System.currentTimeMillis();

                        while (isSending) {
                            int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                            if (bytesRead > 0) {
                                DatagramPacket packet = new DatagramPacket(
                                        buffer, bytesRead, address, RASPBERRY_RECEIVE_PORT
                                );
                                sendSocket.send(packet);
                                packetsSent++;
                            }

                            if (System.currentTimeMillis() - lastLog > 5000) {
                                System.out.println("[TABLET->RASP] " + packetsSent + " paket gönderildi -> " + RASPBERRY_IP + ":" + RASPBERRY_RECEIVE_PORT);
                                packetsSent = 0;
                                lastLog = System.currentTimeMillis();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (isSending) {
                            showError("Ses gönderme hatası: " + e.getMessage());
                        }
                    } finally {
                        if (audioRecord != null) {
                            try {
                                audioRecord.stop();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            sendThread.start();
            showToast("Raspberry'e ses gönderiliyor (" + RASPBERRY_IP + ":" + RASPBERRY_RECEIVE_PORT + ")");

        } catch (Exception e) {
            e.printStackTrace();
            isSending = false;
            showError("Mikrofon başlatılamadı: " + e.getMessage());
        }
    }

    public void stopSending() {
        if (!isSending) return;

        isSending = false;

        if (sendThread != null) {
            sendThread.interrupt();
            try {
                sendThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            audioRecord = null;
        }

        if (sendSocket != null) {
            sendSocket.close();
            sendSocket = null;
        }

        showToast("Mikrofon kapatıldı");
    }

    public void stopReceiving() {
        if (!isReceiving) return;

        isReceiving = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            audioTrack = null;
        }

        if (receiveSocket != null) {
            receiveSocket.close();
            receiveSocket = null;
        }

        showToast("Hoparlör kapatıldı");
    }

    public void startBoth() {
        showToast("Çift yönlü ses şu an devre dışı");
    }

    public void stopAll() {
        stopSending();
        stopReceiving();
    }

    public boolean isSending() {
        return isSending;
    }

    public boolean isReceiving() {
        return isReceiving;
    }

    public void cleanup() {
        stopAll();
    }

    private void showToast(final String message) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showError(final String message) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "HATA: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }
}