package red.henry.skymmich;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Persistent on-disk photo cache, keyed by Immich asset ID.
 * Stored in getFilesDir()/photo_cache/ — survives app updates and system cache clears.
 * LRU eviction keeps the cache under MAX_PHOTOS entries.
 */
public class PhotoCache {

    private static final String TAG = "PhotoCache";
    private static final int MAX_PHOTOS = 500;

    private final File cacheDir;

    public PhotoCache(Context context) {
        cacheDir = new File(context.getFilesDir(), "photo_cache");
        cacheDir.mkdirs();
    }

    public boolean has(String assetId) {
        return fileFor(assetId).exists();
    }

    public File fileFor(String assetId) {
        return new File(cacheDir, assetId + ".jpg");
    }

    /** Update last-modified time so this entry is treated as recently used. */
    public void touch(String assetId) {
        fileFor(assetId).setLastModified(System.currentTimeMillis());
    }

    public void put(String assetId, byte[] data) throws IOException {
        File f = fileFor(assetId);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        evictIfNeeded();
        Log.d(TAG, "Cached " + assetId + " (" + data.length / 1024 + " KB), total=" + size());
    }

    public List<String> getCachedIds() {
        File[] files = cacheDir.listFiles();
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>(files.length);
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".jpg")) ids.add(name.substring(0, name.length() - 4));
        }
        return ids;
    }

    public int size() {
        File[] files = cacheDir.listFiles();
        return files == null ? 0 : files.length;
    }

    private void evictIfNeeded() {
        File[] files = cacheDir.listFiles();
        if (files == null || files.length <= MAX_PHOTOS) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int toDelete = files.length - MAX_PHOTOS;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
            Log.d(TAG, "Evicted " + files[i].getName());
        }
    }
}
