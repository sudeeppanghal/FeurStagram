package com.feurstagram.extension;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen Insights Editor dialog.
 *
 * Opened from the Feurstagram Settings page. Lets the user:
 *  - Enable/disable the insights override system globally
 *  - Add overrides by Media ID (from a reel/post URL or directly)
 *  - Edit per-media metrics (views, likes, reach, etc.)
 *  - Edit country distribution and traffic sources
 *  - Set dashboard-level aggregate overrides
 *
 * Uses the same dark Material palette as Settings.java.
 * All UI is built in code — no bundled resources needed.
 */
public final class InsightsEditorDialog {

    // Re-use palette from Settings
    private static final int SURFACE          = Settings.SURFACE;
    private static final int SURFACE_CONTAINER = Settings.SURFACE_CONTAINER;
    private static final int ON_SURFACE       = Settings.ON_SURFACE;
    private static final int ON_SURFACE_VARIANT = Settings.ON_SURFACE_VARIANT;
    private static final int OUTLINE         = Settings.OUTLINE;
    private static final int DIVIDER         = Settings.DIVIDER;
    private static final int PRIMARY         = Settings.PRIMARY;
    private static final int ON_PRIMARY      = Settings.ON_PRIMARY;
    private static final int RIPPLE          = Settings.RIPPLE;

    // Accent for insights editor (Instagram gradient teal/purple)
    private static final int ACCENT          = 0xFF833AB4; // Instagram purple
    private static final int ACCENT2         = 0xFFE1306C; // Instagram pink-red

    private InsightsEditorDialog() {}

    // ─── Show the main editor ─────────────────────────────────────────────────

