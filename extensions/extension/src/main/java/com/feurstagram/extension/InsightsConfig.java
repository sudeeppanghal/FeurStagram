package com.feurstagram.extension;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stores per-media insights overrides in SharedPreferences.
 *
 * Key format: insights_{mediaId}_{metric}
 * Metric names match Instagram's API field names exactly so the mocker can
 * do a simple key lookup in the JSON object.
 *
 * Country distribution is stored as a JSON string for the whole distribution
 * map so it can be round-tripped cleanly.
 *
 * Master toggle: insights_editor_enabled (default false = real data shown).
 */
public final class InsightsConfig {

    private static final String PREFS = "feurstagram_prefs";

    // Metric key suffixes — these match Instagram API field names
    public static final String VIEWS      = "video_view_count";
    public static final String LIKES      = "like_count";
    public static final String COMMENTS   = "comment_count";
    public static final String SHARES     = "share_count";
    public static final String SAVES      = "save_count";
    public static final String REACH      = "reach";
    public static final String IMPRESSIONS = "impression_count";
    public static final String PLAYS      = "plays";
    public static final String REPLAYS    = "replays";

    // All known per-media metrics
    public static final String[] ALL_METRICS = {
        VIEWS, LIKES, COMMENTS, SHARES, SAVES, REACH, IMPRESSIONS, PLAYS, REPLAYS
    };

    // Key for the set of media IDs that have overrides
    private static final String MEDIA_IDS_KEY = "insights_media_ids";

    private InsightsConfig() {}

    // ─── Master toggle ───────────────────────────────────────────────────────

