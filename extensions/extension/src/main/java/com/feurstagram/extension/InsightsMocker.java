package com.feurstagram.extension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced insights interception engine.
 *
 * Intercepts Instagram API network response bodies (GraphQL, Bloks, REST, Tigon)
 * and replaces insights metrics (views, reach, impressions, likes, comments,
 * shares, saves, country distribution, traffic sources) with user-defined values.
 *
 * Meta/Instagram servers are NEVER updated — only local display bytes are modified.
 */
public final class InsightsMocker {

    private InsightsMocker() {}

    /**
     * Intercept and optionally rewrite a response body.
     */
    public static byte[] interceptResponse(String path, byte[] responseBody) {
        // Must be enabled or have overrides set
        if (!InsightsConfig.isEnabled() && !InsightsConfig.hasAnyOverride()) {
            return responseBody;
        }

        if (responseBody == null || responseBody.length < 10) {
            return responseBody;
        }

        try {
            String json = new String(responseBody, StandardCharsets.UTF_8);
            
            // Fast pre-filter: response must be a JSON object/array
            if (!json.startsWith("{") && !json.startsWith("[")) {
                return responseBody;
            }

            // Check if this response contains relevant insight keys or path markers
            if (!isInsightsPayload(path, json)) {
                return responseBody;
            }

            String modified = rewrite(path, json);
            if (modified == null || modified.equals(json)) {
                return responseBody;
            }

            return modified.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // Safety: on any error return original response
            return responseBody;
        }
    }

    private static boolean isInsightsPayload(String path, String json) {
        if (path != null) {
            String p = path.toLowerCase();
            if (p.contains("insights") || p.contains("professional") || p.contains("dashboard") ||
                p.contains("business") || p.contains("creator") || p.contains("graphql") ||
                p.contains("bloks") || p.contains("media/") || p.contains("clips/")) {
                return true;
            }
        }
        // Content inspection
        return json.contains("insights") ||
               json.contains("video_view_count") ||
               json.contains("play_count") ||
               json.contains("reach") ||
               json.contains("impression") ||
               json.contains("accounts_reached") ||
               json.contains("like_count");
    }

    private static String rewrite(String path, String json) {
        // 1. Identify which mediaId to use for overrides
        String matchedMediaId = findMatchingMediaId(path, json);

        // Fallback: if no specific mediaId matched, but user has overrides set,
        // use the first configured mediaId as the default override!
        if (matchedMediaId == null) {
            matchedMediaId = InsightsConfig.getFirstMediaId();
        }

        if (matchedMediaId != null) {
            json = applyPerMediaOverrides(matchedMediaId, json);
        }

        // 2. Dashboard / Aggregate totals
        json = applyDashboardOverrides(json);

        return json;
    }

    private static String findMatchingMediaId(String path, String json) {
        Set<String> configuredIds = InsightsConfig.getAllMediaIds();
        if (configuredIds == null || configuredIds.isEmpty()) return null;

        // Direct check if any configured ID/shortcode appears in path or JSON
        for (String id : configuredIds) {
            if (id == null || id.isEmpty()) continue;
            if ((path != null && path.contains(id)) || json.contains(id)) {
                return id;
            }
        }
        return null;
    }

    private static String applyPerMediaOverrides(String mediaId, String json) {
        // Map of standard metrics to all possible JSON keys Instagram uses
        String[][] metricMap = {
            { InsightsConfig.VIEWS, "video_view_count", "play_count", "plays", "replays", "fb_play_count", "view_count", "total_video_views", "total_plays" },
            { InsightsConfig.LIKES, "like_count", "likes" },
            { InsightsConfig.COMMENTS, "comment_count", "comments" },
            { InsightsConfig.SHARES, "share_count", "shares", "forward_count" },
            { InsightsConfig.SAVES, "save_count", "saves", "bookmark_count" },
            { InsightsConfig.REACH, "reach", "accounts_reached", "total_reach", "organic_reach" },
            { InsightsConfig.IMPRESSIONS, "impression_count", "impressions", "total_impressions" },
            { InsightsConfig.PLAYS, "plays", "play_count" },
            { InsightsConfig.REPLAYS, "replays" },
        };

        for (String[] mapping : metricMap) {
            String primaryMetric = mapping[0];
            long value = InsightsConfig.getOverride(mediaId, primaryMetric);
            if (value >= 0) {
                for (int i = 1; i < mapping.length; i++) {
                    json = replaceJsonLongValue(json, mapping[i], value);
                }
            }
        }

        // Country distribution
        Map<String, Integer> countries = InsightsConfig.getCountryDistribution(mediaId);
        if (countries != null && !countries.isEmpty()) {
            json = rewriteCountryDistribution(json, countries);
        }

        // Traffic sources
        Map<String, Integer> sources = InsightsConfig.getTrafficSources(mediaId);
        if (sources != null && !sources.isEmpty()) {
            json = rewriteTrafficSources(json, sources);
        }

        return json;
    }

    private static String applyDashboardOverrides(String json) {
        String[] dashboardMetrics = {
            "total_impressions", "total_reach", "total_profile_views",
            "followers_count", "accounts_reached", "accounts_engaged",
            "total_video_views", "video_view_count", "plays", "reach", "impressions"
        };

        for (String metric : dashboardMetrics) {
            long override = InsightsConfig.getDashboardOverride(metric);
            if (override >= 0) {
                json = replaceJsonLongValue(json, metric, override);
            }
        }
        return json;
    }

