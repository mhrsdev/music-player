package com.mhrsdev.musicplayer.ui.update;

import java.util.regex.Pattern;

/** Release notes are GitHub markdown; the screen shows them as plain text with the markup stripped. */
class NotesText {
    private static final int MAX_CHARS = 4000;
    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern BULLET = Pattern.compile("(?m)^([ \\t]*)[-*+][ \\t]+");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|`)");
    private static final Pattern BLANKS = Pattern.compile("\\n{3,}");

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
