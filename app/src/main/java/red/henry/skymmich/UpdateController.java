package red.henry.skymmich;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Checks GitHub Releases for a newer APK, downloads it, and installs
 * silently via {@code pm install -r} through su.
 */
public class UpdateController {

    private static final String TAG = "UpdateController";
    private static final String GITHUB_API = "https://api.github.com";
    private static final String APK_FILENAME = "skymmich-update.apk";

    public enum State {
        IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE,
        DOWNLOADING, INSTALLING, INSTALL_COMPLETE, ERROR
    }

    public interface Callback {
        void onStateChanged(State state, String message);
    }

    private final String githubRepo;
    private final int currentVersionCode;
    private final String currentVersionName;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;
    private final File apkFile;

    private String latestApkUrl;
    private String latestReleaseName;

    public UpdateController(String githubRepo, int currentVersionCode,
                            String currentVersionName, Context context,
                            Callback callback) {
        this.githubRepo = githubRepo;
        this.currentVersionCode = currentVersionCode;
        this.currentVersionName = currentVersionName;
        this.apkFile = new File(context.getCacheDir(), APK_FILENAME);
        this.callback = callback;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public void checkForUpdate() {
        postState(State.CHECKING, "Checking for updates…");

        new Thread(() -> {
            try {
                String url = GITHUB_API + "/repos/" + githubRepo + "/releases/latest";
                Request request = new Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        postState(State.ERROR, "GitHub API HTTP " + response.code());
                        return;
                    }

                    JSONObject release = new JSONObject(response.body().string());
                    String tagName = release.getString("tag_name");
                    latestReleaseName = release.optString("name", tagName);

                    // Find the .apk asset
                    latestApkUrl = null;
                    JSONArray assets = release.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            latestApkUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    if (latestApkUrl == null) {
                        postState(State.ERROR, "No APK in release " + tagName);
                        return;
                    }

                    String remoteVersion = tagName.startsWith("v")
                            ? tagName.substring(1) : tagName;

                    if (remoteVersion.equals(currentVersionName)) {
                        postState(State.UP_TO_DATE, "Up to date (v" + currentVersionName + ")");
                    } else {
                        postState(State.UPDATE_AVAILABLE, "Update available: " + latestReleaseName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
                postState(State.ERROR, "Check failed: " + e.getMessage());
            }
        }).start();
    }

    public void downloadAndInstall() {
        if (latestApkUrl == null) {
            postState(State.ERROR, "No update URL available");
            return;
        }

        postState(State.DOWNLOADING, "Downloading " + latestReleaseName + "…");

        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(latestApkUrl).get().build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        postState(State.ERROR, "Download failed: HTTP " + response.code());
                        return;
                    }

                    try (InputStream in = response.body().byteStream();
                         FileOutputStream out = new FileOutputStream(apkFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                    }
                    apkFile.setReadable(true, false);
                    Log.d(TAG, "Downloaded " + apkFile.length() + " bytes to " + apkFile.getAbsolutePath());
                }

                postState(State.INSTALLING, "Installing update…");
                installViaRoot();

            } catch (Exception e) {
                Log.e(TAG, "Download/install failed", e);
                postState(State.ERROR, "Update failed: " + e.getMessage());
            }
        }).start();
    }

    private void installViaRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = p.getOutputStream();
            os.write(("pm install -r " + apkFile.getAbsolutePath() + "\nexit\n").getBytes());
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) output.append(line).append("\n");
            while ((line = er.readLine()) != null) output.append("ERR: ").append(line).append("\n");

            int exit = p.waitFor();
            Log.d(TAG, "pm install exit=" + exit + " output: " + output);

            if (exit == 0 && output.toString().contains("Success")) {
                apkFile.delete();
                postState(State.INSTALL_COMPLETE, "Update installed! Restarting…");
                // pm install -r on Android 7 kills and restarts the app.
                // Safety net: force-start after 2s if it doesn't auto-restart.
                mainHandler.postDelayed(() -> {
                    try {
                        Process r = Runtime.getRuntime().exec("su");
                        java.io.OutputStream ros = r.getOutputStream();
                        ros.write(("am start -n red.henry.skymmich/.MainActivity\nexit\n").getBytes());
                        ros.flush();
                        ros.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Restart failed", e);
                    }
                }, 2000);
            } else {
                postState(State.ERROR, "Install failed: " + output.toString().trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Root install failed", e);
            postState(State.ERROR, "Root install failed: " + e.getMessage());
        }
    }

    public String getCurrentVersionDisplay() {
        return "v" + currentVersionName + " (build " + currentVersionCode + ")";
    }

    private void postState(State state, String message) {
        mainHandler.post(() -> callback.onStateChanged(state, message));
    }
}
