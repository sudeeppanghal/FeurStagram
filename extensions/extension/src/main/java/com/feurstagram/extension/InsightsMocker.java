package com.feurstagram.extension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Core insights interception engine.
 *
 * Called from the injected TigonServiceLayer response hook with the raw
 * response body bytes. If insights editor is disabled, or the path doesn't
 * match an insights endpoint, the original bytes are returned unchanged.
 *
 * Otherwise the JSON is rewritten — individual metric values replaced with
 * overrides from InsightsConfig — and the modified bytes returned.
 *
 * All JSON manipulation is done with simple string operations deliberately:
 * - No external library dependency
 * - Minimal bytecode footprint in the patched APK
 * - Survives Instagram changing its JSON structure (field-name based matching)
 */
public final class InsightsMocker {

    private InsightsMocker() {}

    // ─── Insights API path patterns ───────────────────────────────────────────

    private static final String[] INSIGHTS_PATHS = {
        "/insights/",
        "/media_insights/",
        "/clips/insights/",
        "/business/get_account_insights/",
        "/creator/account_insights/",
        "/professional_dashboard/",
        "professional_dashboard",    // Bloks path prefix
        "clips_insights",            // Bloks component name
    };

    // ─── Public entry point ───────────────────────────────────────────────────

    /**
     * Intercept and optionally rewrite a response body.
     *
     * @param path         the request URI path
     * @param responseBody the raw response bytes from Meta servers
     * @return the (possibly modified) response bytes to pass to Instagram's parser
     */
    public static byte[] interceptResponse(String path, byte[] responseBody) {
        if (!InsightsConfig.isEnabled()) return responseBody;
        if (path == null || responseBody == null || responseBody.length == 0) return responseBody;

        if (!matchesInsightsPath(path)) return responseBody;

        try {
            String json = new String(responseBody, StandardCharsets.UTF_8);
            String modified = rewrite(path, json);
            if (modified == null || modified.equals(json)) return responseBody;
            return modified.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // Never crash Instagram — return the real response on any error
            return responseBody;
        }
    }

    // ─── Path matching ────────────────────────────────────────────────────────

    private static boolean matchesInsightsPath(String path) {
        for (String pattern : INSIGHTS_PATHS) {
            if (path.contains(pattern)) return true;
        }
        return false;
    }

    // ─── JSON rewriting ───────────────────────────────────────────────────────

    private static String rewrite(String path, String json) {
        // Extract media_id from path if available (e.g. /api/v1/media/12345678/insights/)
        String mediaId = extractMediaId(path, json);

        if (mediaId != null && InsightsConfig.hasOverride(mediaId)) {
            json = applyPerMediaOverrides(mediaId, json);
        }

        // For dashboard/aggregates, apply dashboard-level overrides
        if (path.contains("professional_dashboard") || path.contains("account_insights")) {
            json = applyDashboardOverrides(json);
        }

        return json;
    }

    // ─── Per-media override application ──────────────────────────────────────