    public static boolean isEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("insights_editor_enabled", false);
    }

    public static void setEnabled(boolean value) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putBoolean("insights_editor_enabled", value).apply();
    }

    // ─── Per-metric overrides ─────────────────────────────────────────────────

    /** Returns true if there is any override stored for this mediaId. */
    public static boolean hasOverride(String mediaId) {
        if (mediaId == null) return false;
        SharedPreferences p = prefs();
        if (p == null) return false;
        for (String m : ALL_METRICS) {
            if (p.contains(key(mediaId, m))) return true;
        }
        return false;
    }

    /**
     * Returns the overridden value for the given metric, or -1 if no override
     * is stored (caller should keep the real value).
     */
    public static long getOverride(String mediaId, String metric) {
        SharedPreferences p = prefs();
        if (p == null) return -1L;
        String k = key(mediaId, metric);
        if (!p.contains(k)) return -1L;
        return p.getLong(k, -1L);
    }

    public static void setOverride(String mediaId, String metric, long value) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putLong(key(mediaId, metric), value)
                .putBoolean("insights_editor_enabled", true)
                .apply();
        addToMediaIds(mediaId);
    }

    /** Returns true if ANY media ID or dashboard override is saved. */
    public static boolean hasAnyOverride() {
        Set<String> ids = getAllMediaIds();
        return (ids != null && !ids.isEmpty()) || hasDashboardOverrides();
    }

    /** Returns true if any dashboard-level metrics are saved. */
    public static boolean hasDashboardOverrides() {
        SharedPreferences p = prefs();
        if (p == null) return false;
        String[] dashboardMetrics = {
            "total_impressions", "total_reach", "total_profile_views",
            "followers_count", "accounts_reached", "accounts_engaged",
            "total_video_views", "video_view_count", "plays", "reach", "impressions"
        };
        for (String m : dashboardMetrics) {
            if (p.contains("dashboard_override_" + m)) return true;
        }
        return false;
    }

    /** Returns the first mediaId with overrides, or null. */
    public static String getFirstMediaId() {
        Set<String> ids = getAllMediaIds();
        if (ids == null || ids.isEmpty()) return null;
        return ids.iterator().next();
    }

    public static void removeOverride(String mediaId, String metric) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().remove(key(mediaId, metric)).apply();
    }

    /** Remove all overrides for a mediaId. */
    public static void clearOverrides(String mediaId) {
        SharedPreferences p = prefs();
        if (p == null) return;
        SharedPreferences.Editor ed = p.edit();
        for (String m : ALL_METRICS) {
            ed.remove(key(mediaId, m));
        }
        ed.remove(key(mediaId, "countries"));
        ed.apply();
        removeFromMediaIds(mediaId);
    }

    // ─── Country distribution ─────────────────────────────────────────────────

    /**
     * Stores a country distribution as a simple encoded string.
     * Format: "US:45,IN:30,GB:15,OTHER:10"
     * Percentages should sum to 100.
     */
    public static void setCountryDistribution(String mediaId, Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : distribution.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putString(key(mediaId, "countries"), sb.toString()).apply();
        addToMediaIds(mediaId);
    }

    /**
     * Returns the stored country distribution, or null if none set.
     */
    public static Map<String, Integer> getCountryDistribution(String mediaId) {
        SharedPreferences p = prefs();
        if (p == null) return null;
        String raw = p.getString(key(mediaId, "countries"), null);
        if (raw == null || raw.isEmpty()) return null;
        Map<String, Integer> result = new HashMap<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                try {
                    result.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    // ─── Traffic source distribution ──────────────────────────────────────────

    /**
     * Traffic source keys match Instagram's API values.
     * Format stored same as countries: "profile:30,home:25,hashtag:20,explore:15,other:10"
     */
    public static void setTrafficSources(String mediaId, Map<String, Integer> sources) {
        if (sources == null || sources.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : sources.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putString(key(mediaId, "traffic_sources"), sb.toString()).apply();
    }

    public static Map<String, Integer> getTrafficSources(String mediaId) {
        SharedPreferences p = prefs();
        if (p == null) return null;
        String raw = p.getString(key(mediaId, "traffic_sources"), null);
        if (raw == null || raw.isEmpty()) return null;
        Map<String, Integer> result = new HashMap<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                try {
                    result.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    // ─── Media ID tracking ───────────────────────────────────────────────────

    /** Returns all media IDs that have at least one override. */
    public static Set<String> getAllMediaIds() {
        SharedPreferences p = prefs();
        if (p == null) return new HashSet<>();
        Set<String> stored = p.getStringSet(MEDIA_IDS_KEY, null);
        return stored != null ? new HashSet<>(stored) : new HashSet<>();
    }

    private static void addToMediaIds(String mediaId) {
        SharedPreferences p = prefs();
        if (p == null) return;
        Set<String> ids = new HashSet<>(getAllMediaIds());
        ids.add(mediaId);
        p.edit().putStringSet(MEDIA_IDS_KEY, ids).apply();
    }

    private static void removeFromMediaIds(String mediaId) {
        SharedPreferences p = prefs();
        if (p == null) return;
        Set<String> ids = new HashSet<>(getAllMediaIds());
        ids.remove(mediaId);
        p.edit().putStringSet(MEDIA_IDS_KEY, ids).apply();
    }

    // ─── Dashboard aggregate override ────────────────────────────────────────

    /**
     * Computes the total delta for a metric across all overridden media.
     * Used to adjust dashboard-level totals: dashboard_total = real_total + delta.
     *
     * Since we don't know the real per-media values at dashboard level, we store
     * the user's absolute dashboard-level override separately.
     */
    public static long getDashboardOverride(String metric) {
        SharedPreferences p = prefs();
        if (p == null) return -1L;
        String k = "dashboard_override_" + metric;
        if (!p.contains(k)) return -1L;
        return p.getLong(k, -1L);
    }

    public static void setDashboardOverride(String metric, long value) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putLong("dashboard_override_" + metric, value).apply();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String key(String mediaId, String metric) {
        return "insights_" + mediaId + "_" + metric;
    }

    private static SharedPreferences prefs() {
        android.content.Context ctx = Config.getAppContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }
}
