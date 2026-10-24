package com.mhrsdev.musicplayer.ui.update;

import com.mhrsdev.musicplayer.diag.Regexes;
import java.util.regex.Pattern;

/** Release notes are GitHub markdown; the screen shows them as plain text with the markup stripped. */
class NotesText {
    private static final int MAX_CHARS = 4000;
    private static final Pattern LINK = Regexes.compile("!?\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern COMMENT = Regexes.compile("(?s)<!--.*?-->");
    private static final Pattern TAG = Regexes.compile("<[^>]+>");
    private static final Pattern HEADING = Regexes.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern BULLET = Regexes.compile("(?m)^([ \\t]*)[-*+][ \\t]+");
    private static final Pattern EMPHASIS = Regexes.compile("(\\*\\*|__|`)");
    private static final Pattern BLANKS = Regexes.compile("\\n{3,}");

    private NotesText() { }

    static String plain(String markdown) {
        String s = markdown.replace("\r\n", "\n");
        s = COMMENT.matcher(s).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = TAG.matcher(s).replaceAll("");
        s = HEADING.matcher(s).replaceAll("");
        s = BULLET.matcher(s).replaceAll("$1• ");
        s = EMPHASIS.matcher(s).replaceAll("");
        s = BLANKS.matcher(s).replaceAll("\n\n").trim();
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) + "…" : s;
    }
}
