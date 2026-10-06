package com.applyflow.mail.classifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small regex-based extractors for requisition ids, locations, job URLs, employment type and salary. */
public final class FieldExtractor {

    public record Salary(BigDecimal min, BigDecimal max, String currency) {
    }

    private static final Pattern REF_LABELLED = Pattern.compile(
            "(?i)\\b(?:req(?:uisition)?\\.?\\s*(?:id|#|no\\.?|number)?|job\\s*(?:id|#|ref(?:erence)?|code|"
                    + "number|no\\.?)|reference\\s*(?:id|#|no\\.?|number)?|ref\\.?\\s*(?:#|no\\.?)?|"
                    + "application\\s*(?:id|#|number|no\\.?)|posting\\s*(?:id|#))\\s*[:#]?\\s*"
                    + "(?-i:([A-Z0-9][A-Za-z0-9_-]{3,24}))");
    private static final Pattern REF_STANDALONE = Pattern.compile(
            "\\b(JR\\d{4,}|R-?\\d{4,}|REQ-?\\d{3,}|JOB-?\\d{3,}|REQ\\d{3,})\\b");

    /** "(ID: 10555910)" and "Apprentice - Enterprise Technology Services - 26009713". */
    private static final Pattern REF_PAREN_ID = Pattern.compile("\\((?i:id|job id)\\s*:?\\s*(\\d{5,12})\\)");
    private static final Pattern REF_DASH_NUMBER = Pattern.compile("\\s[-–]\\s(\\d{6,10})(?=\\s|$)");
    /** Short posting codes in brackets after a title: "Software Engineer(Des_961)", "(ITG1)". */
    private static final Pattern REF_PAREN_CODE = Pattern.compile(
            "\\((?=[^)]*\\d)(?=[^)]*[A-Za-z])([A-Za-z][A-Za-z0-9]*(?:[_-][A-Za-z0-9]+)?)\\)");

