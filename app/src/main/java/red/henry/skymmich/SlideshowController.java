package red.henry.skymmich;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SlideshowController {

    private static final String TAG = "Slideshow";
    private static final int BATCH_SIZE = 20;
    private static final int REFILL_THRESHOLD = 5;
    private static final long CROSSFADE_DURATION = 1500;
    private static final int MAX_HISTORY = 100;

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface MetadataCallback {
        void onMetadata(ImmichApi.AssetInfo info);
    }

    private final ImageView frontImage;
    private final ImageView backImage;
    private final ImmichApi api;
    private final OkHttpClient httpClient;
    private final PhotoCache photoCache;
    private final Handler handler;
    private final ExecutorService executor;
    private final ErrorCallback errorCallback;

    private final List<String> queue = new ArrayList<>();
    private final LinkedList<String> history = new LinkedList<>();
    // historyPos: 0 = playing live, >0 = viewing that many steps back in history
    private int historyPos = 0;

    private List<String> albumIds = Collections.emptyList();
    private MetadataCallback metadataCallback;
    private int intervalMs = 30000;
    private boolean running = false;
    private boolean frontVisible = true;

    private final Runnable advanceRunnable = this::advance;

    public SlideshowController(ImageView frontImage, ImageView backImage,
                                ImmichApi api, OkHttpClient httpClient,
                                PhotoCache photoCache, ErrorCallback errorCallback) {
        this.frontImage = frontImage;
        this.backImage = backImage;
        this.api = api;
        this.httpClient = httpClient;
        this.photoCache = photoCache;
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.errorCallback = errorCallback;
    }

    public void setInterval(int seconds) {
        this.intervalMs = seconds * 1000;
    }

    public void setAlbumIds(List<String> albumIds) {
        this.albumIds = albumIds != null ? albumIds : Collections.emptyList();
    }

    public void setMetadataCallback(MetadataCallback cb) {
        this.metadataCallback = cb;
    }

    public boolean isNavigatingHistory() {
        return historyPos > 0;
    }

    public void start() {
        if (running) return;
        running = true;
        Log.d(TAG, "Starting slideshow, interval=" + intervalMs + "ms");
        advance();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(advanceRunnable);
    }

    /** Navigate one step back in history. */
    public void goBack() {
        if (!running) return;
        if (history.size() <= 1) return;
        int maxBack = history.size() - 1;
        if (historyPos >= maxBack) return;
        historyPos++;
        handler.removeCallbacks(advanceRunnable);
        String assetId = getHistoryAsset();
        Log.d(TAG, "goBack historyPos=" + historyPos + " assetId=" + assetId);
        showAsset(assetId);
        handler.postDelayed(advanceRunnable, intervalMs);
    }

    /** Navigate one step forward — either within history or to a new image. */
    public void goForward() {
        if (!running) return;
        handler.removeCallbacks(advanceRunnable);
        if (historyPos > 0) {
            historyPos--;
            if (historyPos == 0) {
                // Back at the live position — advance to a fresh image
                advance();
            } else {
                String assetId = getHistoryAsset();
                Log.d(TAG, "goForward historyPos=" + historyPos + " assetId=" + assetId);
                showAsset(assetId);
                handler.postDelayed(advanceRunnable, intervalMs);
            }
        } else {
            advance();
        }
    }

    private String getHistoryAsset() {
        // history is newest-last; historyPos=1 means second-to-last, etc.
        int idx = history.size() - 1 - historyPos;
        return history.get(idx);
    }

    private void advance() {
        if (!running) return;

        // When navigating history, advance resumes live playback
        historyPos = 0;

        if (queue.size() < REFILL_THRESHOLD) {
            refillQueue();
        }

        if (queue.isEmpty()) {
            handler.postDelayed(advanceRunnable, 3000);
            return;
        }

        String assetId = queue.remove(0);
        pushHistory(assetId);
        showAsset(assetId);
    }

    private void pushHistory(String assetId) {
        history.addLast(assetId);
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    /** Resolve assetId to a local File (from cache or downloaded), then crossfade on main thread. */
    private void showAsset(String assetId) {
        final ImageView loadTarget = frontVisible ? backImage : frontImage;
        final ImageView fadeOut = frontVisible ? frontImage : backImage;

        executor.execute(() -> {
            try {
                File imageFile = resolveToFile(assetId);
                handler.post(() -> crossfade(loadTarget, fadeOut, imageFile, assetId));
            } catch (IOException e) {
                Log.w(TAG, "Failed to resolve asset " + assetId + ": " + e.getMessage());
                if (running) {
                    handler.postDelayed(advanceRunnable, 1000);
                }
            }
        });
    }

    /** Returns a local File for assetId, downloading and caching if not already cached. */
    private File resolveToFile(String assetId) throws IOException {
        if (photoCache.has(assetId)) {
            photoCache.touch(assetId);
            return photoCache.fileFor(assetId);
        }

        Request request = new Request.Builder()
                .url(api.getPreviewUrl(assetId))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            byte[] bytes = response.body().bytes();
            photoCache.put(assetId, bytes);
            return photoCache.fileFor(assetId);
        }
    }

    private void refillQueue() {
        executor.execute(() -> {
            try {
                List<String> newIds = api.fetchRandomAssetIds(BATCH_SIZE, albumIds);
                handler.post(() -> {
                    queue.addAll(newIds);
                    Log.d(TAG, "Queue refilled, size=" + queue.size());
                    if (queue.size() == newIds.size() && running) {
                        handler.removeCallbacks(advanceRunnable);
                        advance();
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Offline — falling back to photo cache", e);
                List<String> cachedIds = photoCache.getCachedIds();
                if (!cachedIds.isEmpty()) {
                    Collections.shuffle(cachedIds);
                    handler.post(() -> {
                        queue.addAll(cachedIds);
                        Log.d(TAG, "Offline queue from cache, size=" + queue.size());
                        if (running) {
                            handler.removeCallbacks(advanceRunnable);
                            advance();
                        }
                    });
                } else {
                    handler.post(() -> {
                        if (errorCallback != null) {
                            errorCallback.onError("Offline and no cached photos available");
                        }
                    });
                }
            }
        });
    }

    private void crossfade(ImageView loadTarget, ImageView fadeOut, File imageFile, String assetId) {
        Glide.with(loadTarget.getContext())
                .asBitmap()
                .load(imageFile)
                .format(DecodeFormat.PREFER_RGB_565)
                .override(1280, 800)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                        if (!running) return;

                        loadTarget.setImageBitmap(bitmap);
                        loadTarget.setAlpha(0f);

                        loadTarget.animate()
                                .alpha(1f)
                                .setDuration(CROSSFADE_DURATION)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        fadeOut.setImageDrawable(null);
                                        fadeOut.setAlpha(0f);
                                        frontVisible = !frontVisible;

                                        if (metadataCallback != null && assetId != null) {
                                            fetchMetadata(assetId);
                                        }

                                        if (running && !isNavigatingHistory()) {
                                            handler.postDelayed(advanceRunnable, intervalMs);
                                        }
                                    }
                                })
                                .start();
                    }

                    @Override
                    public void onLoadFailed(Drawable errorDrawable) {
                        Log.w(TAG, "Failed to load image from file: " + imageFile);
                        if (running) {
                            handler.postDelayed(advanceRunnable, 1000);
                        }
                    }

                    @Override
                    public void onLoadCleared(Drawable placeholder) {}
                });
    }

    private void fetchMetadata(String assetId) {
        executor.execute(() -> {
            ImmichApi.AssetInfo info = api.fetchAssetInfo(assetId);
            if (info != null && metadataCallback != null) {
                handler.post(() -> metadataCallback.onMetadata(info));
            }
        });
    }
}