    static String replaceJsonLongValue(String json, String fieldName, long newValue) {
        if (json == null || fieldName == null) return json;

        String quotedKey = "\"" + fieldName + "\"";
        int pos = 0;
        StringBuilder sb = new StringBuilder(json.length());

        while (pos < json.length()) {
            int keyStart = json.indexOf(quotedKey, pos);
            if (keyStart < 0) {
                sb.append(json, pos, json.length());
                break;
            }

            int colonPos = skipWhitespace(json, keyStart + quotedKey.length());
            if (colonPos >= json.length() || json.charAt(colonPos) != ':') {
                sb.append(json, pos, keyStart + quotedKey.length());
                pos = keyStart + quotedKey.length();
                continue;
            }

            int valueStart = skipWhitespace(json, colonPos + 1);
            if (valueStart >= json.length()) {
                sb.append(json, pos, json.length());
                break;
            }

            char firstChar = json.charAt(valueStart);
            if (firstChar != '-' && !Character.isDigit(firstChar)) {
                sb.append(json, pos, valueStart + 1);
                pos = valueStart + 1;
                continue;
            }

            int valueEnd = valueStart;
            if (json.charAt(valueEnd) == '-') valueEnd++;
            while (valueEnd < json.length() &&
                   (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.')) {
                valueEnd++;
            }

            sb.append(json, pos, valueStart);
            sb.append(newValue);
            pos = valueEnd;
        }

        return sb.toString();
    }

    private static int skipWhitespace(String s, int from) {
        while (from < s.length() && Character.isWhitespace(s.charAt(from))) from++;
        return from;
    }

    private static String rewriteCountryDistribution(String json, Map<String, Integer> dist) {
        if (dist == null || dist.isEmpty()) return json;

        long baseCount = extractLongValue(json, InsightsConfig.REACH);
        if (baseCount <= 0) baseCount = extractLongValue(json, InsightsConfig.VIEWS);
        if (baseCount <= 0) baseCount = 10000L;

        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            String cc = entry.getKey().toUpperCase();
            int pct = entry.getValue();
            long absoluteValue = (baseCount * pct) / 100L;
            json = rewriteCountryValue(json, cc, absoluteValue, pct);
        }
        return json;
    }

    private static String rewriteCountryValue(String json, String countryCode, long absValue, int pctValue) {
        String searchKey = "\"" + countryCode + "\"";
        int pos = json.indexOf(searchKey);
        if (pos < 0) return json;

        int searchEnd = Math.min(pos + 200, json.length());
        String region = json.substring(pos, searchEnd);

        String modified = replaceJsonLongValue(region, "value", absValue);
        modified = replaceJsonLongValue(modified, "count", absValue);
        modified = replaceJsonLongValue(modified, "percentage", pctValue);

        return json.substring(0, pos) + modified + json.substring(pos + region.length());
    }

    private static String rewriteTrafficSources(String json, Map<String, Integer> sources) {
        if (sources == null || sources.isEmpty()) return json;

        long baseViews = extractLongValue(json, InsightsConfig.VIEWS);
        if (baseViews <= 0) baseViews = extractLongValue(json, InsightsConfig.REACH);
        if (baseViews <= 0) baseViews = 10000L;

        for (Map.Entry<String, Integer> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            int pct = entry.getValue();
            long absValue = (baseViews * pct) / 100L;

            String searchKey = "\"" + sourceName.toLowerCase() + "\"";
            int pos = json.indexOf(searchKey);
            if (pos < 0) {
                searchKey = "\"" + sourceName.toUpperCase() + "\"";
                pos = json.indexOf(searchKey);
            }
            if (pos < 0) continue;

            int searchEnd = Math.min(pos + 200, json.length());
            String region = json.substring(pos, searchEnd);
            String modified = replaceJsonLongValue(region, "value", absValue);
            modified = replaceJsonLongValue(modified, "count", absValue);
            modified = replaceJsonLongValue(modified, "percentage", pct);
            json = json.substring(0, pos) + modified + json.substring(pos + region.length());
        }
        return json;
    }

    static long extractLongValue(String json, String fieldName) {
        if (json == null || fieldName == null) return -1L;
        String quotedKey = "\"" + fieldName + "\"";
        int keyPos = json.indexOf(quotedKey);
        if (keyPos < 0) return -1L;

        int colonPos = skipWhitespace(json, keyPos + quotedKey.length());
        if (colonPos >= json.length() || json.charAt(colonPos) != ':') return -1L;

        int valueStart = skipWhitespace(json, colonPos + 1);
        if (valueStart >= json.length()) return -1L;

        char fc = json.charAt(valueStart);
        if (fc == '"') {
            int end = json.indexOf('"', valueStart + 1);
            if (end < 0) return -1L;
            try {
                return Long.parseLong(json.substring(valueStart + 1, end));
            } catch (NumberFormatException e) { return -1L; }
        }

        if (fc != '-' && !Character.isDigit(fc)) return -1L;
        int valueEnd = valueStart;
        if (json.charAt(valueEnd) == '-') valueEnd++;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) valueEnd++;

        try {
            return Long.parseLong(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