    public static void show(Context context) {
        if (context == null) return;
        try {
            Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(buildMainContent(context, dialog));

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(SURFACE));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                window.setDimAmount(0f);
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(SURFACE);
                window.setNavigationBarColor(SURFACE);
            }
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context, "Insights Editor unavailable", Toast.LENGTH_LONG).show();
        }
    }

    // ─── Main screen ──────────────────────────────────────────────────────────

    private static View buildMainContent(Context context, Dialog dialog) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        int top = Settings.statusBarHeight(context) + dp(context, 24);
        root.setPadding(pad, top, pad, pad);

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText("📊 Insights Editor");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        titleView.setTextColor(ON_SURFACE);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(titleView,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Master enable toggle
        Switch masterToggle = new Switch(context);
        masterToggle.setChecked(InsightsConfig.isEnabled());
        masterToggle.setShowText(false);
        masterToggle.setTrackTintList(Settings.buildStateList(PRIMARY, OUTLINE));
        masterToggle.setThumbTintList(Settings.buildStateList(ON_PRIMARY, OUTLINE));
        masterToggle.setOnCheckedChangeListener((btn, checked) -> {
            InsightsConfig.setEnabled(checked);
            Toast.makeText(context,
                    checked ? "Insights override ON — restart app for changes to take effect"
                            : "Insights override OFF — real data will be shown",
                    Toast.LENGTH_LONG).show();
        });
        header.addView(masterToggle);
        root.addView(header);

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText("Override what Instagram shows for your insights locally.\nMeta servers are never updated — only the display changes.");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        subtitle.setTextColor(ON_SURFACE_VARIANT);
        subtitle.setPadding(0, dp(context, 8), 0, dp(context, 4));
        root.addView(subtitle);

        // Add new override button
        Button addBtn = makeGradientButton(context, "+ Add Reel / Post Override");
        addBtn.setOnClickListener(v -> showAddMediaDialog(context, dialog));
        LinearLayout.LayoutParams addLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, dp(context, 16), 0, dp(context, 8));
        root.addView(addBtn, addLp);

        // Dashboard overrides button
        Button dashBtn = Settings.makeButton(context, "📈 Edit Dashboard Totals & Graphs",
                SURFACE_CONTAINER, ON_SURFACE, true);
        dashBtn.setOnClickListener(v -> showDashboardEditor(context, dialog));
        LinearLayout.LayoutParams dashLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        dashLp.setMargins(0, 0, 0, dp(context, 16));
        root.addView(dashBtn, dashLp);

        // Divider
        root.addView(makeSectionLabel(context, "SAVED OVERRIDES"));

        // Scroll list of saved media IDs
        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        buildMediaList(context, dialog, list);
        scroll.addView(list);

        root.addView(scroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom: Close button
        Button close = Settings.makeButton(context, "Close", SURFACE_CONTAINER, ON_SURFACE, true);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, dp(context, 16), 0, 0);
        root.addView(close, closeLp);

        return root;
    }

    // ─── Media list ───────────────────────────────────────────────────────────

    private static void buildMediaList(Context context, Dialog parentDialog, LinearLayout list) {
        list.removeAllViews();
        Set<String> mediaIds = InsightsConfig.getAllMediaIds();

        if (mediaIds.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No overrides yet. Tap '+ Add' above to start.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            empty.setTextColor(ON_SURFACE_VARIANT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(context, 40), 0, dp(context, 40));
            list.addView(empty);
            return;
        }

        for (String mediaId : mediaIds) {
            View card = buildMediaCard(context, parentDialog, list, mediaId);
            list.addView(card);
            list.addView(Settings.makeDivider(context, DIVIDER));
        }
    }

    private static View buildMediaCard(Context context, Dialog parentDialog,
                                       LinearLayout list, String mediaId) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 14));
        row.setBackground(Settings.ripple(RIPPLE,
                Settings.roundedRect(SURFACE_CONTAINER, 12, context)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView idLabel = new TextView(context);
        idLabel.setText("Media ID: " + mediaId);
        idLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        idLabel.setTextColor(ON_SURFACE);
        idLabel.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        texts.addView(idLabel);

        // Show a quick summary of what's overridden
        StringBuilder summary = new StringBuilder();
        for (String metric : InsightsConfig.ALL_METRICS) {
            long val = InsightsConfig.getOverride(mediaId, metric);
            if (val >= 0) {
                if (summary.length() > 0) summary.append("  •  ");
                summary.append(friendlyMetricName(metric)).append(": ").append(formatNumber(val));
            }
        }
        if (summary.length() > 0) {
            TextView summaryView = new TextView(context);
            summaryView.setText(summary.toString());
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            summaryView.setTextColor(ON_SURFACE_VARIANT);
            summaryView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(summaryView);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Edit button
        Button editBtn = Settings.makeButton(context, "Edit", ACCENT, 0xFFFFFFFF, true);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        editBtn.setOnClickListener(v -> showMediaEditor(context, parentDialog, list, mediaId));
        row.addView(editBtn);

        // Delete button
        Button delBtn = Settings.makeButton(context, "✕", 0xFF4D2020, 0xFFFF8A80, true);
        delBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams delLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        delLp.setMargins(dp(context, 6), 0, 0, 0);
        delBtn.setOnClickListener(v -> {
            InsightsConfig.clearOverrides(mediaId);
            buildMediaList(context, parentDialog, list);
            Toast.makeText(context, "Overrides removed for " + mediaId, Toast.LENGTH_SHORT).show();
        });
        row.addView(delBtn, delLp);

        return row;
    }

    // ─── Add new media dialog ─────────────────────────────────────────────────

    private static void showAddMediaDialog(Context context, Dialog parentDialog) {
        Dialog d = new Dialog(context);
        int pad = dp(context, 24);
        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(pad, pad, pad, pad);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Settings.roundedRect(SURFACE_CONTAINER, 24, context));
        card.setPadding(pad, pad, pad, pad);
        frame.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = makeSectionLabel(context, "ADD OVERRIDE");
        title.setPadding(0, 0, 0, dp(context, 12));
        card.addView(title);

        TextView hint = new TextView(context);
        hint.setText("Enter the Media ID or paste a reel/post URL.\n\nTo find a Media ID: open the reel → share → copy link.\nURL format: instagram.com/reel/ABC123/\nThe part after /reel/ is the shortcode — paste the full URL and we'll extract it.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        hint.setTextColor(ON_SURFACE_VARIANT);
        hint.setPadding(0, 0, 0, dp(context, 16));
        card.addView(hint);

        EditText input = new EditText(context);
        input.setHint("Media ID or reel URL");
        input.setHintTextColor(ON_SURFACE_VARIANT);
        input.setTextColor(ON_SURFACE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setBackground(Settings.roundedRect(SURFACE, 12, context));
        input.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        card.addView(input);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(context, 20), 0, 0);

        Button cancel = Settings.makeButton(context, "Cancel", 0, ON_SURFACE_VARIANT, false);
        cancel.setOnClickListener(v -> d.dismiss());
        buttons.addView(cancel);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 8), 1));
        buttons.addView(spacer);

        Button confirm = Settings.makeButton(context, "Continue", ACCENT, 0xFFFFFFFF, true);
        confirm.setOnClickListener(v -> {
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) {
                Toast.makeText(context, "Please enter a Media ID or URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String mediaId = extractMediaIdFromInput(raw);
            if (mediaId == null || mediaId.isEmpty()) {
                Toast.makeText(context, "Could not extract a Media ID from that input", Toast.LENGTH_LONG).show();
                return;
            }
            d.dismiss();
            // Open the per-media editor — this will also save the media ID to the list
            showMediaEditor(context, parentDialog, null, mediaId);
        });
        buttons.addView(confirm);
        card.addView(buttons, btnLp);

        d.setContentView(frame);
        d.setCanceledOnTouchOutside(true);
        styleSmallDialog(d);
        d.show();
    }

    // ─── Per-media editor ─────────────────────────────────────────────────────

    private static void showMediaEditor(Context context, Dialog parentDialog,
                                        LinearLayout listToRefresh, String mediaId) {
        Dialog d = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        int top = Settings.statusBarHeight(context) + dp(context, 16);
        root.setPadding(pad, top, pad, pad);

        // Header
        TextView header = new TextView(context);
        header.setText("Edit Insights — " + mediaId);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        header.setTextColor(ON_SURFACE);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(header);

        TextView sub = new TextView(context);
        sub.setText("Set -1 to keep the real value from Instagram.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setTextColor(ON_SURFACE_VARIANT);
        sub.setPadding(0, dp(context, 4), 0, dp(context, 16));
        root.addView(sub);

        // Metrics section
        root.addView(makeSectionLabel(context, "METRICS"));
        LinearLayout metricsCard = makeCard(context);
        root.addView(metricsCard);

        // Map: metric key → EditText
        Map<String, EditText> metricFields = new LinkedHashMap<>();

        String[][] metricDefs = {
            { InsightsConfig.VIEWS,        "👁  Views (plays)",       "e.g. 1500000" },
            { InsightsConfig.REACH,        "📡  Reach",               "e.g. 850000"  },
            { InsightsConfig.IMPRESSIONS,  "📊  Impressions",         "e.g. 2000000" },
            { InsightsConfig.LIKES,        "❤️  Likes",               "e.g. 45000"   },
            { InsightsConfig.COMMENTS,     "💬  Comments",            "e.g. 3200"    },
            { InsightsConfig.SHARES,       "↗️  Shares",              "e.g. 12000"   },
            { InsightsConfig.SAVES,        "🔖  Saves",               "e.g. 8700"    },
            { InsightsConfig.PLAYS,        "▶️  Plays (total)",        "e.g. 1600000" },
            { InsightsConfig.REPLAYS,      "🔁  Replays",             "e.g. 120000"  },
        };

        for (String[] def : metricDefs) {
            String key = def[0], label = def[1], hint = def[2];
            long current = InsightsConfig.getOverride(mediaId, key);
            EditText et = addMetricRow(context, metricsCard, label, hint,
                    current >= 0 ? String.valueOf(current) : "");
            metricFields.put(key, et);
        }

        // Country distribution section
        root.addView(makeSectionLabel(context, "COUNTRY DISTRIBUTION (%)"));
        root.addView(makeCountryHint(context));
        LinearLayout countriesCard = makeCard(context);
        root.addView(countriesCard);

        Map<String, EditText> countryFields = new LinkedHashMap<>();
        Map<String, Integer> savedCountries = InsightsConfig.getCountryDistribution(mediaId);

        String[][] countriesDef = {
            {"US", "🇺🇸 United States"}, {"IN", "🇮🇳 India"}, {"GB", "🇬🇧 United Kingdom"},
            {"BR", "🇧🇷 Brazil"},        {"MX", "🇲🇽 Mexico"}, {"DE", "🇩🇪 Germany"},
            {"FR", "🇫🇷 France"},        {"ID", "🇮🇩 Indonesia"}, {"JP", "🇯🇵 Japan"},
            {"PK", "🇵🇰 Pakistan"},      {"TR", "🇹🇷 Turkey"},   {"PH", "🇵🇭 Philippines"},
            {"EG", "🇪🇬 Egypt"},         {"SA", "🇸🇦 Saudi Arabia"}, {"OTHER", "🌍 Other"},
        };

        for (String[] cd : countriesDef) {
            String cc = cd[0], label = cd[1];
            int saved = (savedCountries != null && savedCountries.containsKey(cc))
                    ? savedCountries.get(cc) : -1;
            EditText et = addPercentRow(context, countriesCard, label,
                    saved >= 0 ? String.valueOf(saved) : "");
            countryFields.put(cc, et);
        }

        // Traffic sources section
        root.addView(makeSectionLabel(context, "TRAFFIC SOURCES (%)"));
        LinearLayout trafficCard = makeCard(context);
        root.addView(trafficCard);

        Map<String, EditText> trafficFields = new LinkedHashMap<>();
        Map<String, Integer> savedTraffic = InsightsConfig.getTrafficSources(mediaId);

        String[][] trafficDef = {
            {"home",    "🏠 Home Feed"},
            {"profile", "👤 Profile"},
            {"explore", "🔍 Explore"},
            {"hashtag", "#️⃣ Hashtags"},
            {"reels",   "🎬 Reels"},
            {"other",   "📌 Other"},
        };

        for (String[] td : trafficDef) {
            String key = td[0], label = td[1];
            int saved = (savedTraffic != null && savedTraffic.containsKey(key))
                    ? savedTraffic.get(key) : -1;
            EditText et = addPercentRow(context, trafficCard, label,
                    saved >= 0 ? String.valueOf(saved) : "");
            trafficFields.put(key, et);
        }

        scroll.addView(root);

        // Build the full dialog layout with a pinned Save button
        LinearLayout dialogRoot = new LinearLayout(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setBackgroundColor(SURFACE);
        dialogRoot.addView(scroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Save + Back row (pinned at bottom)
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(pad, dp(context, 12), pad, dp(context, 12));
        actions.setBackgroundColor(SURFACE);

        Button back = Settings.makeButton(context, "← Back", SURFACE_CONTAINER, ON_SURFACE, true);
        back.setOnClickListener(v -> d.dismiss());
        actions.addView(back);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 8), 1));
        actions.addView(spacer);

        Button save = Settings.makeButton(context, "Save", ACCENT, 0xFFFFFFFF, true);
        save.setOnClickListener(v -> {
            // Save metric overrides
            for (Map.Entry<String, EditText> e : metricFields.entrySet()) {
                String raw = e.getValue().getText().toString().trim();
                if (raw.isEmpty()) {
                    InsightsConfig.removeOverride(mediaId, e.getKey());
                } else {
                    try {
                        InsightsConfig.setOverride(mediaId, e.getKey(), Long.parseLong(raw));
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Save country distribution
            Map<String, Integer> countries = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> e : countryFields.entrySet()) {
                String raw = e.getValue().getText().toString().trim();
                if (!raw.isEmpty()) {
                    try {
                        countries.put(e.getKey(), Integer.parseInt(raw));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!countries.isEmpty()) InsightsConfig.setCountryDistribution(mediaId, countries);

            // Save traffic sources
            Map<String, Integer> traffic = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> e : trafficFields.entrySet()) {
                String raw = e.getValue().getText().toString().trim();
                if (!raw.isEmpty()) {
                    try {
                        traffic.put(e.getKey(), Integer.parseInt(raw));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!traffic.isEmpty()) InsightsConfig.setTrafficSources(mediaId, traffic);

            Toast.makeText(context, "✓ Saved! Open insights for this reel to see changes.", Toast.LENGTH_LONG).show();

            // Refresh the parent list
            if (listToRefresh != null && parentDialog != null) {
                buildMediaList(context, parentDialog, listToRefresh);
            }
            d.dismiss();
        });
        actions.addView(save);
        dialogRoot.addView(actions);

        d.setContentView(dialogRoot);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(SURFACE));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(SURFACE);
            w.setNavigationBarColor(SURFACE);
        }
        d.show();
    }

    // ─── Dashboard editor ─────────────────────────────────────────────────────

    private static void showDashboardEditor(Context context, Dialog parentDialog) {
        Dialog d = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        int top = Settings.statusBarHeight(context) + dp(context, 16);
        root.setPadding(pad, top, pad, pad);

        TextView header = new TextView(context);
        header.setText("📈 Dashboard Totals");
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        header.setTextColor(ON_SURFACE);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(header);

        TextView sub = new TextView(context);
        sub.setText("Override your Professional Dashboard aggregate numbers.\nThese appear on the main insights overview screen.\nLeave blank to show the real value.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setTextColor(ON_SURFACE_VARIANT);
        sub.setPadding(0, dp(context, 8), 0, dp(context, 16));
        root.addView(sub);

        root.addView(makeSectionLabel(context, "OVERVIEW TOTALS"));
        LinearLayout card = makeCard(context);
        root.addView(card);

        String[][] dashDefs = {
            {"total_impressions",   "📊 Total Impressions"},
            {"total_reach",         "📡 Total Reach"},
            {"accounts_reached",    "👥 Accounts Reached"},
            {"accounts_engaged",    "💬 Accounts Engaged"},
            {"total_video_views",   "👁  Total Video Views"},
            {"total_profile_views", "👤 Profile Views"},
            {"followers_count",     "📌 Followers Count"},
        };

        Map<String, EditText> dashFields = new LinkedHashMap<>();
        for (String[] dd : dashDefs) {
            String key = dd[0], label = dd[1];
            long saved = InsightsConfig.getDashboardOverride(key);
            EditText et = addMetricRow(context, card, label, "e.g. 5000000",
                    saved >= 0 ? String.valueOf(saved) : "");
            dashFields.put(key, et);
        }

        scroll.addView(root);

        LinearLayout dialogRoot = new LinearLayout(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setBackgroundColor(SURFACE);
        dialogRoot.addView(scroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(pad, dp(context, 12), pad, dp(context, 12));
        actions.setBackgroundColor(SURFACE);

        Button back = Settings.makeButton(context, "← Back", SURFACE_CONTAINER, ON_SURFACE, true);
        back.setOnClickListener(v -> d.dismiss());
        actions.addView(back);

        View sp = new View(context);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 8), 1));
        actions.addView(sp);

        Button save = Settings.makeButton(context, "Save", ACCENT, 0xFFFFFFFF, true);
        save.setOnClickListener(v -> {
            for (Map.Entry<String, EditText> e : dashFields.entrySet()) {
                String raw = e.getValue().getText().toString().trim();
                if (!raw.isEmpty()) {
                    try {
                        InsightsConfig.setDashboardOverride(e.getKey(), Long.parseLong(raw));
                    } catch (NumberFormatException ignored) {}
                }
            }
            Toast.makeText(context, "✓ Dashboard overrides saved!", Toast.LENGTH_LONG).show();
            d.dismiss();
        });
        actions.addView(save);
        dialogRoot.addView(actions);

        d.setContentView(dialogRoot);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(SURFACE));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(SURFACE);
            w.setNavigationBarColor(SURFACE);
        }
        d.show();
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private static EditText addMetricRow(Context context, LinearLayout parent,
                                         String label, String hint, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        TextView lv = new TextView(context);
        lv.setText(label);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lv.setTextColor(ON_SURFACE);
        row.addView(lv);

        EditText et = new EditText(context);
        et.setHint(hint);
        et.setHintTextColor(ON_SURFACE_VARIANT & 0x99FFFFFF);
        et.setText(value);
        et.setTextColor(ON_SURFACE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackground(Settings.roundedRect(SURFACE, 8, context));
        et.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        LinearLayout.LayoutParams etLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, dp(context, 4), 0, 0);
        row.addView(et, etLp);

        parent.addView(row);
        parent.addView(Settings.makeDivider(context, DIVIDER));
        return et;
    }

    private static EditText addPercentRow(Context context, LinearLayout parent,
                                          String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        TextView lv = new TextView(context);
        lv.setText(label);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lv.setTextColor(ON_SURFACE);
        row.addView(lv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        EditText et = new EditText(context);
        et.setHint("%");
        et.setHintTextColor(ON_SURFACE_VARIANT);
        et.setText(value);
        et.setTextColor(ON_SURFACE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setGravity(Gravity.CENTER);
        et.setBackground(Settings.roundedRect(SURFACE, 8, context));
        et.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        LinearLayout.LayoutParams etLp =
                new LinearLayout.LayoutParams(dp(context, 60), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(et, etLp);

        TextView pct = new TextView(context);
        pct.setText("%");
        pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        pct.setTextColor(ON_SURFACE_VARIANT);
        pct.setPadding(dp(context, 6), 0, 0, 0);
        row.addView(pct);

        parent.addView(row);
        parent.addView(Settings.makeDivider(context, DIVIDER));
        return et;
    }

    private static TextView makeSectionLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTextColor(ON_SURFACE_VARIANT);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setLetterSpacing(0.1f);
        tv.setPadding(0, dp(context, 16), 0, dp(context, 6));
        return tv;
    }

    private static LinearLayout makeCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Settings.roundedRect(SURFACE_CONTAINER, 16, context));
        card.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return card;
    }

    private static TextView makeCountryHint(Context context) {
        TextView tv = new TextView(context);
        tv.setText("Tip: percentages should add up to 100. Leave blank to keep real data.");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextColor(ON_SURFACE_VARIANT);
        tv.setPadding(0, 0, 0, dp(context, 6));
        return tv;
    }

    private static Button makeGradientButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setMinimumHeight(dp(context, 48));
        btn.setPadding(dp(context, 24), 0, dp(context, 24), 0);

        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ ACCENT, ACCENT2 });
        grad.setCornerRadius(dp(context, 24));
        btn.setBackground(Settings.ripple(0x44FFFFFF, grad));
        return btn;
    }

    private static void styleSmallDialog(Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setDimAmount(0.6f);
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Extracts a media ID from raw user input.
     * Handles:
     *   - Raw numeric ID: "12345678901234567"
     *   - Reel URL: "https://www.instagram.com/reel/ABC123def/"
     *   - Post URL: "https://www.instagram.com/p/ABC123def/"
     * For shortcodes (non-numeric) we store them as-is and match them
     * in the mocker against the "shortcode" JSON field.
     */
    private static String extractMediaIdFromInput(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        raw = raw.trim();

        // Pure numeric ID
        if (raw.matches("[0-9]+")) return raw;

        // URL: extract path component after /reel/ or /p/
        String[] patterns = { "/reel/", "/p/", "/tv/", "/clips/" };
        for (String pat : patterns) {
            int idx = raw.indexOf(pat);
            if (idx >= 0) {
                String after = raw.substring(idx + pat.length());
                int end = after.indexOf('/');
                String code = end >= 0 ? after.substring(0, end) : after;
                code = code.split("\\?")[0].trim(); // strip query params
                if (!code.isEmpty()) return code;
            }
        }

        // Fallback: treat entire input as the ID
        return raw.length() > 3 ? raw : null;
    }

    private static String friendlyMetricName(String metric) {
        switch (metric) {
            case InsightsConfig.VIEWS:       return "Views";
            case InsightsConfig.LIKES:       return "Likes";
            case InsightsConfig.COMMENTS:    return "Comments";
            case InsightsConfig.SHARES:      return "Shares";
            case InsightsConfig.SAVES:       return "Saves";
            case InsightsConfig.REACH:       return "Reach";
            case InsightsConfig.IMPRESSIONS: return "Impressions";
            case InsightsConfig.PLAYS:       return "Plays";
            case InsightsConfig.REPLAYS:     return "Replays";
            default:                          return metric;
        }
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static int dp(Context context, float value) {
        return Settings.dp(context, value);
    }
}