    private static String applyPerMediaOverrides(String mediaId, String json) {
        for (String metric : InsightsConfig.ALL_METRICS) {
            long override = InsightsConfig.getOverride(mediaId, metric);
            if (override >= 0) {
                json = replaceJsonLongValue(json, metric, override);
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

    // ─── Dashboard aggregate override ────────────────────────────────────────

    private static String applyDashboardOverrides(String json) {
        // Dashboard-level metric field names (may differ from per-media names)
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

    // ─── Core JSON field replacement ─────────────────────────────────────────

    /**
     * Replaces the first occurrence of "key": <number> with "key": <newValue>.
     * Works for both integer and float representations (replaces with integer).
     * Handles whitespace variants: "key" : 123, "key":123, etc.
     *
     * This simple approach works because Instagram's insight responses use
     * consistent field names and numeric values at top level and in nested objects.
     * We call it once per field; if the same field appears multiple times in the
     * JSON (e.g. in both a summary and a time-series entry) ALL occurrences are
     * replaced — which is the correct behaviour for a consistent override.
     */
    static String replaceJsonLongValue(String json, String fieldName, long newValue) {
        if (json == null || fieldName == null) return json;

        // Pattern: "fieldName" followed by optional whitespace, colon, optional
        // whitespace, then a numeric value (integer or float, possibly negative).
        // We replace the entire number token.
        String quotedKey = "\"" + fieldName + "\"";
        int pos = 0;
        StringBuilder sb = new StringBuilder(json.length());

        while (pos < json.length()) {
            int keyStart = json.indexOf(quotedKey, pos);
            if (keyStart < 0) {
                sb.append(json, pos, json.length());
                break;
            }

            // Find the colon after the key
            int colonPos = skipWhitespace(json, keyStart + quotedKey.length());
            if (colonPos >= json.length() || json.charAt(colonPos) != ':') {
                sb.append(json, pos, keyStart + quotedKey.length());
                pos = keyStart + quotedKey.length();
                continue;
            }

            // Find the start of the numeric value (skip whitespace after colon)
            int valueStart = skipWhitespace(json, colonPos + 1);
            if (valueStart >= json.length()) {
                sb.append(json, pos, json.length());
                break;
            }

            char firstChar = json.charAt(valueStart);
            if (firstChar != '-' && !Character.isDigit(firstChar)) {
                // Value is not numeric (e.g. string, boolean) — skip this occurrence
                sb.append(json, pos, valueStart + 1);
                pos = valueStart + 1;
                continue;
            }

            // Find end of the numeric value
            int valueEnd = valueStart;
            if (json.charAt(valueEnd) == '-') valueEnd++;
            while (valueEnd < json.length() &&
                   (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.')) {
                valueEnd++;
            }

            // Append everything up to the number, then the new value
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

    // ─── Country distribution rewriting ──────────────────────────────────────

    /**
     * Rewrites the country distribution section of an insights response.
     *
     * Instagram represents country breakdown as a list of objects:
     * "country_code": "US", "value": 45  (where value is a percentage or count)
     *
     * We replace these values proportionally based on the stored distribution.
     */
    private static String rewriteCountryDistribution(String json, Map<String, Integer> dist) {
        if (dist == null || dist.isEmpty()) return json;

        // Find the total reach or view count to compute absolute values from %
        // If not found, use 10000 as a base (produces realistic-looking numbers)
        long baseCount = extractLongValue(json, InsightsConfig.REACH);
        if (baseCount <= 0) baseCount = extractLongValue(json, InsightsConfig.VIEWS);
        if (baseCount <= 0) baseCount = 10000L;

        // Replace each country's value
        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            String cc = entry.getKey().toUpperCase();
            int pct = entry.getValue();
            long absoluteValue = (baseCount * pct) / 100L;

            // Find the country block containing this country code and replace its value
            json = rewriteCountryValue(json, cc, absoluteValue, pct);
        }
        return json;
    }

    /**
     * Find a country entry by its code and rewrite the associated value fields.
     * Handles both {"country_code":"US","value":123} and {"name":"US","count":123} forms.
     */
    private static String rewriteCountryValue(String json, String countryCode, long absValue, int pctValue) {
        String searchKey = "\"" + countryCode + "\"";
        int pos = json.indexOf(searchKey);
        if (pos < 0) return json;

        // Find the next "value" or "count" field within ~200 chars after the country code
        int searchEnd = Math.min(pos + 200, json.length());
        String region = json.substring(pos, searchEnd);

        // Replace value
        String modified = replaceJsonLongValue(region, "value", absValue);
        modified = replaceJsonLongValue(modified, "count", absValue);
        modified = replaceJsonLongValue(modified, "percentage", pctValue);

        return json.substring(0, pos) + modified + json.substring(pos + region.length());
    }

    // ─── Traffic source rewriting ─────────────────────────────────────────────

    private static String rewriteTrafficSources(String json, Map<String, Integer> sources) {
        if (sources == null || sources.isEmpty()) return json;

        long baseViews = extractLongValue(json, InsightsConfig.VIEWS);
        if (baseViews <= 0) baseViews = extractLongValue(json, InsightsConfig.REACH);
        if (baseViews <= 0) baseViews = 10000L;

        // Traffic source names as they appear in Instagram's JSON
        String[] sourceAliases = { "profile", "home", "hashtag", "explore", "other",
                                   "PROFILE", "HOME", "HASHTAG", "EXPLORE", "OTHER" };

        for (Map.Entry<String, Integer> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            int pct = entry.getValue();
            long absValue = (baseViews * pct) / 100L;

            // Find and replace source entry
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

    // ─── Media ID extraction ─────────────────────────────────────────────────

    /**
     * Extracts a media ID from the request path (e.g. /api/v1/media/12345678/insights/)
     * or from the response JSON body (the "id" or "media_id" field).
     */
    private static String extractMediaId(String path, String json) {
        // Try path first: /media/{id}/  or  /clips/{id}/
        if (path != null) {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (("media".equals(part) || "clips".equals(part)) && i + 1 < parts.length) {
                    String candidate = parts[i + 1];
                    if (candidate.matches("[0-9_]+") && candidate.length() > 5) {
                        return candidate;
                    }
                }
            }
        }

        // Try JSON body: "media_id": "12345" or "id": "12345"
        long fromJson = extractLongValue(json, "media_id");
        if (fromJson > 0) return String.valueOf(fromJson);

        // "id" field — only if it looks like a media ID (long number)
        fromJson = extractLongValue(json, "pk");
        if (fromJson > 0) return String.valueOf(fromJson);

        return null;
    }

    /**
     * Extracts a long value for the given field name from a JSON string.
     * Returns -1 if not found or not a number.
     */
    static long extractLongValue(String json, String fieldName) {
        if (json == null || fieldName == null) return -1L;
        String quotedKey = "\"" + fieldName + "\"";
        int keyPos = json.indexOf(quotedKey);
        if (keyPos < 0) return -1L;

        int colonPos = skipWhitespace(json, keyPos + quotedKey.length());
        if (colonPos >= json.length() || json.charAt(colonPos) != ':') return -1L;

        int valueStart = skipWhitespace(json, colonPos + 1);
        if (valueStart >= json.length()) return -1L;

        // Handle quoted numbers: "12345"
        char fc = json.charAt(valueStart);
        if (fc == '"') {
            int end = json.indexOf('"', valueStart + 1);
            if (end < 0) return -1L;
            try {
                return Long.parseLong(json.substring(valueStart + 1, end));
            } catch (NumberFormatException e) { return -1L; }
        }

        // Unquoted numbers
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
