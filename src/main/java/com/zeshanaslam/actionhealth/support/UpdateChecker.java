package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Scheduler;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Checks spigotmc.org once a day for a newer ActionHealth version ("Update Check").
 * <p>
 * The request asks only for the latest version number. It sends no data about the server or
 * its players. The system property actionhealth.updateurl replaces the URL, for tests.
 */
public class UpdateChecker {

    public static final String DOWNLOAD_URL = "https://www.spigotmc.org/resources/action-bar-health.2661/";
    private static final String DEFAULT_URL = "https://api.spigotmc.org/legacy/update.php?resource=2661";
    private static final long FIRST_CHECK_SECONDS = 5;
    private static final long PERIOD_SECONDS = 24 * 60 * 60;
    private static final int TIMEOUT_MILLIS = 5000;

    private final Plugin plugin;
    private final String current;
    private final String url;
    // Set on a background thread, read on the main thread or a region thread.
    private volatile String latest;
    private Scheduler.Task task;

    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
        this.current = plugin.getDescription().getVersion();
        this.url = System.getProperty("actionhealth.updateurl", DEFAULT_URL);
    }

    /**
     * Starts the daily check, or stops it, to match the config.
     */
    public synchronized void setEnabled(boolean enabled) {
        if (enabled && task == null) {
            task = Scheduler.runAsyncTimer(plugin, this::check, FIRST_CHECK_SECONDS, PERIOD_SECONDS);
        } else if (!enabled && task != null) {
            task.cancel();
            task = null;
            latest = null;
        }
    }

    /**
     * Returns the newer version that the last check found, or null.
     */
    public String getLatest() {
        return latest;
    }

    public String getCurrent() {
        return current;
    }

    private void check() {
        try {
            String found = fetch();
            if (isNewer(current, found) && !found.equals(latest)) {
                latest = found;
                plugin.getLogger().info("ActionHealth " + found + " is available. This server runs " + current
                        + ". Download it at " + DOWNLOAD_URL);
            }
        } catch (IOException | RuntimeException e) {
            // Not important for the server owner, for example when the server has no internet
            // access. The next check tries again.
            plugin.getLogger().log(Level.FINE, "Could not check for a new version", e);
        }
    }

    private String fetch() throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "ActionHealth/" + current);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        }
    }

    /**
     * Returns true if latest is a higher version than current, for example 3.10.0 and 3.9.2.
     * Returns false if either is not a version made of numbers and dots.
     */
    static boolean isNewer(String current, String latest) {
        int[] a = parse(current);
        int[] b = parse(latest);
        if (a == null || b == null) return false;

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return y > x;
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null || !version.matches("[0-9]{1,9}(\\.[0-9]{1,9})*")) return null;

        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) numbers[i] = Integer.parseInt(parts[i]);
        return numbers;
    }
}
