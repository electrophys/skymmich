package red.henry.skymmich;

import android.app.Activity;
import android.hardware.SensorManager;
import android.util.Log;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TextClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.util.Collections;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends Activity {

    private ImageView imageFront;
    private ImageView imageBack;
    private View settingsOverlay;
    private EditText editServerUrl;
    private EditText editApiKey;
    private EditText editInterval;
    private EditText editAlbumIds;
    private CheckBox checkShowMetadata;
    private CheckBox checkShowClock;
    private RadioGroup radioClockStyle;
    private RadioButton radioClockDigital;
    private RadioButton radioClockAnalog;
    private RadioButton radioClockBoth;
    private CheckBox checkShowWeather;
    private LinearLayout weatherFields;
    private EditText editWeatherLocation;
    private EditText editWeatherApiKey;
    private CheckBox checkAdbOverNetwork;
    private LinearLayout adbPortFields;
    private EditText editAdbPort;
    private Button btnSave;
    private TextView statusText;

    // Overlays
    private LinearLayout clockOverlay;
    private TextClock clockDigitalTime;
    private TextClock clockDigitalDate;
    private AnalogClockView clockAnalog;
    private LinearLayout weatherOverlay;
    private TextView weatherTemp;
    private TextView weatherDesc;
    private TextView metadataText;

    private SettingsManager settings;
    private ImmichApi api;
    private SlideshowController slideshow;
    private MotionWakeController motionController;
    private WeatherController weatherController;
    private OkHttpClient httpClient;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Slideshow views
        imageFront = findViewById(R.id.image_front);
        imageBack = findViewById(R.id.image_back);

        // Settings overlay views
        settingsOverlay = findViewById(R.id.settings_overlay);
        editServerUrl = findViewById(R.id.edit_server_url);
        editApiKey = findViewById(R.id.edit_api_key);
        editInterval = findViewById(R.id.edit_interval);
        editAlbumIds = findViewById(R.id.edit_album_ids);
        checkShowMetadata = findViewById(R.id.check_show_metadata);
        checkShowClock = findViewById(R.id.check_show_clock);
        radioClockStyle = findViewById(R.id.radio_clock_style);
        radioClockDigital = findViewById(R.id.radio_clock_digital);
        radioClockAnalog = findViewById(R.id.radio_clock_analog);
        radioClockBoth = findViewById(R.id.radio_clock_both);
        checkShowWeather = findViewById(R.id.check_show_weather);
        weatherFields = findViewById(R.id.weather_fields);
        editWeatherLocation = findViewById(R.id.edit_weather_location);
        editWeatherApiKey = findViewById(R.id.edit_weather_api_key);
        checkAdbOverNetwork = findViewById(R.id.check_adb_over_network);
        adbPortFields = findViewById(R.id.adb_port_fields);
        editAdbPort = findViewById(R.id.edit_adb_port);
        btnSave = findViewById(R.id.btn_save);
        statusText = findViewById(R.id.status_text);

        // Overlay views
        clockOverlay = findViewById(R.id.clock_overlay);
        clockDigitalTime = findViewById(R.id.clock_digital_time);
        clockDigitalDate = findViewById(R.id.clock_digital_date);
        clockAnalog = findViewById(R.id.clock_analog);
        weatherOverlay = findViewById(R.id.weather_overlay);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherDesc = findViewById(R.id.weather_desc);
        metadataText = findViewById(R.id.metadata_text);

        settings = new SettingsManager(this);

        // Wire up clock/weather checkbox enabling of dependent fields
        checkShowClock.setOnCheckedChangeListener((btn, checked) ->
                setGroupEnabled(radioClockStyle, checked));
        checkShowWeather.setOnCheckedChangeListener((btn, checked) ->
                setGroupEnabled(weatherFields, checked));
        checkAdbOverNetwork.setOnCheckedChangeListener((btn, checked) ->
                setGroupEnabled(adbPortFields, checked));

        btnSave.setOnClickListener(v -> saveAndStart());

        // Swipe gesture: left = forward, right = back
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_VELOCITY = 500;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                if (slideshow == null) return false;
                if (Math.abs(velocityX) <= Math.abs(velocityY)) return false;
                if (Math.abs(velocityX) < SWIPE_MIN_VELOCITY) return false;
                if (velocityX < 0) {
                    slideshow.goForward();
                } else {
                    slideshow.goBack();
                }
                return true;
            }
        });

        // Corner-tap (bottom-right 120dp) opens settings; all touches feed gesture detector
        View rootView = findViewById(R.id.root);
        rootView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float density = getResources().getDisplayMetrics().density;
                float cornerPx = 120 * density;
                float x = event.getX();
                float y = event.getY();
                float w = rootView.getWidth();
                float h = rootView.getHeight();
                if (x >= w - cornerPx && y >= h - cornerPx) {
                    toggleSettings();
                    return true;
                }
            }
            return true;
        });

        // Motion controller
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        motionController = new MotionWakeController(sensorManager, new MotionWakeController.Callback() {
            @Override
            public void onBrightnessChange(float brightness) {
                runOnUiThread(() -> setBrightness(brightness));
            }

            @Override
            public void onTripleTap() {
                runOnUiThread(() -> toggleSettings());
            }
        });

        bringUpNetwork();
    }

    private void saveAndStart() {
        String serverUrl = editServerUrl.getText().toString().trim();
        String apiKey = editApiKey.getText().toString().trim();
        String intervalStr = editInterval.getText().toString().trim();

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            statusText.setText(R.string.status_required);
            return;
        }

        int interval = 30;
        try {
            interval = Integer.parseInt(intervalStr);
            if (interval < 5) interval = 5;
            if (interval > 3600) interval = 3600;
        } catch (NumberFormatException e) {
            // Use default
        }

        // Persist all settings
        settings.setServerUrl(serverUrl);
        settings.setApiKey(apiKey);
        settings.setIntervalSeconds(interval);
        settings.setAlbumIds(editAlbumIds.getText().toString().trim());
        settings.setShowMetadata(checkShowMetadata.isChecked());
        settings.setShowClock(checkShowClock.isChecked());
        settings.setClockStyle(selectedClockStyle());
        settings.setShowWeather(checkShowWeather.isChecked());
        settings.setWeatherLocation(editWeatherLocation.getText().toString().trim());
        settings.setWeatherApiKey(editWeatherApiKey.getText().toString().trim());
        settings.setAdbOverNetwork(checkAdbOverNetwork.isChecked());
        int adbPort = 5555;
        try {
            adbPort = Integer.parseInt(editAdbPort.getText().toString().trim());
            if (adbPort < 1024 || adbPort > 65535) adbPort = 5555;
        } catch (NumberFormatException e) { /* use default */ }
        settings.setAdbPort(adbPort);

        statusText.setText(R.string.status_testing);
        statusText.setTextColor(0xFFAAAAFF);
        btnSave.setEnabled(false);

        final int finalInterval = interval;
        new Thread(() -> {
            buildHttpClient();
            api = new ImmichApi(httpClient);
            api.configure(settings.getServerUrl(), settings.getApiKey());
            String error = api.testConnection();

            runOnUiThread(() -> {
                btnSave.setEnabled(true);
                if (error == null) {
                    statusText.setText(R.string.status_connected);
                    statusText.setTextColor(0xFF66FF66);
                    settingsOverlay.setVisibility(View.GONE);
                    hideSystemUI();
                    applyOverlaySettings();
                    startSlideshow(finalInterval);
                } else {
                    statusText.setText(getString(R.string.status_failed) + error);
                    statusText.setTextColor(0xFFFF6666);
                }
            });
        }).start();
    }

    private String selectedClockStyle() {
        int id = radioClockStyle.getCheckedRadioButtonId();
        if (id == R.id.radio_clock_analog) return SettingsManager.CLOCK_ANALOG;
        if (id == R.id.radio_clock_both) return SettingsManager.CLOCK_BOTH;
        return SettingsManager.CLOCK_DIGITAL;
    }

    private void initAndStartSlideshow() {
        buildHttpClient();
        api = new ImmichApi(httpClient);
        api.configure(settings.getServerUrl(), settings.getApiKey());
        applyOverlaySettings();
        startSlideshow(settings.getIntervalSeconds());
    }

    private void buildHttpClient() {
        if (httpClient != null) return;

        final String apiKey = settings.getApiKey();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request modified = chain.request().newBuilder()
                            .header("x-api-key", apiKey)
                            .build();
                    return chain.proceed(modified);
                });

        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] c, String t) {}
                @Override public void checkServerTrusted(X509Certificate[] c, String t) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustAll);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            Log.e("MainActivity", "SSL setup failed", e);
        }

        httpClient = builder.build();
        ImmichGlideModule.setOkHttpClient(httpClient);
    }

    private void startSlideshow(int intervalSeconds) {
        if (slideshow != null) slideshow.stop();

        slideshow = new SlideshowController(imageFront, imageBack, api,
                message -> runOnUiThread(() -> {
                    statusText.setText(message);
                    statusText.setTextColor(0xFFFF6666);
                    settingsOverlay.setVisibility(View.VISIBLE);
                }));
        slideshow.setAlbumIds(settings.getAlbumIdList());
        slideshow.setInterval(intervalSeconds);

        if (settings.isShowMetadata()) {
            slideshow.setMetadataCallback(info -> {
                metadataText.setText(formatMetadata(info));
            });
        }

        slideshow.start();
    }

    private String formatMetadata(ImmichApi.AssetInfo info) {
        StringBuilder sb = new StringBuilder();
        if (info.dateTaken != null && !info.dateTaken.isEmpty()) sb.append(info.dateTaken);
        if (info.city != null) {
            if (sb.length() > 0) sb.append("  \u2014  ");
            sb.append(info.city);
            if (info.country != null) sb.append(", ").append(info.country);
        }
        if (info.filename != null && !info.filename.isEmpty()) {
            if (sb.length() > 0) sb.append("  \u2014  ");
            sb.append(info.filename);
        }
        return sb.toString();
    }

    private void applyOverlaySettings() {
        // Metadata
        metadataText.setVisibility(settings.isShowMetadata() ? View.VISIBLE : View.GONE);

        // Clock
        if (settings.isShowClock()) {
            clockOverlay.setVisibility(View.VISIBLE);
            String style = settings.getClockStyle();
            boolean showDigital = !SettingsManager.CLOCK_ANALOG.equals(style);
            boolean showAnalog = !SettingsManager.CLOCK_DIGITAL.equals(style);
            clockDigitalTime.setVisibility(showDigital ? View.VISIBLE : View.GONE);
            clockDigitalDate.setVisibility(showDigital ? View.VISIBLE : View.GONE);
            clockAnalog.setVisibility(showAnalog ? View.VISIBLE : View.GONE);
            if (showAnalog) clockAnalog.start();
        } else {
            clockOverlay.setVisibility(View.GONE);
            clockAnalog.stop();
        }

        // Weather
        if (weatherController != null) {
            weatherController.stop();
            weatherController = null;
        }
        if (settings.isShowWeather() && !settings.getWeatherLocation().isEmpty()) {
            weatherOverlay.setVisibility(View.VISIBLE);
            weatherController = new WeatherController(
                    httpClient,
                    settings.getWeatherLocation(),
                    settings.getWeatherApiKey(),
                    (temp, desc) -> runOnUiThread(() -> {
                        weatherTemp.setText(temp);
                        weatherDesc.setText(desc);
                    })
            );
            weatherController.start();
        } else {
            weatherOverlay.setVisibility(View.GONE);
        }
    }

    private void toggleSettings() {
        if (settingsOverlay.getVisibility() == View.VISIBLE) {
            settingsOverlay.setVisibility(View.GONE);
            hideSystemUI();
        } else {
            populateSettingsFields();
            settingsOverlay.setVisibility(View.VISIBLE);
            showDiagnostics();
        }
    }

    private void populateSettingsFields() {
        editServerUrl.setText(settings.getServerUrl());
        editApiKey.setText(settings.getApiKey());
        editInterval.setText(String.valueOf(settings.getIntervalSeconds()));
        editAlbumIds.setText(settings.getAlbumIds());
        checkShowMetadata.setChecked(settings.isShowMetadata());
        checkShowClock.setChecked(settings.isShowClock());
        String clockStyle = settings.getClockStyle();
        if (SettingsManager.CLOCK_ANALOG.equals(clockStyle)) radioClockAnalog.setChecked(true);
        else if (SettingsManager.CLOCK_BOTH.equals(clockStyle)) radioClockBoth.setChecked(true);
        else radioClockDigital.setChecked(true);
        setGroupEnabled(radioClockStyle, settings.isShowClock());
        checkShowWeather.setChecked(settings.isShowWeather());
        editWeatherLocation.setText(settings.getWeatherLocation());
        editWeatherApiKey.setText(settings.getWeatherApiKey());
        setGroupEnabled(weatherFields, settings.isShowWeather());
        checkAdbOverNetwork.setChecked(settings.isAdbOverNetwork());
        editAdbPort.setText(String.valueOf(settings.getAdbPort()));
        setGroupEnabled(adbPortFields, settings.isAdbOverNetwork());
    }

    private void setGroupEnabled(View group, boolean enabled) {
        group.setEnabled(enabled);
        group.setAlpha(enabled ? 1.0f : 0.4f);
        if (group instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) group;
            for (int i = 0; i < vg.getChildCount(); i++) {
                vg.getChildAt(i).setEnabled(enabled);
            }
        }
    }

    private void bringUpNetwork() {
        new Thread(() -> {
            try {
                boolean hasNetwork = false;
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    // Check for an assigned IPv4 address — works for WiFi, Ethernet, USB-RNDIS, etc.
                    for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            hasNetwork = true;
                            break;
                        }
                    }
                    if (hasNetwork) break;
                }
                if (hasNetwork) {
                    Log.d("MainActivity", "Network already up");
                    runOnUiThread(this::onNetworkReady);
                    return;
                }

                Log.d("MainActivity", "No network, running eth_up.sh via su");
                Process p = Runtime.getRuntime().exec("su");
                java.io.OutputStream os = p.getOutputStream();
                os.write("sh /data/local/tmp/eth_up.sh\nexit\n".getBytes());
                os.flush();
                os.close();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) output.append(line).append("\n");
                while ((line = er.readLine()) != null) output.append("ERR: ").append(line).append("\n");
                int exit = p.waitFor();
                output.append("exit=").append(exit).append("\n");
                Log.d("MainActivity", "eth_up.sh output: " + output);

                runOnUiThread(this::onNetworkReady);
            } catch (Exception e) {
                Log.e("MainActivity", "bringUpNetwork failed", e);
                runOnUiThread(this::onNetworkReady);
            }
        }).start();
    }

    private void enableAdbOverNetwork() {
        if (!settings.isAdbOverNetwork()) return;
        int port = settings.getAdbPort();
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                java.io.OutputStream os = p.getOutputStream();
                os.write(("setprop service.adb.tcp.port " + port + "\n"
                        + "stop adbd\n"
                        + "start adbd\n"
                        + "exit\n").getBytes());
                os.flush();
                os.close();
                p.waitFor();
                Log.d("MainActivity", "ADB over network enabled on port " + port);
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to enable ADB over network: " + e.getMessage());
            }
        }).start();
    }

    private void onNetworkReady() {
        enableAdbOverNetwork();
        if (settings.isConfigured()) {
            settingsOverlay.setVisibility(View.GONE);
            hideSystemUI();
            initAndStartSlideshow();
        } else {
            populateSettingsFields();
            settingsOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void showDiagnostics() {
        new Thread(() -> {
            StringBuilder diag = new StringBuilder();
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (ni.isUp() && !ni.isLoopback()) {
                        diag.append("IF: ").append(ni.getName());
                        java.util.List<java.net.InetAddress> addrs = Collections.list(ni.getInetAddresses());
                        for (java.net.InetAddress a : addrs) {
                            if (a instanceof java.net.Inet4Address) diag.append(" ").append(a.getHostAddress());
                        }
                        diag.append("\n");
                    }
                }
            } catch (Exception e) {
                diag.append("Interfaces: error\n");
            }
            if (diag.length() == 0) {
                diag.append("No active network interfaces!\n");
            } else if (settings.isAdbOverNetwork()) {
                // Extract first IPv4 to show the connect command
                try {
                    outer:
                    for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        if (!ni.isUp() || ni.isLoopback()) continue;
                        for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                            if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                                diag.append("ADB: adb connect ")
                                        .append(addr.getHostAddress())
                                        .append(":").append(settings.getAdbPort()).append("\n");
                                break outer;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            try {
                Process p = Runtime.getRuntime().exec(new String[]{"getprop", "net.dns1"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String dns1 = br.readLine();
                diag.append("DNS1: ").append(dns1 != null ? dns1 : "(empty)").append("\n");
                br.close();
            } catch (Exception e) {
                diag.append("DNS1: error\n");
            }

            try {
                File log = new File("/data/local/tmp/eth_up.log");
                if (log.exists()) {
                    BufferedReader br = new BufferedReader(new FileReader(log));
                    String line;
                    diag.append("\n-- eth_up.log --\n");
                    while ((line = br.readLine()) != null) diag.append(line).append("\n");
                    br.close();
                } else {
                    diag.append("\nNo eth_up.log\n");
                }
            } catch (Exception e) {
                diag.append("\neth_up.log: ").append(e.getMessage()).append("\n");
            }

            runOnUiThread(() -> {
                statusText.setText(diag.toString());
                statusText.setTextColor(0xFFAAAAFF);
            });
        }).start();
    }

    private void setBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness;
        getWindow().setAttributes(lp);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        motionController.start();
        if (settings.isShowClock() && SettingsManager.CLOCK_ANALOG.equals(settings.getClockStyle())
                || SettingsManager.CLOCK_BOTH.equals(settings.getClockStyle())) {
            clockAnalog.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        motionController.stop();
        clockAnalog.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (slideshow != null) slideshow.stop();
        if (weatherController != null) weatherController.stop();
        clockAnalog.stop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }
}
