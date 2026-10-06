package com.applyflow.mail.classifier;

import com.applyflow.mail.parser.ParsedEmail;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the hiring company from an email. Precedence: recruiting display name, subject/body phrases, ATS URL
 * slugs, ATS display name, Workday tenant, sender domain. Never returns an ATS/job-board/mail-provider name.
 */
public final class CompanyExtractor {

    public record CompanyGuess(String name, String domain, String evidence) {
    }

    /** A capitalised company token sequence (case-sensitive); allows "&", "of", "and" joiners. */
    private static final String TOKEN = "[A-Z0-9](?:[\\w&'\\-]|\\.(?=\\w))*";
    private static final String COMPANY = "(" + TOKEN + "(?:[ ](?:" + TOKEN + "|&|of|and|de)){0,4})";

    private static final List<Pattern> SUBJECT_BODY_PATTERNS = List.of(
            Pattern.compile("(?i:your application was sent to)\\s+" + COMPANY),
            Pattern.compile("(?m)^\\s*(?i:company(?: name)?|employer|organi[sz]ation)\\s*:\\s*" + COMPANY + "\\s*$"),
            Pattern.compile("(?i:thank(?:s| you) for (?:applying|your application|your interest)"
                    + "(?: to| at| with| in)(?: (?:joining|working (?:at|for|with)))?)\\s+" + COMPANY),
            Pattern.compile("(?i:your application (?:to|at|with))\\s+" + COMPANY),
            Pattern.compile("(?i:(?:application|applying) (?:to|at|with))\\s+" + COMPANY),
            Pattern.compile("(?i:(?:position|role|job|opportunity|opening|career|careers|interview|interviewing|"
                    + "internship|team|offer|employment|journey)s? (?:at|with))\\s+" + COMPANY),
            Pattern.compile("(?i:on behalf of)\\s+" + COMPANY),
            Pattern.compile("(?i:interest in (?:joining |working (?:at|for|with) )?)" + COMPANY),
            Pattern.compile("(?i:welcome to|join|joining)\\s+" + COMPANY + "(?i:[!.,]| as | team)"),
            Pattern.compile(COMPANY + "\\s+(?i:talent acquisition|recruiting|recruitment|careers|hiring|university "
                    + "recruiting|campus recruiting|people|hr)(?: (?i:team))"),
            Pattern.compile("(?i:the\\s+)" + COMPANY + "\\s+(?i:team|recruiting team|hiring team|talent team)\\b"),
            Pattern.compile(COMPANY + "\\s+(?i:talent acquisition|recruiting)\\b"),
            Pattern.compile("(?i:from)\\s+" + COMPANY + "(?i: talent| recruiting| careers| hr)\\b"));

    private static final Pattern SUBJECT_AT_END = Pattern.compile("\\s(?i:at|with|@)\\s+" + COMPANY
            + "\\s*(?:\\([^)]*\\))?[!.]?$");

    private static final List<Pattern> URL_SLUGS = List.of(
            Pattern.compile("(?i)(?:boards|job-boards|jobs)\\.greenhouse\\.io/(?:embed/job_app\\?for=)?([a-z0-9][a-z0-9_-]+)"),
            Pattern.compile("(?i)jobs\\.lever\\.co/([a-z0-9][a-z0-9_-]+)"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.wd\\d+\\.myworkdayjobs\\.com"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.(?:wd\\d+\\.)?myworkday\\.com"),
            Pattern.compile("(?i)jobs\\.ashbyhq\\.com/([a-z0-9][a-z0-9_.-]+)"),
            Pattern.compile("(?i)(?:careers|jobs)\\.smartrecruiters\\.com/([a-z0-9][a-z0-9_-]+)"),
            Pattern.compile("(?i)apply\\.workable\\.com/([a-z0-9][a-z0-9_-]+)"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.bamboohr\\.com"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.recruitee\\.com"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.breezy\\.hr"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.teamtailor\\.com"),
            Pattern.compile("(?i)https?://([a-z0-9][a-z0-9-]+)\\.jobs\\.personio\\.(?:de|com)"),
            Pattern.compile("(?i)https?://careers-([a-z0-9][a-z0-9-]+)\\.icims\\.com"));

