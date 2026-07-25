package com.feurstagram.extension;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;

/**
 * Text-video renderer ported from BatmanJaat.
 * Renders video frames as ASCII / glyph text art.
 */
public final class TextVideoRenderer {

    private final String densityChars = "@#%*+=-:. ";
    private Bitmap[] glyphBitmaps;
    private float cachedTextSize = 0f;

    private void initGlyphs(float textSize) {
        if (glyphBitmaps != null && cachedTextSize == textSize) {
            return;
        }
        if (glyphBitmaps != null) {
            for (Bitmap b : glyphBitmaps) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
        }
        Paint paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);

        int charW = Math.max((int) paint.measureText("A"), 1);
        Paint.FontMetrics fm = paint.getFontMetrics();
        int charH = Math.max((int) (fm.descent - fm.ascent), 1);
        float baseline = -fm.ascent;

        glyphBitmaps = new Bitmap[densityChars.length()];
        for (int i = 0; i < densityChars.length(); i++) {
            Bitmap bmp = Bitmap.createBitmap(charW, charH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawText(String.valueOf(densityChars.charAt(i)), 0f, baseline, paint);
            glyphBitmaps[i] = bmp;
        }
        cachedTextSize = textSize;
    }

    public Bitmap renderFrame(Bitmap src, int cols, float textSize, String colorMode, float hueShift) {
        if (src == null) return null;
        initGlyphs(textSize);

        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int charW = glyphBitmaps[0].getWidth();
        int charH = glyphBitmaps[0].getHeight();

        int finalCols = Math.max(cols, 1);
        int rows = Math.max((int) (((float) srcH / srcW) * finalCols * ((float) charW / charH)), 1);

        Bitmap scaled = Bitmap.createScaledBitmap(src, finalCols, rows, true);
        Bitmap outBmp = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outBmp);
        canvas.drawColor(Color.BLACK);

        float cellW = (float) srcW / finalCols;
        float cellH = (float) srcH / rows;

        Paint paint = new Paint();
        paint.setFilterBitmap(true);

        int[] pixels = new int[finalCols * rows];
        scaled.getPixels(pixels, 0, finalCols, 0, 0, finalCols, rows);
        scaled.recycle();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < finalCols; c++) {
                int color = pixels[r * finalCols + c];
                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;

                int lum = (int) (0.299f * red + 0.587f * green + 0.114f * blue);
                int glyphIdx = (lum * (densityChars.length() - 1)) / 255;

                Bitmap glyph = glyphBitmaps[glyphIdx];
                float x = c * cellW;
                float y = r * cellH;

                if ("COLOR".equalsIgnoreCase(colorMode)) {
                    paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                } else {
                    paint.setColorFilter(null);
                }

                canvas.drawBitmap(glyph, null, new android.graphics.RectF(x, y, x + cellW, y + cellH), paint);
            }
        }
        return outBmp;
    }
}
