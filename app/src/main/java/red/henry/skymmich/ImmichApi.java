package red.henry.skymmich;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImmichApi {

    private static final String TAG = "ImmichApi";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private String serverUrl;
    private String apiKey;

    public static class AssetInfo {
        public final String filename;
        public final String dateTaken;
        public final String city;
        public final String country;

        AssetInfo(String filename, String dateTaken, String city, String country) {
            this.filename = filename;
            this.dateTaken = dateTaken;
            this.city = city;
            this.country = country;
        }
    }

    public ImmichApi(OkHttpClient client) {
        this.client = client;
    }

    public void configure(String serverUrl, String apiKey) {
        this.serverUrl = serverUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    /**
     * Fetch a batch of random image asset IDs, optionally restricted to specific albums.
     * If albumIds is empty, fetches from the entire library.
     */
    public List<String> fetchRandomAssetIds(int count, List<String> albumIds) throws IOException {
        if (albumIds == null || albumIds.isEmpty()) {
            return fetchRandomAssetIdsUnfiltered(count);
        }
        return fetchRandomAssetIdsFromAlbums(count, albumIds);
    }

    /** Convenience overload for callers that don't use album filtering. */
    public List<String> fetchRandomAssetIds(int count) throws IOException {
        return fetchRandomAssetIdsUnfiltered(count);
    }

    private List<String> fetchRandomAssetIdsUnfiltered(int count) throws IOException {
        List<String> ids = trySearchRandom(count, null);
        if (ids != null && !ids.isEmpty()) return ids;
        return tryGetRandom(count);
    }

    private List<String> fetchRandomAssetIdsFromAlbums(int count, List<String> albumIds)
            throws IOException {
        List<String> pool = new ArrayList<>();
        // Round-robin: try each album, collect up to count ids total
        int perAlbum = Math.max(1, (count + albumIds.size() - 1) / albumIds.size());
        for (String albumId : albumIds) {
            try {
                List<String> ids = fetchAssetIdsFromAlbum(albumId, perAlbum);
                pool.addAll(ids);
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch from album " + albumId + ": " + e.getMessage());
            }
        }
        if (pool.isEmpty()) {
            Log.w(TAG, "All album fetches failed, falling back to unfiltered");
            return fetchRandomAssetIdsUnfiltered(count);
        }
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    private List<String> fetchAssetIdsFromAlbum(String albumId, int limit) throws IOException {
        Request request = new Request.Builder()
                .url(serverUrl + "/api/albums/" + albumId)
                .header("x-api-key", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GET album failed: " + response.code());
            }
            String body = response.body().string();
            JSONObject album = new JSONObject(body);
            JSONArray array = album.getJSONArray("assets");
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject asset = array.getJSONObject(i);
                // Only include images
                if (asset.has("id") &&
                        "IMAGE".equals(asset.optString("type", "IMAGE"))) {
                    ids.add(asset.getString("id"));
                }
            }
            Collections.shuffle(ids);
            Log.d(TAG, "Album " + albumId + " returned " + ids.size() + " image assets");
            return ids.subList(0, Math.min(limit, ids.size()));
        } catch (Exception e) {
            throw new IOException("Failed to fetch album " + albumId, e);
        }
    }

    private List<String> trySearchRandom(int count, String albumId) {
        try {
            JSONObject body = new JSONObject();
            body.put("size", count);
            body.put("type", "IMAGE");
            if (albumId != null) body.put("albumId", albumId);

            Request request = new Request.Builder()
                    .url(serverUrl + "/api/search/random")
                    .header("x-api-key", apiKey)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "search/random failed: " + response.code());
                    return null;
                }
                String responseBody = response.body().string();
                JSONArray array = new JSONArray(responseBody);
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject asset = array.getJSONObject(i);
                    if (asset.has("id")) ids.add(asset.getString("id"));
                }
                Log.d(TAG, "search/random returned " + ids.size() + " assets");
                return ids;
            }
        } catch (Exception e) {
            Log.e(TAG, "search/random error: " + e.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }

    private List<String> tryGetRandom(int count) throws IOException {
        Request request = new Request.Builder()
                .url(serverUrl + "/api/assets/random?count=" + count)
                .header("x-api-key", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GET random failed: " + response.code());
            }
            String responseBody = response.body().string();
            JSONArray array = new JSONArray(responseBody);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject asset = array.getJSONObject(i);
                if (asset.has("id")) ids.add(asset.getString("id"));
            }
            Log.d(TAG, "assets/random returned " + ids.size() + " assets");
            return ids;
        } catch (Exception e) {
            throw new IOException("Failed to fetch random assets", e);
        }
    }

    /** Build the URL for loading a preview-sized image. */
    public String getPreviewUrl(String assetId) {
        return serverUrl + "/api/assets/" + assetId + "/thumbnail?size=preview";
    }

    /** Fetch metadata for a single asset. Returns null on failure. */
    public AssetInfo fetchAssetInfo(String assetId) {
        try {
            Request request = new Request.Builder()
                    .url(serverUrl + "/api/assets/" + assetId)
                    .header("x-api-key", apiKey)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                JSONObject obj = new JSONObject(response.body().string());
                String filename = obj.optString("originalFilename", "");
                String dateTaken = obj.optString("localDateTime", "");
                // Trim to date portion only (yyyy-MM-dd)
                if (dateTaken.length() > 10) dateTaken = dateTaken.substring(0, 10);
                String city = null;
                String country = null;
                if (obj.has("exifInfo")) {
                    JSONObject exif = obj.getJSONObject("exifInfo");
                    city = exif.isNull("city") ? null : exif.optString("city", null);
                    country = exif.isNull("country") ? null : exif.optString("country", null);
                }
                return new AssetInfo(filename, dateTaken, city, country);
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchAssetInfo failed for " + assetId + ": " + e.getMessage());
            return null;
        }
    }

    /** Returns null on success, or an error message on failure. */
    public String testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(serverUrl + "/api/server/ping")
                    .header("x-api-key", apiKey)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) return null;
                return "HTTP " + response.code();
            }
        } catch (Exception e) {
            Log.e(TAG, "testConnection failed: " + e.getClass().getName() + ": " + e.getMessage());
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (e.getCause() != null) {
                msg += "\n" + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            return msg;
        }
    }
}
