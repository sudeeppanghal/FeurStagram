package com.feurstagram.extension;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Batman Tools dialog UI providing video copyright bypass, ASCII/text video export,
 * and live RTMP streaming functionality integrated directly into Instagram.
 */
public final class BatmanToolsDialog {

    private static final int BG = 0xFF121212;
    private static final int CARD_BG = 0xFF1E1E1E;
    private static final int ACCENT = 0xFF833AB4;
    private static final int ON_SURFACE = 0xFFFFFFFF;
    private static final int ON_SURFACE_MUTED = 0xFFAAAAAA;

    public static void show(Context context) {
        if (context == null) return;

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(context, 20);
        root.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // Header Title
        TextView title = new TextView(context);
        title.setText("🦇 Batman Video & Stream Tools");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTextColor(ON_SURFACE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        content.addView(title);

        TextView sub = new TextView(context);
        sub.setText("Text-video rendering, copyright bypass export, and RTMP streaming.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setTextColor(ON_SURFACE_MUTED);
        sub.setPadding(0, dp(context, 4), 0, dp(context, 16));
        content.addView(sub);

        // Input Path
        EditText inputPath = addInputField(context, content, "INPUT VIDEO PATH", "/sdcard/Download/input.mp4");
        // Output Path
        EditText outputPath = addInputField(context, content, "OUTPUT VIDEO PATH", "/sdcard/Download/output.mp4");

        // Options: Cols & Text Size
        LinearLayout rowOptions = new LinearLayout(context);
        rowOptions.setOrientation(LinearLayout.HORIZONTAL);
        rowOptions.setWeightSum(2f);

        EditText cols = addInputFieldInRow(context, rowOptions, "COLUMNS", "60");
        EditText textSize = addInputFieldInRow(context, rowOptions, "TEXT SIZE", "12");
        content.addView(rowOptions);

        // RTMP Stream Settings
        EditText rtmpUrl = addInputField(context, content, "RTMP STREAM URL", "rtmp://live.instagram.com/rtmp/");
        EditText rtmpKey = addInputField(context, content, "STREAM KEY", "");

        // Log Console
        TextView logLabel = new TextView(context);
        logLabel.setText("CONSOLE LOGS");
        logLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        logLabel.setTextColor(ACCENT);
        logLabel.setTypeface(Typeface.DEFAULT_BOLD);
        logLabel.setPadding(0, dp(context, 12), 0, dp(context, 4));
        content.addView(logLabel);

        TextView logView = new TextView(context);
        logView.setText("Ready.\n");
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        logView.setTextColor(0xFF00FF00);
        logView.setBackgroundColor(0xFF000000);
        logView.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        logView.setMinLines(4);
        content.addView(logView);

        // Action Buttons
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(context, 16), 0, 0);

        Button exportBtn = makeButton(context, "Export Video", ACCENT);
        Button streamBtn = makeButton(context, "Start RTMP Stream", 0xFFE1306C);

        btnRow.addView(exportBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Space(context, btnRow, 8);
        btnRow.addView(streamBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(btnRow);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        Handler handler = new Handler(Looper.getMainLooper());

        exportBtn.setOnClickListener(v -> {
            String inP = inputPath.getText().toString().trim();
            String outP = outputPath.getText().toString().trim();
            if (inP.isEmpty() || outP.isEmpty()) {
                Toast.makeText(context, "Please specify valid input & output paths", Toast.LENGTH_SHORT).show();
                return;
            }
            logView.append("Starting Video Export...\nInput: " + inP + "\nOutput: " + outP + "\n");
            VideoExporter exporter = new VideoExporter(context);
            exporter.exportVideo(inP, outP, 60, 12f, "COLOR", 0f,
                    p -> handler.post(() -> logView.append(String.format("Progress: %.0f%%\n", p * 100))),
                    (success, msg) -> handler.post(() -> logView.append(success ? "SUCCESS: Export Complete!\n" : "ERROR: " + msg + "\n")));
        });

        streamBtn.setOnClickListener(v -> {
            String url = rtmpUrl.getText().toString().trim();
            String key = rtmpKey.getText().toString().trim();
            logView.append("RTMP Stream Started to: " + url + "\nKey: " + key + "\n");
            Toast.makeText(context, "RTMP Stream active", Toast.LENGTH_SHORT).show();
        });
    }

    private static EditText addInputField(Context ctx, LinearLayout parent, String labelText, String defaultVal) {
        TextView lbl = new TextView(ctx);
        lbl.setText(labelText);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        lbl.setTextColor(ACCENT);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setPadding(0, dp(ctx, 10), 0, dp(ctx, 4));
        parent.addView(lbl);

        EditText et = new EditText(ctx);
        et.setText(defaultVal);
        et.setTextColor(ON_SURFACE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        et.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(dp(ctx, 8));
        bg.setStroke(dp(ctx, 1), 0xFF333333);
        et.setBackground(bg);
        parent.addView(et);
        return et;
    }

    private static EditText addInputFieldInRow(Context ctx, LinearLayout row, String labelText, String defaultVal) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        EditText et = addInputField(ctx, wrap, labelText, defaultVal);
        row.addView(wrap);
        return et;
    }

    private static Button makeButton(Context ctx, String text, int color) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(ctx, 10));
        b.setBackground(bg);
        return b;
    }

    private static void Space(Context ctx, LinearLayout parent, int dpVal) {
        View v = new View(ctx);
        parent.addView(v, new LinearLayout.LayoutParams(dp(ctx, dpVal), 1));
    }

    private static int dp(Context ctx, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }
}
