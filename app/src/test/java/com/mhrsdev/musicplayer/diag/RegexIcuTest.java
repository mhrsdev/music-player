package com.mhrsdev.musicplayer.diag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Android's java.util.regex is ICU, stricter than the JDK the unit tests run on: v0.3.1 crashed at launch on Android 10
 * because {@code \{[^{}]*}} (unescaped closing brace) is a PatternSyntaxException on ICU but fine on the JDK. No JVM
 * test can run ICU, so this scans every regex literal in the app sources against a checker of what ICU rejects, and
 * loads every class that keeps a static Pattern so an initializer failure fails here too.
 */
public class RegexIcuTest {
    private static final Path JAVA = new File("src/main/java").toPath();

    // ---- the checker -------------------------------------------------------------------------------------------

    /** Reasons {@code p} may be rejected (or behave differently) on ICU; empty = fine. */
    static List<String> problems(String p) {
        List<String> out = new ArrayList<>();
        int n = p.length();
        int depth = 0;
        boolean inClass = false;
        for (int i = 0; i < n; i++) {
            char c = p.charAt(i);
            if (c == '\\') {
                if (i + 1 >= n) { out.add("trailing backslash"); break; }
                char d = p.charAt(i + 1);
                if (d == 'p' || d == 'P') {
                    int close = i + 2 < n && p.charAt(i + 2) == '{' ? p.indexOf('}', i) : -1;
                    if (close < 0) { if (i + 2 >= n) out.add("incomplete \\p"); i++; continue; }
                    String name = p.substring(i + 3, close);
                    if (name.startsWith("Is") || name.startsWith("java") || name.contains("=")) {
                        out.add("unsupported property \\" + d + "{" + name + "} (use \\p{L} style)");
                    }
                    i = close;
                    continue;
                }
                if ("hHRVk".indexOf(d) >= 0) out.add("\\" + d + " is not ICU-safe");
                i++;
                continue;
            }
            if (inClass) {
                if (c == '[') out.add("unescaped '[' inside a class at " + i);
                else if (c == ']') inClass = false;
                else if (c == '&' && i + 1 < n && p.charAt(i + 1) == '&') out.add("'&&' in a class at " + i);
                else if (c == '{' || c == '}') out.add("unescaped '" + c + "' inside a class at " + i);
                continue;
            }
            switch (c) {
                case '[' -> {
                    inClass = true;
                    int j = i + 1 < n && p.charAt(i + 1) == '^' ? i + 2 : i + 1;
                    if (j < n && p.charAt(j) == ']') out.add("']' first in a class at " + j);
                }
                case ']' -> out.add("unescaped ']' outside a class at " + i);
                case '}' -> out.add("unescaped '}' at " + i);
                case '{' -> {
                    Matcher m = Pattern.compile("\\{\\d+(,\\d*)?\\}").matcher(p);
                    if (!m.find(i) || m.start() != i) out.add("'{' that is not a quantifier at " + i);
                    else if (i == 0 || "(|".indexOf(p.charAt(i - 1)) >= 0) out.add("quantifier without an atom at " + i);
                    else {
                        i = m.end() - 1;
                        if (i + 1 < n && p.charAt(i + 1) == '+') out.add("possessive quantifier at " + (i + 1));
                    }
                }
                case '*', '+', '?' -> {
                    if (i + 1 < n && p.charAt(i + 1) == '+' && c != '?' || c == '?' && i + 1 < n && p.charAt(i + 1) == '+'
                            && i > 0 && p.charAt(i - 1) != '(') {
                        out.add("possessive quantifier at " + i);
                    }
                }
                case '(' -> {
                    depth++;
                    if (i + 1 < n && p.charAt(i + 1) == '?') {
                        String rest = p.substring(i + 2, Math.min(n, i + 4));
                        if (rest.startsWith(">")) out.add("atomic group at " + i);
                        else if (rest.startsWith("<") && !rest.startsWith("<=") && !rest.startsWith("<!")) out.add("named group at " + i);
                        else if (!(rest.startsWith(":") || rest.startsWith("=") || rest.startsWith("!") || rest.startsWith("<")
                                || Pattern.compile("^[imsx-]+[:)]").matcher(p.substring(i + 2)).find())) {
                            out.add("unsupported group/flag syntax at " + i);
                        }
                        if (p.startsWith("(?<=", i) || p.startsWith("(?<!", i)) {
                            int end = matchingParen(p, i);
                            String body = p.substring(i + 4, end < 0 ? n : end);
                            if (Pattern.compile("(?<!\\\\)[*+]|\\{\\d+,\\}").matcher(body).find()) {
                                out.add("lookbehind of unbounded length at " + i);
                            }
                        }
                    }
                }
                case ')' -> {
                    if (--depth < 0) { out.add("unbalanced ')' at " + i); depth = 0; }
                }
                default -> { }
            }
        }
        if (inClass) out.add("unterminated class");
        if (depth > 0) out.add("unterminated group");
        return out;
    }

