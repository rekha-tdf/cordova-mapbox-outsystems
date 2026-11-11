package com.outsystems.mapbox;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Draws marker pins and loads the images shown inside them. */
final class MarkerIcons {
    // Find (square) and Waypoint (circle) markers share a near-black frame; pinColor overrides it.
    static final int DEFAULT_PIN_COLOR = Color.rgb(31, 31, 31);
    static final int FIND_PIN_COLOR = Color.rgb(31, 31, 31);

    static final int PHOTO_PIN_COLOR = Color.rgb(221, 0, 29);

    interface Loaded {
        void done(Bitmap image);
    }

    private static final int WIDTH = 72;
    private static final int HEIGHT = 96;
    private static final float CENTER_X = WIDTH / 2.0f;
    // Marker head: a 52x52 white card (rounded square for Find, circle for Waypoint) inside
    // a thin black outline that narrows below the card to a rounded tip. The tip ends at
    // HEIGHT - 10, where the classic pin ended, so the bottom anchor and every marker's
    // position on the map are unchanged.
    private static final float HEAD_SIZE = 52.0f;
    private static final float HEAD_Y = 46.0f;
    private static final float OUTLINE = 2.5f;
    private static final float FRAME_INSET = 3.8f;
    private static final float SQUARE_RADIUS = 6.0f;
    private static final float CHIN_HALF_WIDTH = 19.0f;
    private static final float CHIN_BAND = 2.5f;
    private static final float TIP_Y = HEIGHT - 10.0f;
    private static final float TIP_ROUNDING = 2.0f;
    private static final int PLACEHOLDER_COLOR = 0xFFE5E7EB;
    private static final int PLACEHOLDER_GLYPH_COLOR = 0xFF6B7280;
    private static final int CROSSHAIR_COLOR = 0xFFD32F2F;
    private static final int IMAGE_DECODE_SIZE = 128;
    private static final int PHOTO_WIDTH = 52;
    private static final int PHOTO_HEIGHT = 63;
    private static final float PHOTO_CENTER_X = 26.0f;
    private static final float PHOTO_CENTER_Y = 26.0f;
    private static final float PHOTO_RADIUS = 14.5f;
    private static final int PHOTO_PLACEHOLDER_COLOR = 0xFFF3F4F6;
    private static final int TIMEOUT_MS = 10000;

    // Reusing one Bitmap instance per look lets Mapbox register a single style image for it.
    private final LruCache<String, Bitmap> pins = new LruCache<>(64);
    private final LruCache<String, Bitmap> images = new LruCache<>(64);
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private final java.util.Set<HttpURLConnection> connections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long generation;

    synchronized void cancelAll() {
        generation++;
        executor.shutdownNow();
        for (HttpURLConnection connection : connections) connection.disconnect();
        connections.clear();
        executor = Executors.newFixedThreadPool(3);
        pins.evictAll();
        images.evictAll();
    }

    boolean hasImage(String source) {
        return source != null && images.get(source) != null;
    }

    /** Returns the pin for this look, using the cached image when it has loaded. */
    Bitmap pin(int color, boolean isFind, String source, float density) {
        return pin(color, isFind, source, source == null ? null : images.get(source), density);
    }

    Bitmap pin(int color, boolean isFind, String source, Bitmap image, float density) {
        density = validDensity(density);
        String key = density + "|" + color + "|" + isFind + "|" + (image == null ? "" : source);
        Bitmap cached = pins.get(key);
        if (cached == null) {
            cached = draw(color, isFind, image, density);
            pins.put(key, cached);
        }
        return cached;
    }

