package com.applyflow.mail.classifier;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a job title from subject/body text. Returns null rather than guessing badly. */
public final class TitleExtractor {

    /** Words that make a phrase look like a job title. */
    static final Pattern ROLE_WORD = Pattern.compile(
            "(?i)\\b(engineer|engineering|developer|manager|analyst|designer|scientist|intern|internship|consultant|"
                    + "architect|specialist|associate|administrator|director|programmer|tester|qa|sdet|officer|"
                    + "executive|coordinator|representative|accountant|writer|researcher|technician|sde|sre|devops|"
                    + "lead|head|vp|principal|staff|trainee|fellow|strategist|recruiter|marketer|advisor|"
                    + "partner|owner|graduate|apprentice|counsel|editor|operator|planner|controller|auditor|"
                    + "economist|statistician|support|agent|member of technical staff|mts|swe|ml|ai|data|"
                    + "product|backend|frontend|full[- ]?stack|java|python|cloud|security|mobile|ios|android)\\b");

    private static final String END = "(?=\\s+(?:at|with|in|has|have|was|is|on|from|and we|-|–|\\|)\\s|\\s*[(\\[,.!?;:\\n|]|\\s*$)";
    private static final String T = "([A-Za-z0-9][A-Za-z0-9 /&+#.,'()\\-]{1,90}?)";

    private static final List<Pattern> PATTERNS = List.of(
            // LinkedIn "Your application was sent to X" puts the title on the next non-empty line.
            Pattern.compile("(?i)application was sent to[^\\n]*\\n\\s*(?:\\n\\s*)?([^\\n]{2,90})"),
            Pattern.compile("(?i)(?:for|about|regarding) (?:the|our|your) " + T
                    + "\\s+(?:role|position|opening|job|opportunity|vacancy|req)\\b"),
            Pattern.compile("(?i)application for (?:the )?(?:position of |role of |post of |job of )?(?:an? )?" + T + END),
            Pattern.compile("(?i)applying (?:for|to) (?:the )?(?:position of |role of )?(?:an? )?" + T
                    + "(?:\\s+(?:role|position|job|opening|opportunity))"),
            Pattern.compile("(?i)(?:position|role|job title|job|title|opening|requisition title|vacancy)\\s*:\\s*"
                    + "([^\\n|]{2,90}?)" + "(?=\\s*(?:\\n|\\||\\(|$|\\s{2,}|\\s+-\\s|,\\s*(?:location|req)))"),
            Pattern.compile("(?i)interview (?:invitation )?(?:for|-|:) (?:the )?(?:position of |role of )?(?:an? )?" + T + END),
            Pattern.compile("(?i)(?:offer|assessment|test|challenge) for (?:the )?(?:position of |role of )?(?:an? )?" + T + END),
            Pattern.compile("(?i)(?:position|role|post) of (?:an? )?" + T + END),
            Pattern.compile("(?i)\\bas (?:an? |our (?:new )?)" + T + "(?=\\s+(?:at|with|on|in)\\s|\\s*[,.!])"),
            Pattern.compile("(?i)(?:your application|you applied) (?:for|to) (?:the )?" + T + END),
            Pattern.compile("(?i)(?:the|our) " + T + "\\s+(?:role|position|opening)\\b"));

    /** Subject-only patterns that may legitimately contain " - " inside the title. */
    private static final List<Pattern> SUBJECT_PATTERNS = List.of(
            Pattern.compile("(?i)^(?:thank(?:s| you) for (?:applying|your application)|application (?:received|"
                    + "submitted|confirmation))\\s*(?:to|for|:|-)\\s*(?:the\\s+)?(.{2,90}?)(?:\\s+(?:position|role))?"
                    + "(?:\\s*[-–]\\s*\\d{5,})?\\s*[!.]?$"),
            Pattern.compile("(?i)\\bfor (?:the )?((?:(?!\\bfor\\b).){2,90}?)(?:\\s*[-–]\\s*\\d{5,})?\\s+"
                    + "(?:position|role|opening)\\b"));

    private static final Pattern SEGMENT_SPLIT = Pattern.compile("\\s*(?::|\\s-\\s|\\s–\\s|\\s\\|\\s)\\s*");
    private static final Pattern SUBJECT_NOISE = Pattern.compile(
            "(?i)^(?:(?:re|fwd?|fw|aw|wg)\\s*:\\s*)+");
    private static final Pattern LEADING_NOISE = Pattern.compile(
            "(?i)^(?:the|a|an|our|your|new|open|exciting|current|following|position of|role of|application for|"
                    + "application to|applying for|interest in|candidacy for)\\s+");
    private static final Pattern TRAILING_NOISE = Pattern.compile(
            "(?i)\\s+(?:role|position|job|opening|opportunity|vacancy|application|interview|req|requisition|"
                    + "team|at|with|in|for|and)$");
    private static final Pattern BAD_CONTENT = Pattern.compile(
            "(?i)\\b(your|our|we|you|us|thank|thanks|unfortunately|received|invite|invitation|update|next steps|"
                    + "regarding|confirmation|congratulations|reminder|schedule|scheduled|status|apply|applied|"
                    + "applying|please|click|here|link|this|that|these|which|who|will|would|can|could)\\b");

    private TitleExtractor() {
    }