    private static final Pattern DISPLAY_SUFFIX = Pattern.compile(
            "(?i)^(?:the\\s+)?(.+?)(?:\\s*[-|@,:]\\s*|\\s+via\\s+|\\s+)(?:university|campus |global |technical |"
                    + "tech )?(?:recruiting|recruitment|careers?|jobs|talent acquisition|talent|hiring|hr|"
                    + "people(?: team| ops)?|recruiter|staffing|human resources|candidate experience|"
                    + "early careers|ta)(?:\\s+team)?(?:\\s+\\(.*\\))?$");
    private static final Pattern VIA_ATS = Pattern.compile("(?i)^(.+?)\\s+(?:via|@|-|\\|)\\s+"
            + "(?:greenhouse|lever|workday|smartrecruiters|ashby|icims|jobvite|workable|taleo|bamboohr|recruitee)$");

    private static final Set<String> REJECT_WORDS = Set.of(
            "us", "our", "the", "we", "you", "your", "this", "that", "it", "team", "hiring", "recruiting", "careers",
            "career", "jobs", "job", "linkedin", "indeed", "greenhouse", "lever", "workday", "glassdoor", "naukri",
            "hackerrank", "codility", "codesignal", "hirevue", "karat", "hi", "hello", "dear", "thank", "thanks",
            "unfortunately", "application", "applications", "applicant", "candidate", "position", "role",
            "interview", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "january",
            "february", "march", "april", "may", "june", "july", "august", "september", "october", "november",
            "december", "no", "reply", "noreply", "notifications", "notification", "talent", "recruitment", "hr",
            "people", "a", "an", "all", "my", "his", "her", "their", "re", "fwd", "fw", "update", "next", "steps",
            "regards", "best", "sincerely", "important", "action", "required", "reminder", "invitation", "new",
            "online", "assessment", "test", "offer", "remote", "hybrid", "onsite", "full", "time", "workable",
            "smartrecruiters", "ashby", "jobvite", "icims", "taleo", "please", "congratulations", "welcome",
            "team member", "company", "organization", "organisation", "firm", "group", "opportunity", "today",
            "tomorrow", "yesterday", "week", "day", "i", "me", "am", "is", "are");

    private static final Pattern ROLE_WORD = Pattern.compile(
            "(?i)\\b(engineer|engineering|developer|manager|analyst|designer|scientist|intern|internship|consultant|"
                    + "architect|specialist|associate|administrator|director|programmer|tester|officer|executive|"
                    + "coordinator|representative|accountant|researcher|technician|sde|sre|devops|lead|position|role|"
                    + "application|interview|software|backend|frontend|full stack|fullstack)\\b");

    private CompanyExtractor() {
    }

