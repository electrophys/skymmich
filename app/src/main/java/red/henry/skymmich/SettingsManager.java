package red.henry.skymmich;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class SettingsManager {

    private static final String PREFS_NAME = "skymmich_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final String KEY_ALBUM_IDS = "album_ids";
    private static final String KEY_SHOW_METADATA = "show_metadata";
    private static final String KEY_SHOW_CLOCK = "show_clock";
    private static final String KEY_CLOCK_STYLE = "clock_style";
    private static final String KEY_SHOW_WEATHER = "show_weather";
    private static final String KEY_WEATHER_API_KEY = "weather_api_key";
    private static final String KEY_WEATHER_LOCATION = "weather_location";
    private static final String KEY_ADB_OVER_NETWORK = "adb_over_network";
    private static final String KEY_ADB_PORT = "adb_port";
    private static final String KEY_TIMEZONE = "timezone";
    private static final String KEY_TEMP_UNITS = "temp_units";

    public static final String CLOCK_DIGITAL = "digital";
    public static final String CLOCK_ANALOG = "analog";
    public static final String CLOCK_BOTH = "both";

    public static final String TEMP_FAHRENHEIT = "fahrenheit";
    public static final String TEMP_CELSIUS = "celsius";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        prefs.edit().putString(KEY_API_KEY, key).apply();
    }

    public int getIntervalSeconds() {
        return prefs.getInt(KEY_INTERVAL, 30);
    }

    public void setIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_INTERVAL, seconds).apply();
    }

    public String getAlbumIds() {
        return prefs.getString(KEY_ALBUM_IDS, "");
    }

    public void setAlbumIds(String albumIds) {
        prefs.edit().putString(KEY_ALBUM_IDS, albumIds).apply();
    }

    /** Returns trimmed, non-empty album ID strings, or an empty list if none configured. */
    public List<String> getAlbumIdList() {
        String raw = getAlbumIds().trim();
        List<String> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public boolean isShowMetadata() {
        return prefs.getBoolean(KEY_SHOW_METADATA, false);
    }

    public void setShowMetadata(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_METADATA, show).apply();
    }

    public boolean isShowClock() {
        return prefs.getBoolean(KEY_SHOW_CLOCK, false);
    }

    public void setShowClock(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply();
    }

    public String getClockStyle() {
        return prefs.getString(KEY_CLOCK_STYLE, CLOCK_DIGITAL);
    }

    public void setClockStyle(String style) {
        prefs.edit().putString(KEY_CLOCK_STYLE, style).apply();
    }

    public boolean isShowWeather() {
        return prefs.getBoolean(KEY_SHOW_WEATHER, false);
    }

    public void setShowWeather(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_WEATHER, show).apply();
    }

    public String getWeatherApiKey() {
        return prefs.getString(KEY_WEATHER_API_KEY, "");
    }

    public void setWeatherApiKey(String key) {
        prefs.edit().putString(KEY_WEATHER_API_KEY, key).apply();
    }

    public String getWeatherLocation() {
        return prefs.getString(KEY_WEATHER_LOCATION, "");
    }

    public void setWeatherLocation(String location) {
        prefs.edit().putString(KEY_WEATHER_LOCATION, location).apply();
    }

    public boolean isAdbOverNetwork() {
        return prefs.getBoolean(KEY_ADB_OVER_NETWORK, false);
    }

    public void setAdbOverNetwork(boolean enabled) {
        prefs.edit().putBoolean(KEY_ADB_OVER_NETWORK, enabled).apply();
    }

    public int getAdbPort() {
        return prefs.getInt(KEY_ADB_PORT, 5555);
    }

    public void setAdbPort(int port) {
        prefs.edit().putInt(KEY_ADB_PORT, port).apply();
    }

    public String getTimezone() {
        return prefs.getString(KEY_TIMEZONE, "US/Pacific");
    }

    public void setTimezone(String timezone) {
        prefs.edit().putString(KEY_TIMEZONE, timezone).apply();
    }

    public String getTempUnits() {
        return prefs.getString(KEY_TEMP_UNITS, TEMP_FAHRENHEIT);
    }

    public void setTempUnits(String units) {
        prefs.edit().putString(KEY_TEMP_UNITS, units).apply();
    }

    public boolean isConfigured() {
        return !getServerUrl().isEmpty() && !getApiKey().isEmpty();
    }
}
