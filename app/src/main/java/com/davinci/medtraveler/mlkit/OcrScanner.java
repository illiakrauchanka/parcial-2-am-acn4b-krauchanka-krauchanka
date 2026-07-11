package com.davinci.medtraveler.mlkit;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Wraps ML Kit on-device text recognition and derives a "brand name" candidate from
 *  the raw OCR lines. The candidate heuristic is a pure static function so it can be
 *  unit-tested on the JVM without ML Kit. */
public class OcrScanner {

    public interface ResultCallback {
        void onResult(List<String> lines, String candidate);
        void onError(Exception e);
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    /** Runs on-device OCR over the given image. Callbacks fire on the main thread. */
    public void recognize(Context ctx, Uri imageUri, ResultCallback cb) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(ctx, imageUri);
        } catch (Exception e) {
            main.post(() -> cb.onError(e));
            return;
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(text -> {
                    List<String> lines = flatten(text);
                    String candidate = pickBrandCandidate(lines);
                    main.post(() -> cb.onResult(lines, candidate));
                })
                .addOnFailureListener(e -> main.post(() -> cb.onError(e)));
    }

    private static List<String> flatten(Text text) {
        List<String> out = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks())
            for (Text.Line l : b.getLines()) out.add(l.getText());
        return out;
    }

    /** Best-guess brand name from OCR lines. Boxes print the brand big and usually in
     *  ALL CAPS, so we score: letters only (dosage lines like "200 mg" are skipped),
     *  +length, x2 when the line is fully upper-case. Latin and Cyrillic both count.
     *  Returns null when no line qualifies. */
    public static String pickBrandCandidate(List<String> lines) {
        if (lines == null) return null;
        String best = null;
        int bestScore = 0;
        for (String raw : lines) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;
            int letters = 0, digits = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetter(c)) letters++;
                else if (Character.isDigit(c)) digits++;
            }
            if (letters < 3 || digits > 0) continue;      // dosage / count / expiry lines
            if (s.length() > 20) continue;                // prose line, not a brand
            int score = letters;
            if (s.equals(s.toUpperCase(Locale.ROOT)) && !s.equals(s.toLowerCase(Locale.ROOT)))
                score *= 2;                                // ALL-CAPS brand bonus
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }
}
