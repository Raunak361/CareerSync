package com.example.careerMonitor.monitoringService.scraper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the two stored labels for a job:
 *   type        -> "job" | "internship"                (never null)
 *   experience  -> "fresher" | "1 year" | "2 years" | "3-5 years" | "5+ years" | "6 months" | null
 *
 * Evidence order for experience: (1) value supplied by the source (API field / schema.org),
 * (2) the title, (3) internship => fresher, (4) the description, only counting numbers that sit next to
 * the word "experience" so "founded 10 years ago" is ignored. If nothing is stated, experience stays null.
 */
public final class JobClassifier {

    private JobClassifier() {}

    public static final String TYPE_JOB = "job";
    public static final String TYPE_INTERNSHIP = "internship";
    public static final String FRESHER = "fresher";

    private static final Pattern INTERN_TITLE = Pattern.compile(
            "\\b(interns?|internships?|co-?op|apprentices?|apprenticeships?)\\b|summer (analyst|associate)|\\bstudent trainee\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERN_HINT = Pattern.compile("intern|apprentice|co-?op|trainee program", Pattern.CASE_INSENSITIVE);

    private static final Pattern FRESHER_WORDS = Pattern.compile(
            "\\bfreshers?\\b|entry[- ]level|\\bnew grads?\\b|new graduates?|recent graduates?|graduate (engineer )?trainee|"
                    + "campus (hire|hiring|recruit\\w*)|university (grad\\w*|hire)|\\bno (prior|previous|relevant)? ?experience\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})\\s*(?:-|\u2013|\u2014|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLUS = Pattern.compile("(\\d{1,2})\\s*\\+\\s*(?:years?|yrs?)\\b|(?:minimum(?: of)?|at least|min\\.?)\\s*(\\d{1,2})\\s*(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE = Pattern.compile("\\b(\\d{1,2})\\s*(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHS = Pattern.compile("\\b(\\d{1,2})\\s*months?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPERIENCE_WORD = Pattern.compile("experience|experienced|\\bexp\\b", Pattern.CASE_INSENSITIVE);
    /** Company-history phrasing around a number ("founded 12 years ago", "years old") is not a requirement. */
    private static final Pattern HISTORY_WORDS = Pattern.compile(
            "\\bago\\b|founded|established|\\bhistory\\b|years old|anniversary|\\bsince\\b|over the (past|last)|in business",
            Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------ public API

    /** Fill type (always) and experience (when determinable) on a job, keeping anything already set. */
    public static ScrapedJob complete(ScrapedJob j) {
        String type = classifyType(j.getTitle(), j.getType());
        String exp = normalizeExperience(j.getExperience());

        if (exp == null) exp = fromText(j.getTitle(), false);
        if (exp == null && TYPE_INTERNSHIP.equals(type)) exp = FRESHER;
        if (exp == null && j.getDescription() != null) exp = fromText(j.getDescription(), true);
        return j.withType(type).withExperience(exp);
    }

    /** Title is the strongest signal; otherwise trust the provider's employment-type hint; default "job". */
    public static String classifyType(String title, String hint) {
        if (title != null && INTERN_TITLE.matcher(title).find()) return TYPE_INTERNSHIP;
        if (hint != null) {
            String h = hint.trim();
            if (h.equalsIgnoreCase(TYPE_JOB)) return TYPE_JOB;
            if (h.equalsIgnoreCase(TYPE_INTERNSHIP)) return TYPE_INTERNSHIP;
            if (INTERN_HINT.matcher(h).find()) return TYPE_INTERNSHIP;
        }
        return TYPE_JOB;
    }

    /**
     * Normalises a value that is already an experience statement (provider field, schema.org text, or our own
     * earlier output). Returns null when it says nothing usable ("Associate", "Mid-Senior level", ...).
     */
    public static String normalizeExperience(String hint) {
        if (hint == null) return null;
        String h = hint.trim();
        if (h.isEmpty()) return null;
        String low = h.toLowerCase();
        if (low.equals(FRESHER)) return FRESHER;
        if (low.contains("entry") || low.contains("fresher") || low.contains("intern") || low.contains("no experience")
                || low.contains("graduate") || low.equals("none") || low.contains("no requirement")) return FRESHER;
        Matcher mm = Pattern.compile("^(\\d{1,2})\\s*months?$", Pattern.CASE_INSENSITIVE).matcher(h);
        if (mm.find()) return parse(mm.group(1)) + " months";
        return fromText(h, false);
    }

    /** Public for tests/tools. requireExperienceWord=true applies the "next to the word experience" rule. */
    public static String fromText(String text, boolean requireExperienceWord) {
        if (text == null || text.trim().isEmpty()) return null;

        // earliest acceptable numeric statement wins
        int bestPos = Integer.MAX_VALUE;
        String best = null;

        Matcher m = RANGE.matcher(text);
        while (m.find()) {
            if (!nearExperience(text, m.start(), m.end(), requireExperienceWord)) continue;
            if (m.start() < bestPos) { bestPos = m.start(); best = formatRange(parse(m.group(1)), parse(m.group(2))); }
            break;
        }
        m = PLUS.matcher(text);
        while (m.find()) {
            if (!nearExperience(text, m.start(), m.end(), requireExperienceWord)) continue;
            int n = parse(m.group(1) != null ? m.group(1) : m.group(2));
            if (m.start() < bestPos) { bestPos = m.start(); best = n == 0 ? FRESHER : n + "+ years"; }
            break;
        }
        m = SINGLE.matcher(text);
        while (m.find()) {
            if (!nearExperience(text, m.start(), m.end(), requireExperienceWord)) continue;
            if (m.start() < bestPos) { bestPos = m.start(); best = formatSingle(parse(m.group(1))); }
            break;
        }
        if (best != null) return best;

        if (requireExperienceWord) {
            m = MONTHS.matcher(text);
            while (m.find()) {
                if (nearExperience(text, m.start(), m.end(), true)) return parse(m.group(1)) + " months";
            }
        }
        // no number: explicit fresher wording (title always; description only in its opening part)
        String scope = requireExperienceWord && text.length() > 2500 ? text.substring(0, 2500) : text;
        return FRESHER_WORDS.matcher(scope).find() ? FRESHER : null;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean nearExperience(String text, int start, int end, boolean required) {
        if (!required) return true;
        int nf = Math.max(0, start - 30), nt = Math.min(text.length(), end + 30);
        if (HISTORY_WORDS.matcher(text.substring(nf, nt)).find()) return false;
        int from = Math.max(0, start - 80), to = Math.min(text.length(), end + 100);
        return EXPERIENCE_WORD.matcher(text.substring(from, to)).find();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static String formatSingle(int n) {
        if (n <= 0) return FRESHER;
        return n == 1 ? "1 year" : n + " years";
    }

    private static String formatRange(int a, int b) {
        if (b < a) { int t = a; a = b; b = t; }
        if (a == b) return formatSingle(a);
        if (a == 0 && b <= 1) return FRESHER;
        return a + "-" + b + " years";
    }
}