    /** Loads an allowed image source off the main thread; {@code done} runs on a worker thread. */
    synchronized void load(String source, String hosts, Loaded done) {
        final long requestGeneration = generation;
        executor.execute(() -> {
            Bitmap image = null;
            try {
                byte[] bytes = read(source, hosts, requestGeneration);
                image = bytes == null ? null : decode(bytes);
            } catch (Exception | OutOfMemoryError e) {
                Log.w("MapboxPlugin", "Marker image could not be loaded.");
            }
            synchronized (this) {
                if (requestGeneration != generation) return;
                if (image != null) images.put(source, image);
                done.done(image);
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /** Larger "photo" pin (pinStyle "photo"); shows a grey disc until the image has loaded. */
    Bitmap photoPin(int color, String source, float density) {
        return photoPin(color, source, source == null ? null : images.get(source), density);
    }

    Bitmap photoPin(int color, String source, Bitmap image, float density) {
        density = validDensity(density);
        String key = "photo|" + density + "|" + color + "|" + (image == null ? "" : source);
        Bitmap cached = pins.get(key);
        if (cached == null) {
            cached = drawPhoto(color, image, density);
            pins.put(key, cached);
        }
        return cached;
    }

    private static Bitmap drawPhoto(int pinColor, Bitmap image, float density) {
        // Artwork is authored on a 52x63 grid and rendered at 2x so that iconSize=0.5
        // shows it at 52x63 dp, like the classic pin does with its 72x96 canvas.
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(PHOTO_WIDTH * 2 * density)),
            Math.max(1, Math.round(PHOTO_HEIGHT * 2 * density)), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(density * 2, density * 2);

        // Teardrop: circular head with a concave neck narrowing to a rounded tip.
        Path pinPath = new Path();
        pinPath.moveTo(8.75f, 44.0f);
        pinPath.arcTo(new RectF(1.0f, 1.0f, 51.0f, 51.0f), 133.8f, 272.4f);
        pinPath.cubicTo(36.95f, 50.0f, 29.0f, 53.75f, 29.0f, 60.0f);
        pinPath.cubicTo(29.0f, 62.75f, 23.0f, 62.75f, 23.0f, 60.0f);
        pinPath.cubicTo(23.0f, 53.75f, 15.05f, 50.0f, 8.75f, 44.0f);
        pinPath.close();

        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinPaint.setColor(pinColor);
        canvas.drawPath(pinPath, pinPaint);

        if (image == null) {
            Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            discPaint.setColor(PHOTO_PLACEHOLDER_COLOR);
            canvas.drawCircle(PHOTO_CENTER_X, PHOTO_CENTER_Y, PHOTO_RADIUS, discPaint);
            return bitmap;
        }

        float scale = 2 * PHOTO_RADIUS / Math.min(image.getWidth(), image.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(
            PHOTO_CENTER_X - image.getWidth() * scale / 2,
            PHOTO_CENTER_Y - image.getHeight() * scale / 2
        );
        BitmapShader shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        imagePaint.setShader(shader);
        canvas.drawCircle(PHOTO_CENTER_X, PHOTO_CENTER_Y, PHOTO_RADIUS, imagePaint);
        return bitmap;
    }

    private byte[] read(String source, String hosts, long requestGeneration) throws Exception {
        if (requestGeneration != generation || Thread.currentThread().isInterrupted()) return null;
        if (source.startsWith("data:")) {
            byte[] bytes = Base64.decode(source.substring(source.indexOf(',') + 1), Base64.DEFAULT);
            return bytes.length <= MapboxSecurity.MAX_MARKER_IMAGE_BYTES ? bytes : null;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connections.add(connection);
        try {
            if (requestGeneration != generation || Thread.currentThread().isInterrupted()) return null;
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "image/*");
            // getURL() reflects redirects, so the final host must pass the same policy.
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || !MapboxSecurity.markerImageAllowed(connection.getURL().toString(), hosts)
                    || connection.getContentLength() > MapboxSecurity.MAX_MARKER_IMAGE_BYTES) {
                return null;
            }

            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (requestGeneration != generation || Thread.currentThread().isInterrupted()) return null;
                    if (out.size() + read > MapboxSecurity.MAX_MARKER_IMAGE_BYTES) {
                        return null;
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } finally {
            connections.remove(connection);
            connection.disconnect();
        }
    }

    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= IMAGE_DECODE_SIZE
                && bounds.outHeight / (sampleSize * 2) >= IMAGE_DECODE_SIZE) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static float validDensity(float density) {
        return density > 0 && !Float.isNaN(density) && !Float.isInfinite(density) ? density : 1.0f;
    }

    private static Bitmap draw(int pinColor, boolean isFind, Bitmap image, float density) {
        // Annotation images default to the map's pixel ratio. Supply physical
        // pixels for our logical 72x96 artwork; iconSize=0.5 then yields 36x48 dp.
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(WIDTH * density)),
            Math.max(1, Math.round(HEIGHT * density)), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(density, density);

        // Soft shadow: the outline silhouette, slightly larger, lower and translucent.
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(40, 0, 0, 0));
        canvas.drawPath(outlinePath(isFind, 1.0f, 1.5f), shadowPaint);
        canvas.drawPath(chinPath(isFind, 1.0f, 1.5f), shadowPaint);

        // Black outline around the card, narrowing below it to the tip on the coordinate.
        Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(pinColor);
        canvas.drawPath(outlinePath(isFind, 0.0f, 0.0f), outlinePaint);
        canvas.drawPath(chinPath(isFind, 0.0f, 0.0f), outlinePaint);

        // White card: rounded square for Find markers, circle for Waypoint markers.
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(Color.WHITE);
        canvas.drawPath(headPath(isFind, HEAD_Y, 0.0f), cardPaint);

        Path inner = headPath(isFind, HEAD_Y, FRAME_INSET);
        if (image != null) {
            // Center-crop the image inside the white border.
            float side = HEAD_SIZE - 2 * FRAME_INSET;
            float scale = side / Math.min(image.getWidth(), image.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(
                CENTER_X - image.getWidth() * scale / 2,
                HEAD_Y - image.getHeight() * scale / 2
            );
            BitmapShader shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(matrix);

            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            imagePaint.setShader(shader);
            canvas.drawPath(inner, imagePaint);
            return bitmap;
        }

        Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeCap(Paint.Cap.ROUND);
        if (isFind) {
            // Find without an image (or still loading): grey tile with a magnifying glass.
            Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            placeholderPaint.setColor(PLACEHOLDER_COLOR);
            canvas.drawPath(inner, placeholderPaint);
            glyphPaint.setColor(PLACEHOLDER_GLYPH_COLOR);
            glyphPaint.setStrokeWidth(3.0f);
            canvas.drawCircle(CENTER_X - 2.0f, HEAD_Y - 2.0f, 7.0f, glyphPaint);
            canvas.drawLine(CENTER_X + 3.0f, HEAD_Y + 3.0f, CENTER_X + 9.0f, HEAD_Y + 9.0f, glyphPaint);
        } else {
            // Waypoint without an image (or still loading): red compass crosshair.
            glyphPaint.setColor(CROSSHAIR_COLOR);
            glyphPaint.setStrokeWidth(3.0f);
            canvas.drawCircle(CENTER_X, HEAD_Y, 10.5f, glyphPaint);
            canvas.drawLine(CENTER_X, HEAD_Y - 17.0f, CENTER_X, HEAD_Y - 8.0f, glyphPaint);
            canvas.drawLine(CENTER_X, HEAD_Y + 8.0f, CENTER_X, HEAD_Y + 17.0f, glyphPaint);
            canvas.drawLine(CENTER_X - 17.0f, HEAD_Y, CENTER_X - 8.0f, HEAD_Y, glyphPaint);
            canvas.drawLine(CENTER_X + 8.0f, HEAD_Y, CENTER_X + 17.0f, HEAD_Y, glyphPaint);
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            starPaint.setColor(CROSSHAIR_COLOR);
            Path star = new Path();
            star.moveTo(CENTER_X, HEAD_Y - 8.5f);
            star.lineTo(CENTER_X + 2.0f, HEAD_Y - 2.0f);
            star.lineTo(CENTER_X + 8.5f, HEAD_Y);
            star.lineTo(CENTER_X + 2.0f, HEAD_Y + 2.0f);
            star.lineTo(CENTER_X, HEAD_Y + 8.5f);
            star.lineTo(CENTER_X - 2.0f, HEAD_Y + 2.0f);
            star.lineTo(CENTER_X - 8.5f, HEAD_Y);
            star.lineTo(CENTER_X - 2.0f, HEAD_Y - 2.0f);
            star.close();
            canvas.drawPath(star, starPaint);
        }

        return bitmap;
    }

    /** Rounded square (Find) or circle (Waypoint) centered at centerY; negative inset grows it. */
    private static Path headPath(boolean isFind, float centerY, float inset) {
        Path path = new Path();
        float half = HEAD_SIZE / 2 - inset;
        if (isFind) {
            float radius = Math.max(2.0f, SQUARE_RADIUS - inset * 0.5f);
            path.addRoundRect(new RectF(CENTER_X - half, centerY - half, CENTER_X + half, centerY + half),
                radius, radius, Path.Direction.CW);
        } else {
            path.addCircle(CENTER_X, centerY, half, Path.Direction.CW);
        }
        return path;
    }

    /**
     * Black outline around the card: for Find it extends CHIN_BAND further below the card
     * so the chin starts from a full-width band; for Waypoint it is a slightly larger circle.
     */
    private static Path outlinePath(boolean isFind, float dy, float grow) {
        if (!isFind) return headPath(false, HEAD_Y + dy, -OUTLINE - grow);
        Path path = new Path();
        float half = HEAD_SIZE / 2 + OUTLINE + grow;
        float radius = SQUARE_RADIUS + OUTLINE * 0.5f + grow;
        path.addRoundRect(new RectF(CENTER_X - half, HEAD_Y + dy - half,
            CENTER_X + half, HEAD_Y + dy + half + CHIN_BAND), radius, radius, Path.Direction.CW);
        return path;
    }

    /**
     * Wedge from the outline down to a rounded tip at the coordinate. Square heads get a
     * flat chin; circular heads get tangent sides, making a teardrop.
     */
    private static Path chinPath(boolean isFind, float dy, float grow) {
        float tipY = TIP_Y + dy + grow;
        float sideX;
        float sideY;
        if (isFind) {
            sideX = CHIN_HALF_WIDTH + grow;
            sideY = HEAD_Y + HEAD_SIZE / 2 + OUTLINE + CHIN_BAND + dy;
        } else {
            // Tangent points from the tip to the outlined circle.
            float radius = HEAD_SIZE / 2 + OUTLINE + grow;
            float distance = tipY - (HEAD_Y + dy);
            float cos = radius / distance;
            float sin = (float) Math.sqrt(Math.max(0.0f, 1.0f - cos * cos));
            sideX = radius * sin;
            sideY = HEAD_Y + dy + radius * cos;
        }
        // Start inside the head so the wedge joins it without a seam.
        float topY = HEAD_Y + dy;
        float topX = sideX * (tipY - topY) / (tipY - sideY);
        float shoulder = TIP_ROUNDING / (tipY - sideY) * sideX;
        Path path = new Path();
        path.moveTo(CENTER_X - Math.min(topX, HEAD_SIZE / 2), topY);
        path.lineTo(CENTER_X + Math.min(topX, HEAD_SIZE / 2), topY);
        path.lineTo(CENTER_X + sideX, sideY);
        path.lineTo(CENTER_X + shoulder, tipY - TIP_ROUNDING);
        path.quadTo(CENTER_X, tipY, CENTER_X - shoulder, tipY - TIP_ROUNDING);
        path.lineTo(CENTER_X - sideX, sideY);
        path.close();
        return path;
    }

    /** Parses #RRGGBB or #RRGGBBAA, matching the iOS color option format. */
    static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if ((hex.length() != 6 && hex.length() != 8) || !hex.matches("[0-9A-Fa-f]+")) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                return (int) (0xFF000000L | parsed);
            }
            return (int) (((parsed & 0xFF) << 24) | (parsed >>> 8));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
