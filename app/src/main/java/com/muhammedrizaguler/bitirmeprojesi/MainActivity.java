package com.muhammedrizaguler.bitirmeprojesi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.BatteryState;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity implements DynamicMenuManager.ActivityCallback {

    private ImageButton soundButton;
    private ImageButton lightsButton;
    private ImageButton recordButton;
    private ImageButton screenShotButton;
    private ImageButton emergencyStopButton;
    private ImageButton powerButton;

    private FrameLayout dynamicMenuContainer;
    private DynamicMenuManager dynamicMenuManager;

    private ImageButton activeButton = null;

    private RTSPCameraActivity rtspFragment;

    private static final int VIDEO_REQUEST_CODE = 1000;
    private MediaProjectionManager projectionManager;

    private FrameLayout mainContent;

    private BatteryStateManager batteryStatusManager;

    // Lidar için değişkenler
    private UDPListenerThread udpListener;
    private TextView obstacleWarningText;

    // Ses yöneticisi
    private AudioManager audioManager;

    //Joystick yöneticisi
    private JoystickView joystickView;
    private JoystickDataSender joystickSender;
    private TextView joystickCoords;
    private TextView joystickStatus;

    // Raspberry Pi bağlantı ayarları — kendi değerlerinizle güncelleyin
    private static final String RASPBERRY_PI_IP   = "192.168.1.132"; // ← Değiştir
    private static final int    RASPBERRY_PI_PORT  = 5005;

    // BroadcastReceiver - video kayıt durumunu dinle
    private android.content.BroadcastReceiver recordingReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("RECORDING_STOPPED".equals(action)) {
                Toast.makeText(MainActivity.this, "Video galeriye kaydedildi!", Toast.LENGTH_LONG).show();
            } else if ("VIDEO_SAVED".equals(action)) {
                Toast.makeText(MainActivity.this, "Video başarıyla kaydedildi!", Toast.LENGTH_LONG).show();
            } else if ("RECORDING_ERROR".equals(action)) {
                String error = intent.getStringExtra("error");
                Toast.makeText(MainActivity.this, "Hata: " + error, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen setup
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_main);
        mainContent = findViewById(R.id.main_content);

        initializeButtons();
        initializeDynamicMenu();
        initializeJoystick();
        setupButtonListeners();
        setupMainLayoutClickListener();

        loadRTSPFragment();

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // Ses yöneticisini başlat
        audioManager = new AudioManager(this);

        dynamicMenuManager.setActivityCallback(this);

        connectRTSPToMenuManager();
        connectAudioToMenuManager();

        // Pil durumu
        TextView batteryStatusText = findViewById(R.id.battery_status_text);
        batteryStatusManager = new BatteryStateManager(this, batteryStatusText);
        batteryStatusManager.startMonitoring();

        // Lidar uyarı metni
        obstacleWarningText = findViewById(R.id.obstacle_warning_text);
        if (obstacleWarningText != null) {
            obstacleWarningText.setVisibility(View.GONE);
        }

        // UDP dinleyiciyi başlat (LIDAR İÇİN - PORT 5000 KULLANACAK)
        udpListener = new UDPListenerThread();
        udpListener.start();

        // Ses izni kontrolü
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 200);
        }
    }

    private void connectAudioToMenuManager() {
        if (dynamicMenuManager != null && audioManager != null) {
            dynamicMenuManager.setAudioManager(audioManager);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VIDEO_REQUEST_CODE) {
            if (dynamicMenuManager != null) {
                dynamicMenuManager.handleMediaProjectionResult(resultCode, data);
            }
        }
    }


    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
    }

    private void loadRTSPFragment() {
        rtspFragment = new RTSPCameraActivity();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content, rtspFragment)
                .commit();
    }

    private void connectRTSPToMenuManager() {
        getSupportFragmentManager().executePendingTransactions();

        if (dynamicMenuManager != null && rtspFragment != null) {
            dynamicMenuManager.setRTSPFragment(rtspFragment);
        }
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void initializeButtons() {
        soundButton = findViewById(R.id.sound_button);
        lightsButton = findViewById(R.id.lights_button);
        recordButton = findViewById(R.id.record_button);
        screenShotButton = findViewById(R.id.screenshot_button);
        powerButton = findViewById(R.id.power_button);
    }

    private void initializeDynamicMenu() {
        dynamicMenuContainer = findViewById(R.id.dynamic_menu_container);
        if (dynamicMenuContainer == null) {
            createDynamicMenuContainer();
        }

        View rootView = findViewById(android.R.id.content);
        dynamicMenuManager = new DynamicMenuManager(this, dynamicMenuContainer, rootView);

        if (dynamicMenuContainer != null) {
            dynamicMenuContainer.setVisibility(View.GONE);
        }
    }

    private void createDynamicMenuContainer() {
        ConstraintLayout mainLayout = findViewById(R.id.root_layout);
        if (mainLayout == null) return;

        dynamicMenuContainer = new FrameLayout(this);
        dynamicMenuContainer.setId(View.generateViewId());
        dynamicMenuContainer.setBackgroundColor(Color.TRANSPARENT);
        dynamicMenuContainer.setVisibility(View.GONE);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;

        float density = getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (50 * density);
        params.topMargin = (int) (116 * density);

        dynamicMenuContainer.setLayoutParams(params);
        mainLayout.addView(dynamicMenuContainer);

        System.out.println("Dynamic menu container created");
    }

    private void setupMainLayoutClickListener() {
        View mainLayout = findViewById(R.id.main_content);
        if (mainLayout != null) {
            mainLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetAllButtons();
                    hideDynamicMenu();
                }
            });
        }
    }

    private void resetAllButtons() {
        if (soundButton != null) soundButton.setColorFilter(Color.WHITE);
        if (lightsButton != null) lightsButton.setColorFilter(Color.WHITE);
        if (recordButton != null) recordButton.setColorFilter(Color.WHITE);
        if (screenShotButton != null) screenShotButton.setColorFilter(Color.WHITE);
        if (emergencyStopButton != null) emergencyStopButton.setColorFilter(Color.WHITE);
        if (powerButton != null) powerButton.setColorFilter(Color.WHITE);

        activeButton = null;
    }

    private void setupButtonListeners() {
        if (soundButton != null) {
            soundButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(soundButton, "sound");
                }
            });
        }

        if (lightsButton != null) {
            lightsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(lightsButton, "lights");
                }
            });
        }

        if (recordButton != null) {
            recordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(recordButton, "record");
                }
            });
        }

        if (screenShotButton != null) {
            screenShotButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(screenShotButton, "screenshot");
                }
            });
        }

        if (emergencyStopButton != null) {
            emergencyStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(emergencyStopButton, "emergency");
                }
            });
        }

        if (powerButton != null) {
            powerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleButton(powerButton, "power");
                }
            });
        }
    }

    private void toggleButton(ImageButton clickedButton, String buttonType) {
        if (activeButton == clickedButton) {
            clickedButton.setColorFilter(Color.WHITE);
            activeButton = null;
            hideDynamicMenu();
        } else {
            resetAllButtons();
            clickedButton.setColorFilter(Color.parseColor("#506B70"));
            activeButton = clickedButton;
            showDynamicMenu(buttonType, clickedButton);
        }
    }

    private void showDynamicMenu(String buttonType, ImageButton clickedButton) {
        if (dynamicMenuManager != null) {
            dynamicMenuManager.showMenu(buttonType, clickedButton);
        }
    }

    private void hideDynamicMenu() {
        if (dynamicMenuManager != null) {
            dynamicMenuManager.hideMenu();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dynamicMenuManager != null) {
            dynamicMenuManager.onDestroy();
        }

        if (batteryStatusManager != null) {
            batteryStatusManager.onDestroy();
        }

        if (udpListener != null) {
            udpListener.stopListening();
        }

        if (audioManager != null) {
            audioManager.cleanup();
        }

        if (joystickSender != null) {
            joystickSender.disconnect();
        }
    }

    /**
     * UDP Dinleyici Thread - LIDAR İÇİN
     * PORT 5000 KULLANIR (Ses ile çakışmayı önlemek için)
     */
    private class UDPListenerThread extends Thread {
        private volatile boolean running = true;
        private java.net.DatagramSocket socket;
        private static final int UDP_PORT = 5000; // SES İLE ÇAKIŞMASIN DİYE DEĞİŞTİRDİK!

        @Override
        public void run() {
            try {
                socket = new java.net.DatagramSocket(UDP_PORT);
                byte[] buffer = new byte[1024];

                System.out.println("[LIDAR] UDP Dinleyici başlatıldı - Port: " + UDP_PORT);

                while (running) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showObstacleWarning(message);
                        }
                    });
                }
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "UDP Dinleyici Hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }

        public void stopListening() {
            running = false;
            if (socket != null) {
                socket.close();
            }
            interrupt();
        }
    }

    private void showObstacleWarning(String message) {
        if (obstacleWarningText != null) {
            obstacleWarningText.setText(message + "ENGEL ALGILANDI!");
            obstacleWarningText.setVisibility(View.VISIBLE);

            obstacleWarningText.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (obstacleWarningText != null) {
                        obstacleWarningText.setVisibility(View.GONE);
                    }
                }
            }, 3000);
        }

        Toast.makeText(this, "UYARI: " + message, Toast.LENGTH_SHORT).show();
    }

    private void initializeJoystick() {
        joystickView   = findViewById(R.id.joystick_view);
        joystickCoords = findViewById(R.id.joystick_coords);
        joystickStatus = findViewById(R.id.joystick_status);

        // Göndericiyi oluştur ve bağlan
        joystickSender = new JoystickDataSender(RASPBERRY_PI_IP, RASPBERRY_PI_PORT);

        joystickSender.setStatusListener(status -> {
            if (joystickStatus != null) {
                joystickStatus.setText(status);
            }
        });

        joystickSender.connect();

        // Joystick hareketi dinleyicisi
        joystickView.setJoystickListener((x, y) -> {
            // Koordinat göstergesi güncelle
            if (joystickCoords != null) {
                joystickCoords.setText(
                        String.format(java.util.Locale.US, "X: %+.2f  Y: %+.2f", x, y));
            }

            // Raspberry Pi'ye gönder
            joystickSender.send(x, y);
        });
    }

}