    private static int matchingParen(String p, int open) {
        int depth = 0;
        boolean cls = false;
        for (int i = open; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '\\') { i++; continue; }
            if (cls) { if (c == ']') cls = false; continue; }
            if (c == '[') cls = true;
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    // ---- the checker checks itself -----------------------------------------------------------------------------

    @Test public void rejectsTheV031CrashPatternAndItsFamily() {
        String bad = "\\([^()]*\\)|\\[[^\\[\\]]*]|\\{[^{}]*}|【[^】]*】"; // what shipped in v0.3.1
        assertFalse(problems(bad).isEmpty());
        assertFalse(problems("\\{[^\\{\\}]*}").isEmpty());
        assertFalse(problems("a]").isEmpty());
        assertFalse(problems("a}").isEmpty());
        assertFalse(problems("a{x}").isEmpty());
        assertFalse(problems("{3}").isEmpty());
        assertFalse(problems("[a[b]]").isEmpty());
        assertFalse(problems("[a&&b]").isEmpty());
        assertFalse(problems("[{]").isEmpty());
        assertFalse(problems("\\p{IsLatin}").isEmpty());
        assertFalse(problems("\\p{javaLowerCase}").isEmpty());
        assertFalse(problems("(?<=a+)b").isEmpty());
        assertFalse(problems("(?<=a{1,})b").isEmpty());
        assertFalse(problems("a++").isEmpty());
        assertFalse(problems("(?>a)").isEmpty());
        assertFalse(problems("(?<name>a)").isEmpty());
        assertFalse(problems("\\h").isEmpty());
        assertFalse(problems("(a").isEmpty());
    }

    @Test public void acceptsTheFixedPatternAndOrdinaryRegexes() {
        String[] good = {
            "\\([^()]*\\)|\\[[^\\[\\]]*\\]|\\{[^\\{\\}]*\\}|【[^】]*】", "[ \\t]{2,}", "\\n{3,}",
            "(?m)^[ \\t]{0,3}#{1,6}[ \\t]*", "(?s)<!--.*?-->", "\\b[\\p{L}\\p{N}-]+\\.(com|ir)\\b", "(?i)\\s+(x|×)\\s+.*$",
            "^\\s*\\d{1,3}\\s*[-._)\\]]+\\s*", "(?<=a{1,3})b", "(?=.)", "a*?b", "[^>]+", "(\\*\\*|__|`)",
            "\"([^\"]*)\"", "(?:(\\d+):)?(\\d+(?:\\.\\d+)?)", "(^|[\\s(\\[:])@[\\w.]{3,}"};
        for (String g : good) assertEquals(g, new ArrayList<String>(), problems(g));
    }

    // ---- every regex literal in the app ------------------------------------------------------------------------

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(JAVA)) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    /** Java string literal at {@code from} (a quote) -> {value, indexAfter}; null if not a literal. */
    private static Object[] literal(String src, int from) {
        if (from >= src.length() || src.charAt(from) != '"') return null;
        StringBuilder sb = new StringBuilder();
        int i = from + 1;
        for (; i < src.length() && src.charAt(i) != '"'; i++) {
            char c = src.charAt(i);
            if (c != '\\') { sb.append(c); continue; }
            char d = src.charAt(++i);
            switch (d) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> { sb.append((char) Integer.parseInt(src.substring(i + 1, i + 5), 16)); i += 4; }
                default -> sb.append(d); // \\ \" \' and anything the compiler would reject
            }
        }
        return new Object[] {sb.toString(), i + 1};
    }

    /** The concatenated literal argument starting at {@code from}, or null when the argument is not a pure literal. */
    private static String argument(String src, int from) {
        StringBuilder sb = new StringBuilder();
        int i = from;
        while (true) {
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
            Object[] lit = literal(src, i);
            if (lit == null) return null;
            sb.append((String) lit[0]);
            i = (Integer) lit[1];
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
            if (i < src.length() && src.charAt(i) == '+') { i++; continue; }
            return sb.toString();
        }
    }

    private static final Pattern CALL = Pattern.compile(
            "(?:Regexes\\.compile|Pattern\\.compile|Pattern\\.matches|\\.matches|\\.replaceAll|\\.replaceFirst|\\.split)\\(");

    /** file -> regex literals passed to a regex-taking call. */
    private static List<String[]> literals() throws IOException {
        List<String[]> out = new ArrayList<>();
        for (Path p : sources()) {
            String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Matcher m = CALL.matcher(src);
            while (m.find()) {
                String arg = argument(src, m.end());
                if (arg != null) out.add(new String[] {JAVA.relativize(p).toString(), arg});
            }
        }
        return out;
    }

    @Test public void everyRegexInTheAppIsSafeOnAndroidsIcuEngine() throws IOException {
        List<String[]> all = literals();
        assertTrue("scanner found the app's regexes", all.size() > 40);
        List<String> bad = new ArrayList<>();
        for (String[] l : all) {
            List<String> pr = problems(l[1]);
            if (!pr.isEmpty()) bad.add(l[0] + "  \"" + l[1] + "\"  ->  " + pr);
        }
        assertEquals("ICU would reject or mis-handle these (fix them; escape } and ] ): " + bad, new ArrayList<String>(), bad);
    }

    @Test public void staticPatternsGoThroughTheCrashProofHelper() throws IOException {
        List<String> raw = new ArrayList<>();
        for (Path p : sources()) {
            String name = JAVA.relativize(p).toString();
            if (name.endsWith("diag/Regexes.java")) continue;
            String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (Pattern.compile("static[^;=]*\\bPattern\\b[^;=]*=\\s*(java\\.util\\.regex\\.)?Pattern\\.compile\\(").matcher(src).find()) {
                raw.add(name);
            }
        }
        assertEquals("a static Pattern must be built with Regexes.compile (an ICU failure in <clinit> kills the app)",
                new ArrayList<String>(), raw);
    }

    @Test public void everyClassWithAStaticPatternInitialisesCleanly() throws Exception {
        Regexes.FAILURES.set(0);
        TreeSet<String> classes = new TreeSet<>();
        for (Path p : sources()) {
            String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (src.contains("Regexes.compile(") && !p.toString().endsWith("Regexes.java")) {
                classes.add(JAVA.relativize(p).toString().replace(".java", "").replace(File.separatorChar, '.'));
            }
        }
        assertTrue("found the classes that keep static patterns: " + classes, classes.size() >= 8);
        for (String c : classes) Class.forName(c, true, getClass().getClassLoader());
        assertEquals("a static Pattern fell back to never-matching", 0, Regexes.failures());
    }

    @Test public void aBrokenPatternDegradesInsteadOfCrashing() {
        int before = Regexes.failures();
        Pattern p = Regexes.compile("(unclosed");
        assertEquals(before + 1, Regexes.failures());
        assertFalse(p.matcher("(unclosed").find());
        assertFalse(p.matcher("anything at all").find());
        assertTrue(Regexes.compile("a{2}", Pattern.CASE_INSENSITIVE).matcher("AA").matches());
        Regexes.FAILURES.set(before);
    }
}
