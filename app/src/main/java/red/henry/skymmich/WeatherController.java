package red.henry.skymmich;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Periodically fetches weather and delivers results via WeatherCallback.
 *
 * If apiKey is empty, uses the free Open-Meteo API (no key required).
 * If apiKey is provided, uses OpenWeatherMap.
 *
 * location can be a city name ("London") or "lat,lon" ("51.5,-0.1").
 */
public class WeatherController {

    private static final String TAG = "WeatherController";
    private static final long FETCH_INTERVAL_MINUTES = 30;

    public interface WeatherCallback {
        void onWeather(String temperature, String description);
    }

    private final OkHttpClient client;
    private final String location;
    private final String apiKey;
    private final String tempUnits;
    private final WeatherCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService scheduler;

    public WeatherController(OkHttpClient client, String location,
                             String apiKey, String tempUnits,
                             WeatherCallback callback) {
        this.client = client;
        this.location = location.trim();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.tempUnits = tempUnits != null ? tempUnits : "fahrenheit";
        this.callback = callback;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // Fetch immediately, then every 30 minutes
        scheduler.scheduleAtFixedRate(this::fetchWeather,
                0, FETCH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void fetchWeather() {
        try {
            if (apiKey.isEmpty()) {
                fetchOpenMeteo();
            } else {
                fetchOpenWeatherMap();
            }
        } catch (Exception e) {
            Log.w(TAG, "Weather fetch failed: " + e.getMessage());
            // Silently retry on next interval
        }
    }

    // ── Open-Meteo (free, no key) ─────────────────────────────────────────────

    private void fetchOpenMeteo() throws Exception {
        double lat, lon;

        if (location.matches("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$")) {
            String[] parts = location.split(",");
            lat = Double.parseDouble(parts[0]);
            lon = Double.parseDouble(parts[1]);
        } else {
            // Geocode the city name
            double[] coords = geocodeOpenMeteo(location);
            if (coords == null) {
                Log.w(TAG, "Open-Meteo geocoding failed for: " + location);
                return;
            }
            lat = coords[0];
            lon = coords[1];
        }

        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,weather_code"
                + "&temperature_unit=" + ("celsius".equals(tempUnits) ? "celsius" : "fahrenheit");

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "Open-Meteo forecast HTTP " + response.code());
                return;
            }
            JSONObject root = new JSONObject(response.body().string());
            JSONObject current = root.getJSONObject("current");
            double temp = current.getDouble("temperature_2m");
            int code = current.getInt("weather_code");
            String tempStr = Math.round(temp) + ("celsius".equals(tempUnits) ? "°C" : "°F");
            String desc = wmoDescription(code);
            deliver(tempStr, desc);
        }
    }

    private double[] geocodeOpenMeteo(String cityName) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + java.net.URLEncoder.encode(cityName, "UTF-8")
                + "&count=1";
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            JSONObject root = new JSONObject(response.body().string());
            if (!root.has("results")) return null;
            JSONArray results = root.getJSONArray("results");
            if (results.length() == 0) return null;
            JSONObject first = results.getJSONObject(0);
            return new double[]{first.getDouble("latitude"), first.getDouble("longitude")};
        }
    }

    /** WMO Weather interpretation codes → human-readable descriptions */
    private String wmoDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 49) return "Fog";
        if (code <= 59) return "Drizzle";
        if (code <= 69) return "Rain";
        if (code <= 79) return "Snow";
        if (code <= 84) return "Rain showers";
        if (code <= 94) return "Thunderstorm";
        return "Thunderstorm";
    }

    // ── OpenWeatherMap ────────────────────────────────────────────────────────

    private void fetchOpenWeatherMap() throws Exception {
        String query = location.matches("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$")
                ? "lat=" + location.split(",")[0] + "&lon=" + location.split(",")[1]
                : "q=" + java.net.URLEncoder.encode(location, "UTF-8");

        String url = "https://api.openweathermap.org/data/2.5/weather?"
                + query + "&appid=" + apiKey + "&units=" + ("celsius".equals(tempUnits) ? "metric" : "imperial");

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "OpenWeatherMap HTTP " + response.code());
                return;
            }
            JSONObject root = new JSONObject(response.body().string());
            double temp = root.getJSONObject("main").getDouble("temp");
            String desc = root.getJSONArray("weather")
                    .getJSONObject(0).getString("description");
            // Capitalise first letter
            if (!desc.isEmpty()) {
                desc = Character.toUpperCase(desc.charAt(0)) + desc.substring(1);
            }
            deliver(Math.round(temp) + ("celsius".equals(tempUnits) ? "°C" : "°F"), desc);
        }
    }

    private void deliver(String temp, String desc) {
        mainHandler.post(() -> callback.onWeather(temp, desc));
    }
}