    private static final Pattern LOCATION_LABEL = Pattern.compile(
            "(?im)^\\s*(?:location|job location|office location|work location|based in)s?\\s*[:\\-]\\s*([^\\n|]{2,80})$");
    /** LinkedIn style "Atlassian · Bengaluru, Karnataka, India". */
    private static final Pattern LINKEDIN_LOCATION = Pattern.compile(
            "(?m)^[^\\n·]{2,60}\\s·\\s([^\\n·(]{2,80}?)(?:\\s*\\(.*\\))?\\s*$");
    private static final Pattern LOCATION_PAREN = Pattern.compile(
            "\\((Remote|Hybrid|On-?site|Remote[^)]{0,40}|[A-Z][a-zA-Z .]+,\\s*[A-Z]{2}(?:\\s*/\\s*Remote)?)\\)");
    private static final Pattern IN_CITY = Pattern.compile(
            "\\b(?:in|at our|based in|located in)\\s+(Seattle|Bangalore|Bengaluru|Hyderabad|Pune|Mumbai|Chennai|"
                    + "Delhi|New Delhi|Gurgaon|Gurugram|Noida|Kolkata|Ahmedabad|London|Dublin|Berlin|Amsterdam|"
                    + "Singapore|Sydney|Toronto|Vancouver|New York|San Francisco|Austin|Boston|Chicago|Los Angeles|"
                    + "Mountain View|Sunnyvale|Redmond|Bellevue|Remote)\\b");

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"')\\]]+");
    private static final List<Pattern> JOB_URL_HOSTS = List.of(
            Pattern.compile("(?i)greenhouse\\.io/.+/jobs/\\d+"),
            Pattern.compile("(?i)jobs\\.lever\\.co/[^/]+/[0-9a-f-]{8,}"),
            Pattern.compile("(?i)myworkdayjobs\\.com/.+/job/"),
            Pattern.compile("(?i)jobs\\.ashbyhq\\.com/[^/]+/[0-9a-f-]{8,}"),
            Pattern.compile("(?i)smartrecruiters\\.com/[^/]+/\\d+"),
            Pattern.compile("(?i)amazon\\.jobs/.+/jobs/\\d+"),
            Pattern.compile("(?i)linkedin\\.com/jobs/view/\\d+"),
            Pattern.compile("(?i)apply\\.workable\\.com/[^/]+/j/"),
            Pattern.compile("(?i)/(?:careers?|jobs?|positions?|openings?|vacancies)/[^\\s?#]*\\d{4,}"));
    /** Logos, stylesheets and other assets (e.g. ".../careers/.../logo-200-height.png") are never postings. */
    private static final Pattern STATIC_ASSET = Pattern.compile(
            "(?i)\\.(?:png|jpe?g|gif|svg|webp|ico|bmp|css|js|woff2?|ttf|pdf)(?:$|[?#])|/content/dam/|/assets?/|/images?/");
    private static final Pattern TRACKING = Pattern.compile("(?i)unsubscribe|/track|click\\.|list-manage|"
            + "preferences|utm_|/open\\?|/o/|mailchimp|sendgrid|safelinks");

    private static final Pattern USD_RANGE = Pattern.compile(
            "\\$\\s?(\\d{2,3}(?:,\\d{3})+|\\d{2,3}(?:\\.\\d)?\\s?[kK])\\s*(?:-|to|–)\\s*\\$?\\s?"
                    + "(\\d{2,3}(?:,\\d{3})+|\\d{2,3}(?:\\.\\d)?\\s?[kK])");
    private static final Pattern EUR_GBP_RANGE = Pattern.compile(
            "([€£])\\s?(\\d{2,3}(?:,\\d{3})+|\\d{2,3}\\s?[kK])\\s*(?:-|to|–)\\s*[€£]?\\s?"
                    + "(\\d{2,3}(?:,\\d{3})+|\\d{2,3}\\s?[kK])");
    private static final Pattern LPA_RANGE = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)?\\s?(\\d{1,3}(?:\\.\\d{1,2})?)\\s*(?:-|to|–)\\s*(\\d{1,3}(?:\\.\\d{1,2})?)\\s*"
                    + "(?:lpa|lakhs?(?: per annum)?|l\\.p\\.a)");
    private static final Pattern LPA_SINGLE = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)\\s?(\\d{1,3}(?:\\.\\d{1,2})?)\\s*(?:lpa|lakhs?(?: per annum)?)"
                    + "|\\b(\\d{1,3}(?:\\.\\d{1,2})?)\\s*lpa\\b");

    private FieldExtractor() {
    }

    public static String applicationRef(String subject, String body) {
        String text = (subject == null ? "" : subject) + "\n" + head(body, 6000);
        Matcher m = REF_LABELLED.matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (v.matches(".*\\d.*")) {
                return v.toUpperCase(Locale.ROOT);
            }
        }
        m = REF_STANDALONE.matcher(text);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        for (Pattern p : List.of(REF_PAREN_ID, REF_DASH_NUMBER)) {
            m = p.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        m = REF_PAREN_CODE.matcher(text);
        while (m.find()) {
            String code = m.group(1);
            if (code.length() >= 3 && code.length() <= 12) {
                return code.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    public static String location(String subject, String body) {
        String b = head(body, 6000);
        Matcher m = LOCATION_LABEL.matcher(b);
        if (m.find()) {
            String loc = m.group(1).trim().replaceAll("[.;]+$", "");
            if (!loc.isBlank() && loc.length() <= 80) {
                return loc;
            }
        }
        m = LINKEDIN_LOCATION.matcher(b);
        if (m.find()) {
            return m.group(1).trim();
        }
        String s = subject == null ? "" : subject;
        m = LOCATION_PAREN.matcher(s);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = IN_CITY.matcher(s + "\n" + head(b, 1500));
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String jobUrl(String body, String html) {
        String text = head(body, 20000) + "\n" + head(html, 40000);
        Matcher m = URL.matcher(text);
        String fallback = null;
        while (m.find()) {
            String url = m.group().replaceAll("[.,;:!>]+$", "").replace("&amp;", "&");
            if (TRACKING.matcher(url).find() || STATIC_ASSET.matcher(url).find()) {
                continue;
            }
            for (Pattern p : JOB_URL_HOSTS) {
                if (p.matcher(url).find()) {
                    return normalizeUrl(url);
                }
            }
            if (fallback == null && url.matches("(?i).*(?:boards\\.greenhouse\\.io|jobs\\.lever\\.co)/.*")) {
                fallback = normalizeUrl(url);
            }
        }
        return fallback;
    }

    /** Drops query/fragment so the same posting compares equal. */
    public static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q > 0 && !u.contains("for=")) {
            u = u.substring(0, q);
        }
        int h = u.indexOf('#');
        if (h > 0) {
            u = u.substring(0, h);
        }
        if (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u.length() > 2000 ? u.substring(0, 2000) : u;
    }

    public static String employmentType(String subject, String body) {
        String t = ((subject == null ? "" : subject) + " " + head(body, 4000)).toLowerCase(Locale.ROOT);
        if (t.matches("(?s).*\\b(internship|intern)\\b.*")) {
            return "Internship";
        }
        if (t.matches("(?s).*\\bfull[- ]time\\b.*")) {
            return "Full-time";
        }
        if (t.matches("(?s).*\\bpart[- ]time\\b.*")) {
            return "Part-time";
        }
        if (t.matches("(?s).*\\b(contract|contractor|c2h|contract-to-hire)\\b.*")) {
            return "Contract";
        }
        if (t.matches("(?s).*\\b(temporary|temp role)\\b.*")) {
            return "Temporary";
        }
        return null;
    }

    public static Salary salary(String body) {
        String t = head(body, 8000);
        Matcher m = USD_RANGE.matcher(t);
        if (m.find()) {
            return new Salary(money(m.group(1)), money(m.group(2)), "USD");
        }
        m = EUR_GBP_RANGE.matcher(t);
        if (m.find()) {
            return new Salary(money(m.group(2)), money(m.group(3)), m.group(1).equals("€") ? "EUR" : "GBP");
        }
        m = LPA_RANGE.matcher(t);
        if (m.find()) {
            return new Salary(lakhs(m.group(1)), lakhs(m.group(2)), "INR");
        }
        m = LPA_SINGLE.matcher(t);
        if (m.find()) {
            String v = m.group(1) != null ? m.group(1) : m.group(2);
            BigDecimal amount = lakhs(v);
            return new Salary(amount, amount, "INR");
        }
        return null;
    }

    private static BigDecimal money(String raw) {
        String r = raw.replace(",", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (r.endsWith("k")) {
            return new BigDecimal(r.substring(0, r.length() - 1)).multiply(BigDecimal.valueOf(1000));
        }
        return new BigDecimal(r);
    }

    private static BigDecimal lakhs(String raw) {
        return new BigDecimal(raw).multiply(BigDecimal.valueOf(100_000));
    }

    private static String head(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