    public static String extract(String subject, String body, String companyName) {
        String s = subject == null ? "" : SUBJECT_NOISE.matcher(subject.trim()).replaceAll("");
        for (Pattern p : SUBJECT_PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                String c = clean(m.group(1), companyName);
                if (c != null) {
                    return c;
                }
            }
        }
        String fromSubject = byPatterns(s, companyName);
        if (fromSubject != null) {
            return fromSubject;
        }
        String fromSegments = bySubjectSegments(s, companyName);
        if (fromSegments != null) {
            return fromSegments;
        }
        if (body != null && !body.isBlank()) {
            String head = body.length() > 5000 ? body.substring(0, 5000) : body;
            String fromBody = byPatterns(head, companyName);
            // HTML-to-text often wraps a title across lines ("the Engineer, Software\nEngineering position").
            return fromBody != null ? fromBody : byPatterns(head.replaceAll("\\s+", " "), companyName);
        }
        return null;
    }

    private static String byPatterns(String text, String companyName) {
        if (text.isBlank()) {
            return null;
        }
        String t = text.replace('’', '\'');
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                String c = clean(m.group(1), companyName);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    /** "Interview Invitation: Software Engineer at Stripe" / "Application Received - Data Analyst (R-123)". */
    private static String bySubjectSegments(String subject, String companyName) {
        if (subject.isBlank()) {
            return null;
        }
        for (String seg : SEGMENT_SPLIT.split(subject)) {
            String candidate = seg.replaceAll("(?i)\\s+(?:at|with|@)\\s+.*$", "").trim();
            // "phone screen for Backend Engineer" → "Backend Engineer"
            candidate = candidate.replaceFirst("(?i)^.*?\\b(?:for|as)\\s+(?:the\\s+|an?\\s+)?", "").trim();
            String c = clean(candidate, companyName);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    static String clean(String raw, String companyName) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim();
        c = c.replaceAll("\\s*\\((?:[A-Z]{1,4}[- ]?)?\\d{3,}[^)]*\\)", ""); // "(R-12345)"
        c = c.replaceAll("\\s*\\([A-Za-z]{1,6}[_-]?\\d{1,6}\\)", ""); // "(Des_961)", "(ITG1)"
        c = c.replaceAll("\\s*\\((?i:remote|hybrid|on-?site|full[- ]time|part[- ]time|contract)\\)", "");
        c = c.replaceAll("[\"'“”]", "").trim();
        c = c.replaceAll("\\s*[-–]\\s*\\d{5,}$", "").trim(); // "Apprentice - 26009713"
        // "recent application to the Engineer, Software Engineering" → "Engineer, Software Engineering"
        c = c.replaceFirst("(?i)^.*?\\b(?:application|applying|applied)\\s+(?:to|for)\\s+(?:the\\s+|an?\\s+)?", "");
        c = c.replaceAll("[.,;:!?\\-]+$", "").trim();
        String prev;
        do {
            prev = c;
            c = LEADING_NOISE.matcher(c).replaceAll("").trim();
            // "EY and the Java Developer" → "Java Developer"
            c = c.replaceFirst("(?i)^\\S+(?:\\s\\S+)?\\s+and the\\s+", "");
            c = TRAILING_NOISE.matcher(c).replaceAll("").trim();
        } while (!c.equals(prev));
        if (c.length() < 2 || c.length() > 80) {
            return null;
        }
        String[] words = c.split("\\s+");
        if (words.length > 9) {
            return null;
        }
        if (!ROLE_WORD.matcher(c).find() || BAD_CONTENT.matcher(c).find()) {
            return null;
        }
        if (companyName != null && CompanyNames.normalize(c).equals(CompanyNames.normalize(companyName))) {
            return null;
        }
        if (c.equals(c.toLowerCase(Locale.ROOT))) {
            c = titleCase(c);
        }
        return c;
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (w.length() <= 3 && w.matches("(?i)sde|sre|qa|ml|ai|ios|vp|ui|ux|api")) {
                sb.append(w.toUpperCase(Locale.ROOT));
            } else if (w.equals("of") || w.equals("and") || w.equals("the")) {
                sb.append(w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    /** Normalized title used for matching: lowercase tokens without seniority/noise words. */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String t = title.toLowerCase(Locale.ROOT);
        t = t.replaceAll("^(?:(?:re|fwd?|fw)\\s*:\\s*)+", "");
        t = t.replaceAll("\\([^)]*\\)", " ");
        t = t.replaceAll("[^a-z0-9+# ]", " ");
        t = t.replaceAll("\\b(senior|sr|junior|jr|mid|level|entry|associate|lead|staff|principal|i|ii|iii|iv|v|"
                + "l\\d|sde\\d|the|a|an|of|and|for|position|role|opening|job|remote|hybrid|onsite|full|time|"
                + "part|contract|intern|internship|new|grad|graduate|experienced|team)\\b", " ");
        t = t.replaceAll("\\bsde\\b", "software development engineer");
        t = t.replaceAll("\\bswe\\b", "software engineer");
        t = t.replaceAll("\\bsre\\b", "site reliability engineer");
        t = t.replaceAll("\\bdev\\b", "developer");
        return t.replaceAll("\\s+", " ").trim();
    }
}
