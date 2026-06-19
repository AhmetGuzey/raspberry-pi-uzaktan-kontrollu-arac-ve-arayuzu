package com.muhammedrizaguler.bitirmeprojesi;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class RTSPCameraActivity extends Fragment {

    private ExoPlayer player;
    private PlayerView playerView;
    private static final String RTSP_URL = "rtsp://192.168.1.132:8554/camera";
    private Handler mainHandler;

    // Callback interface for bitmap capture
    public interface BitmapCaptureCallback {
        void onBitmapCaptured(Bitmap bitmap);
        void onCaptureFailed();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_rtsp_camera, container, false);
        playerView = view.findViewById(R.id.playerView);
        mainHandler = new Handler(Looper.getMainLooper());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializePlayer();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    Toast.makeText(getContext(), "Bağlanıyor...", Toast.LENGTH_SHORT).show();
                } else if (state == Player.STATE_READY) {
                    Toast.makeText(getContext(), "Bağlantı başarılı!", Toast.LENGTH_SHORT).show();
                } else if (state == Player.STATE_ENDED) {
                    Toast.makeText(getContext(), "Yayın sona erdi.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(getContext(),
                        "Hata: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        connectToStream();
    }

    private void connectToStream() {
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(RTSP_URL)
                .setMimeType("application/x-rtsp")
                .build();

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    /**
     * RTSP akışından anlık görüntü yakalama (ASENKRON - RENDER BEKLEMELI)
     * Bu metod birden fazla frame render edilene kadar bekler
     */
    public void captureCurrentFrame(BitmapCaptureCallback callback) {
        if (playerView == null || callback == null) {
            if (callback != null) callback.onCaptureFailed();
            return;
        }

        // TextureView'ı bul
        android.view.TextureView textureView = findTextureView(playerView);

        if (textureView == null || !textureView.isAvailable()) {
            callback.onCaptureFailed();
            return;
        }

        // ÇÖZÜM: Birkaç frame bekle, sonra bitmap al
        // TextureView render döngüsünü beklemek için delay kullanıyoruz
        mainHandler.postDelayed(() -> {
            try {
                // İkinci bir kontrol daha
                if (textureView.isAvailable()) {
                    Bitmap bitmap = textureView.getBitmap();

                    if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                        // Bitmap'in gerçekten içerik olup olmadığını kontrol et
                        callback.onBitmapCaptured(bitmap);
                    } else {
                        callback.onCaptureFailed();
                    }
                } else {
                    callback.onCaptureFailed();
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.onCaptureFailed();
            }
        }, 200); // 200ms gecikme - render döngüsünü bekle
    }

    /**
     * ALTERNATIF METOD: SurfaceTexture listener kullanarak frame yakalama
     * Daha güvenilir ama biraz daha karmaşık
     */
    public void captureCurrentFrameAdvanced(BitmapCaptureCallback callback) {
        if (playerView == null || callback == null) {
            if (callback != null) callback.onCaptureFailed();
            return;
        }

        android.view.TextureView textureView = findTextureView(playerView);

        if (textureView == null || !textureView.isAvailable()) {
            callback.onCaptureFailed();
            return;
        }

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (surfaceTexture == null) {
            callback.onCaptureFailed();
            return;
        }

        // Frame update listener ekle
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surface) {
                // Listener'ı kaldır (bir kere çalışsın)
                surface.setOnFrameAvailableListener(null);

                // Bir sonraki frame'i bekle
                mainHandler.post(() -> {
                    try {
                        Bitmap bitmap = textureView.getBitmap();

                        if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                            callback.onBitmapCaptured(bitmap);
                        } else {
                            callback.onCaptureFailed();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.onCaptureFailed();
                    }
                });
            }
        });
    }

    /**
     * PlayerView içindeki TextureView'ı recursive olarak bul
     */
    private android.view.TextureView findTextureView(View view) {
        if (view instanceof android.view.TextureView) {
            return (android.view.TextureView) view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                android.view.TextureView found = findTextureView(viewGroup.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Player'ın hazır olup olmadığını kontrol et
     */
    public boolean isPlayerReady() {
        return player != null && player.getPlaybackState() == Player.STATE_READY;
    }

    /**
     * TextureView'ın hazır olup olmadığını kontrol et
     */
    public boolean isTextureViewReady() {
        android.view.TextureView textureView = findTextureView(playerView);
        return textureView != null && textureView.isAvailable() && textureView.getSurfaceTexture() != null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}