    public static CompanyGuess extract(ParsedEmail email, SenderProfile sender) {
        String display = email.senderName() == null ? "" : email.senderName().trim().replaceAll("^\"|\"$", "");
        String domain = email.senderDomain();
        String companyDomain = sender.isIntermediary() || sender.kind() == SenderProfile.Kind.NON_JOB_SERVICE
                ? null : CompanyNames.registrableDomain(domain);

        // 1. Recruiting display name ("Amazon Recruiting", "Google Careers", "Stripe via Greenhouse").
        String fromDisplay = fromDisplayName(display);
        if (fromDisplay != null) {
            return new CompanyGuess(fromDisplay, companyDomain, "sender name");
        }
        // 2. Subject phrases ("... at Stripe" at the end of a subject is reliable too).
        String fromSubject = firstPatternMatch(email.subject());
        if (fromSubject == null && email.subject() != null) {
            Matcher at = SUBJECT_AT_END.matcher(email.subject().trim());
            if (at.find()) {
                fromSubject = cleanCandidate(at.group(1));
            }
        }
        if (fromSubject != null) {
            return new CompanyGuess(fromSubject, companyDomain, "subject");
        }
        String body = email.bodyText() == null ? "" : email.bodyText();
        String bodyHead = body.length() > 4000 ? body.substring(0, 4000) : body;
        // 3. ATS URL slugs (body text includes link targets).
        String fromUrl = fromUrlSlug(body + "\n" + (email.bodyHtml() == null ? "" : email.bodyHtml()));
        // 4. Body phrases.
        String fromBody = firstPatternMatch(bodyHead);
        if (fromBody != null && (fromUrl == null || CompanyNames.normalize(fromBody)
                .contains(CompanyNames.normalize(fromUrl)))) {
            return new CompanyGuess(fromBody, companyDomain, "body");
        }
        if (fromUrl != null) {
            return new CompanyGuess(fromUrl, companyDomain, "job link");
        }
        // 5. ATS display name that is not a person ("Stripe" <no-reply@greenhouse.io>).
        if ((sender.kind() == SenderProfile.Kind.ATS || sender.kind() == SenderProfile.Kind.ASSESSMENT_PLATFORM)
                && !display.isEmpty() && SenderAnalyzer.isNoReply(email.senderLocalPart())) {
            String cleaned = cleanCandidate(display);
            if (cleaned != null) {
                return new CompanyGuess(cleaned, null, "ATS sender name");
            }
        }
        // 6. Workday tenant mailbox: "salesforce@myworkday.com".
        if (sender.kind() == SenderProfile.Kind.ATS && domain.endsWith("myworkday.com")
                && !SenderAnalyzer.isNoReply(email.senderLocalPart())) {
            String tenant = CompanyNames.nameFromSlug(email.senderLocalPart().replaceAll("[._]", "-"));
            if (tenant != null) {
                return new CompanyGuess(tenant, null, "Workday tenant");
            }
        }
        // 7. Sender domain (company mail servers only).
        if (companyDomain != null && sender.kind() != SenderProfile.Kind.NON_JOB_SERVICE) {
            // Prefer the sender's own spelling when it is the domain label ("HCA Healthcare" @ hcahealthcare.com).
            String label = CompanyNames.domainLabel(companyDomain);
            if (label != null && !display.isEmpty()
                    && display.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(label.replace("-", ""))) {
                String cleaned = cleanCandidate(display);
                if (cleaned != null) {
                    return new CompanyGuess(cleaned, companyDomain, "sender name");
                }
            }
            String name = CompanyNames.nameFromDomain(companyDomain);
            if (name != null && cleanCandidate(name) != null) {
                return new CompanyGuess(name, companyDomain, "sender domain");
            }
        }
        if (fromBody != null) {
            return new CompanyGuess(fromBody, companyDomain, "body");
        }
        return new CompanyGuess(null, companyDomain, null);
    }

    static String fromDisplayName(String display) {
        if (display == null || display.isBlank()) {
            return null;
        }
        Matcher via = VIA_ATS.matcher(display.trim());
        if (via.matches()) {
            return cleanCandidate(via.group(1));
        }
        Matcher m = DISPLAY_SUFFIX.matcher(display.trim());
        if (m.matches()) {
            return cleanCandidate(m.group(1));
        }
        return null;
    }

    static String firstPatternMatch(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace('’', '\'');
        int best = Integer.MAX_VALUE;
        String bestName = null;
        for (Pattern p : SUBJECT_BODY_PATTERNS) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                String c = cleanCandidate(m.group(1));
                if (c != null) {
                    if (m.start() < best) {
                        best = m.start();
                        bestName = c;
                    }
                    break;
                }
            }
        }
        return bestName;
    }

    static String fromUrlSlug(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Pattern p : URL_SLUGS) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String slug = m.group(1).toLowerCase(Locale.ROOT);
                if (slug.equals("www") || slug.equals("jobs") || slug.equals("careers") || slug.equals("app")) {
                    continue;
                }
                return CompanyNames.nameFromSlug(slug);
            }
        }
        return null;
    }

    /** Trims joiners/punctuation and rejects generic words, role titles and over-long sequences. */
    static String cleanCandidate(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().replaceAll("^[\"'(\\[]+|[\"')\\],.;:!?\\-]+$", "").trim();
        c = c.replaceAll("(?i)\\s+(?:and|of|de|&)$", "").trim();
        c = c.replaceAll("(?i)^(?:the)\\s+", "").trim();
        // Drop trailing generic words ("Stripe Team", "Acme Hiring").
        c = c.replaceAll("(?i)(?:\\s+(?:team|hiring|recruiting|recruitment|careers|jobs|talent|experienced|campus|"
                + "university|early|global|inc|llc|ltd)\\.?)+$", "").trim();
        if (c.length() < 2 || c.length() > 50) {
            return null;
        }
        String[] words = c.split("\\s+");
        if (words.length > 5) {
            return null;
        }
        if (REJECT_WORDS.contains(c.toLowerCase(Locale.ROOT)) || REJECT_WORDS.contains(words[0].toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (ROLE_WORD.matcher(c).find()) {
            return null;
        }
        if (c.contains("@") || c.matches(".*\\d{4,}.*") || !c.matches(".*[A-Za-z].*")) {
            return null;
        }
        return c;
    }
}
