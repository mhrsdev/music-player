package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Simple JSON word-sync sidecar ({@code .elrc}/{@code .json}): either {@code {"lines":[...]}} or a bare array of lines.
 * A line is {@code {"time"|"start"|"startMs": ms, "text": "...", "words": [{"start"|"startMs": ms, "end"|"endMs": ms,
 * "text": "..."}]}}; without "text" the words are joined with spaces. Times in milliseconds.
 */
class JsonLyricsParser {
    private JsonLyricsParser() { }

    static boolean looksLikeJson(String raw) {
        int i = 0;
        while (i < raw.length() && (Character.isWhitespace(raw.charAt(i)) || raw.charAt(i) == '﻿')) i++;
        return i < raw.length() && (raw.charAt(i) == '{' || raw.startsWith("[{", i) || raw.startsWith("[ ", i)
                || raw.startsWith("[\n", i) || raw.startsWith("[]", i));
    }

    @Nullable
    static List<LyricLine> parse(String raw) {
        try {
            String s = raw.charAt(0) == '﻿' ? raw.substring(1) : raw;
            Object root = new JSONTokener(s).nextValue();
            JSONArray arr = root instanceof JSONObject o ? o.optJSONArray("lines") : root instanceof JSONArray a ? a : null;
            if (arr == null) return null;
            List<LyricLine> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject l = arr.optJSONObject(i);
                if (l != null) addLine(out, l);
            }
            out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
            return out.isEmpty() ? null : out;
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private static void addLine(List<LyricLine> out, JSONObject l) {
        JSONArray ws = l.optJSONArray("words");
        String given = l.optString("text", "").trim();
        StringBuilder text = new StringBuilder(given);
        List<LyricWord> words = new ArrayList<>();
        int searchFrom = 0;
        if (ws != null) {
            for (int k = 0; k < ws.length(); k++) {
                JSONObject w = ws.optJSONObject(k);
                if (w == null) continue;
                String wt = w.optString("text", "").trim();
                if (wt.isEmpty()) continue;
                long s = ms(w, "startMs", "start", "time");
                long e = ms(w, "endMs", "end", null);
                int at;
                if (given.isEmpty()) {
                    if (text.length() > 0) text.append(' ');
                    at = text.length();
                    text.append(wt);
                } else {
                    at = given.indexOf(wt, searchFrom);
                    if (at < 0) continue; // word not in the line text: skip rather than mis-highlight
                }
                searchFrom = at + wt.length();
                if (s >= 0) words.add(new LyricWord(s, Math.max(s, e >= 0 ? e : s + 400), wt, at, at + wt.length()));
            }
        }
        long time = ms(l, "startMs", "start", "time");
        if (time < 0 && !words.isEmpty()) time = words.get(0).startMs();
        if (text.length() == 0) return;
        out.add(new LyricLine(time < 0 ? LyricLine.UNSYNCED : time, text.toString(), words.isEmpty() ? null : words,
                false));
    }

    private static long ms(JSONObject o, String a, String b, @Nullable String c) {
        for (String k : new String[]{a, b, c}) {
            if (k != null && o.has(k) && !o.isNull(k)) {
                double v = o.optDouble(k, -1);
                if (v >= 0) return Math.round(v);
            }
        }
        return -1;
    }
}
