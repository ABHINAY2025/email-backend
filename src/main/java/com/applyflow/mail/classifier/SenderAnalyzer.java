package com.applyflow.mail.classifier;

import com.applyflow.mail.classifier.SenderProfile.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Classifies a sender address into ATS / job board / assessment platform / non-job service / company. */
public final class SenderAnalyzer {

    /** ATS and career-site domains (suffix match) to display name. */
    static final Map<String, String> ATS_DOMAINS = new LinkedHashMap<>();
    static final Map<String, String> ASSESSMENT_DOMAINS = new LinkedHashMap<>();
    static final Map<String, String> JOB_BOARD_DOMAINS = new LinkedHashMap<>();

    static {
        ATS_DOMAINS.put("greenhouse.io", "Greenhouse");
        ATS_DOMAINS.put("greenhouse-mail.io", "Greenhouse");
        ATS_DOMAINS.put("lever.co", "Lever");
        ATS_DOMAINS.put("myworkday.com", "Workday");
        ATS_DOMAINS.put("myworkdayjobs.com", "Workday");
        ATS_DOMAINS.put("workday.com", "Workday");
        ATS_DOMAINS.put("smartrecruiters.com", "SmartRecruiters");
        ATS_DOMAINS.put("smartrecruiters-mail.com", "SmartRecruiters");
        ATS_DOMAINS.put("icims.com", "iCIMS");
        ATS_DOMAINS.put("jobvite.com", "Jobvite");
        ATS_DOMAINS.put("ashbyhq.com", "Ashby");
        ATS_DOMAINS.put("bamboohr.com", "BambooHR");
        ATS_DOMAINS.put("taleo.net", "Taleo");
        ATS_DOMAINS.put("successfactors.com", "SuccessFactors");
        ATS_DOMAINS.put("successfactors.eu", "SuccessFactors");
        ATS_DOMAINS.put("recruitee.com", "Recruitee");
        ATS_DOMAINS.put("workable.com", "Workable");
        ATS_DOMAINS.put("workablemail.com", "Workable");
        ATS_DOMAINS.put("breezy.hr", "Breezy HR");
        ATS_DOMAINS.put("breezyhr.com", "Breezy HR");
        ATS_DOMAINS.put("jazzhr.com", "JazzHR");
        ATS_DOMAINS.put("applytojob.com", "JazzHR");
        ATS_DOMAINS.put("teamtailor.com", "Teamtailor");
        ATS_DOMAINS.put("teamtailor-mail.com", "Teamtailor");
        ATS_DOMAINS.put("personio.de", "Personio");
        ATS_DOMAINS.put("personio.com", "Personio");
        ATS_DOMAINS.put("avature.net", "Avature");
        ATS_DOMAINS.put("eightfold.ai", "Eightfold");
        ATS_DOMAINS.put("phenompeople.com", "Phenom");
        ATS_DOMAINS.put("freshteam.com", "Freshteam");
        ATS_DOMAINS.put("zohorecruit.com", "Zoho Recruit");
        ATS_DOMAINS.put("darwinbox.in", "Darwinbox");
        ATS_DOMAINS.put("dover.com", "Dover");
        ATS_DOMAINS.put("gem.com", "Gem");
        ATS_DOMAINS.put("amazon.jobs", "Amazon Jobs");
        // Oracle Recruiting Cloud sends on behalf of its customers (JPMorgan, Chubb, ...).
        ATS_DOMAINS.put("cloud.oracle.com", "Oracle Recruiting");
        ATS_DOMAINS.put("oraclecloud.com", "Oracle Recruiting");
        ATS_DOMAINS.put("jobs2web.com", "SuccessFactors");

        ASSESSMENT_DOMAINS.put("hackerrank.com", "HackerRank");
        ASSESSMENT_DOMAINS.put("hackerrankforwork.com", "HackerRank");
        ASSESSMENT_DOMAINS.put("codility.com", "Codility");
        ASSESSMENT_DOMAINS.put("codesignal.com", "CodeSignal");
        ASSESSMENT_DOMAINS.put("karat.com", "Karat");
        ASSESSMENT_DOMAINS.put("karat.io", "Karat");
        ASSESSMENT_DOMAINS.put("hirevue.com", "HireVue");
        ASSESSMENT_DOMAINS.put("testgorilla.com", "TestGorilla");
        ASSESSMENT_DOMAINS.put("mettl.com", "Mercer Mettl");
        ASSESSMENT_DOMAINS.put("hackerearth.com", "HackerEarth");
        ASSESSMENT_DOMAINS.put("imocha.io", "iMocha");
        ASSESSMENT_DOMAINS.put("coderbyte.com", "Coderbyte");

        JOB_BOARD_DOMAINS.put("linkedin.com", "LinkedIn");
        JOB_BOARD_DOMAINS.put("indeed.com", "Indeed");
        JOB_BOARD_DOMAINS.put("indeedemail.com", "Indeed");
        JOB_BOARD_DOMAINS.put("naukri.com", "Naukri");
        JOB_BOARD_DOMAINS.put("glassdoor.com", "Glassdoor");
        JOB_BOARD_DOMAINS.put("wellfound.com", "Wellfound");
        JOB_BOARD_DOMAINS.put("angel.co", "Wellfound");
        JOB_BOARD_DOMAINS.put("dice.com", "Dice");
        JOB_BOARD_DOMAINS.put("ziprecruiter.com", "ZipRecruiter");
        JOB_BOARD_DOMAINS.put("otta.com", "Otta");
        JOB_BOARD_DOMAINS.put("welcometothejungle.com", "Welcome to the Jungle");
        JOB_BOARD_DOMAINS.put("hired.com", "Hired");
        JOB_BOARD_DOMAINS.put("monster.com", "Monster");
        JOB_BOARD_DOMAINS.put("foundit.in", "foundit");
        JOB_BOARD_DOMAINS.put("instahyre.com", "Instahyre");
        JOB_BOARD_DOMAINS.put("hirist.com", "Hirist");
        JOB_BOARD_DOMAINS.put("hirist.tech", "Hirist");
        JOB_BOARD_DOMAINS.put("iimjobs.com", "iimjobs");
        JOB_BOARD_DOMAINS.put("cutshort.io", "Cutshort");
        JOB_BOARD_DOMAINS.put("internshala.com", "Internshala");
        JOB_BOARD_DOMAINS.put("simplyhired.com", "SimplyHired");
    }

    static final Set<String> PERSONAL_PROVIDERS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.in", "ymail.com", "outlook.com", "hotmail.com",
            "live.com", "msn.com", "icloud.com", "me.com", "mac.com", "aol.com", "protonmail.com", "proton.me",
            "zoho.com", "zohomail.com", "gmx.com", "gmx.de", "yandex.com", "rediffmail.com", "mail.com");

    /** Domains whose mail is (almost) never about one of the user's job applications. */
    static final Map<String, String> NON_JOB_DOMAINS = new LinkedHashMap<>();

    static {
        for (String d : List.of("github.com", "gitlab.com", "bitbucket.org", "atlassian.net", "atlassian.com",
                "slack.com", "notion.so", "figma.com", "vercel.com", "netlify.com", "heroku.com", "docker.com",
                "npmjs.com", "sentry.io", "circleci.com", "travis-ci.com", "jetbrains.com", "stackoverflow.email")) {
            NON_JOB_DOMAINS.put(d, "developer tool notification");
        }
        for (String d : List.of("amazonaws.com", "aws.amazon.com", "cloud.google.com", "azure.com",
                "microsoftazure.com", "digitalocean.com", "cloudflare.com", "mongodb.com", "oracle-cloud.com")) {
            NON_JOB_DOMAINS.put(d, "cloud provider notification");
        }
        for (String d : List.of("facebookmail.com", "instagram.com", "twitter.com", "x.com", "pinterest.com",
                "reddit.com", "redditmail.com", "quora.com", "discord.com", "tiktok.com", "snapchat.com",
                "youtube.com", "medium.com", "substack.com", "whatsapp.com")) {
            NON_JOB_DOMAINS.put(d, "social network notification");
        }
        for (String d : List.of("paypal.com", "hdfcbank.net", "hdfcbank.com", "icicibank.com", "axisbank.com",
                "sbi.co.in", "kotak.com", "chase.com", "bankofamerica.com", "wellsfargo.com", "citi.com",
                "americanexpress.com", "paytm.com", "phonepe.com", "razorpay.com", "cred.club", "zerodha.com",
                "groww.in", "venmo.com", "revolut.com", "wise.com")) {
            NON_JOB_DOMAINS.put(d, "banking / payments");
        }
        for (String d : List.of("swiggy.in", "zomato.com", "myntra.com", "ajio.com", "ebay.com", "etsy.com",
                "walmart.com", "target.com", "bestbuy.com", "bigbasket.com", "blinkit.com", "zepto.co",
                "nykaa.com", "doordash.com", "ubereats.com", "booking.com", "airbnb.com", "makemytrip.com",
                "goibibo.com", "irctc.co.in", "spotify.com", "primevideo.com", "hotstar.com")) {
            NON_JOB_DOMAINS.put(d, "shopping / consumer service");
        }
        NON_JOB_DOMAINS.put("accounts.google.com", "account security");
        NON_JOB_DOMAINS.put("accountprotection.microsoft.com", "account security");
    }

    private static final Pattern RECRUITING_LOCAL = Pattern.compile(
            "^(?:no-?reply[-.]?)?(?:careers?|jobs?|recruit(?:ing|ment|er|ers)?|talent(?:acquisition)?|hr|hiring|"
                    + "people|staffing|campus|university|ta|apply|applications?|candidates?)(?:[-.]?(?:no-?reply|team|"
                    + "india|us|global|ops|notifications?))?\\d*$");
    private static final Pattern RECRUITING_DISPLAY = Pattern.compile(
            "(?i)\\b(recruit(?:ing|ment|er)?|careers?|talent|hiring|jobs|hr|human resources|people team|"
                    + "talent acquisition|campus)\\b");
    private static final Pattern NON_JOB_LOCAL = Pattern.compile(
            "^(?:auto-confirm|order(?:s|-update|-updates|-confirmation)?|shipment(?:-tracking)?|shipping|delivery|"
                    + "store-news|deals|offers|promotions?|marketing|newsletters?|news|billing|invoices?|receipts?|"
                    + "statements?|payments?(?:-messages)?|account-security|security(?:-noreply)?|alerts?|"
                    + "digital-no-reply|aws-billing|aws-marketing|notifications?-noreply|updates)$");

    private SenderAnalyzer() {
    }

    public static SenderProfile analyze(String senderEmail, String senderName, String subject) {
        String email = senderEmail == null ? "" : senderEmail.toLowerCase(Locale.ROOT).trim();
        int at = email.lastIndexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        String domain = at < 0 ? "" : email.substring(at + 1);
        String subj = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        String display = senderName == null ? "" : senderName;

        String ats = matchSuffix(domain, ATS_DOMAINS);
        if (ats != null) {
            return new SenderProfile(Kind.ATS, ats + " ATS", 0.45, 0, ats);
        }
        String assessment = matchSuffix(domain, ASSESSMENT_DOMAINS);
        if (assessment != null) {
            if (subj.contains("newsletter") || subj.contains("webinar") || subj.contains("contest")
                    || subj.contains("hackathon") || local.contains("marketing") || local.contains("news")) {
                return new SenderProfile(Kind.NON_JOB_SERVICE, assessment + " marketing", 0, 0.8, null);
            }
            return new SenderProfile(Kind.ASSESSMENT_PLATFORM, assessment + " assessment platform", 0.45, 0, null);
        }
        String board = matchSuffix(domain, JOB_BOARD_DOMAINS);
        if (board != null) {
            return analyzeJobBoard(board, local, subj);
        }
        String nonJob = matchSuffix(domain, NON_JOB_DOMAINS);
        if (nonJob != null) {
            // Employees of these companies (e.g. a recruiter at atlassian.com) still send real job mail.
            if (RECRUITING_LOCAL.matcher(local).matches() || RECRUITING_DISPLAY.matcher(display).find()) {
                return new SenderProfile(Kind.RECRUITING_MAILBOX, "Recruiting mailbox (" + email + ")", 0.3, 0,
                        "Company site");
            }
            if (!isNoReply(local) && !NON_JOB_LOCAL.matcher(local).matches()
                    && display.trim().matches("\\p{Lu}[\\p{L}'.-]+(?:\\s+\\p{Lu}[\\p{L}'.-]+){1,3}")
                    && !subj.contains("notification")) {
                return new SenderProfile(Kind.COMPANY, "Company domain " + domain, 0, 0, "Company site");
            }
            return new SenderProfile(Kind.NON_JOB_SERVICE, capitalize(nonJob), 0, 1.0, null);
        }
        if (PERSONAL_PROVIDERS.contains(domain)) {
            return new SenderProfile(Kind.PERSONAL_PROVIDER, "Personal email address", 0, 0, null);
        }
        boolean recruitingLocal = RECRUITING_LOCAL.matcher(local).matches();
        boolean recruitingDisplay = RECRUITING_DISPLAY.matcher(display).find();
        if (recruitingLocal || recruitingDisplay) {
            double score = (recruitingLocal ? 0.25 : 0) + (recruitingDisplay ? 0.2 : 0);
            return new SenderProfile(Kind.RECRUITING_MAILBOX, "Recruiting mailbox (" + email + ")",
                    Math.min(0.4, score), 0, "Company site");
        }
        if (NON_JOB_LOCAL.matcher(local).matches()) {
            return new SenderProfile(Kind.NON_JOB_SERVICE, "Automated " + local + "@ mailbox", 0, 0.6, null);
        }
        return new SenderProfile(Kind.COMPANY, "Company domain " + domain, 0, 0, "Company site");
    }

    private static SenderProfile analyzeJobBoard(String board, String local, String subj) {
        boolean alertLocal = local.contains("alert") || local.contains("jobs-listings") || local.contains("digest")
                || local.contains("recommend") || local.contains("jobalerts") || local.contains("newsletter")
                || local.contains("jobmatch");
        boolean applicationSubject = subj.contains("application") || subj.contains("applied")
                || subj.contains("you applied");
        if (alertLocal && !applicationSubject) {
            return new SenderProfile(Kind.JOB_BOARD_ALERT, board + " job alert", 0, 1.2, board);
        }
        boolean applicationLocal = local.startsWith("jobs-noreply") || local.contains("apply")
                || local.contains("application") || local.equals("jobs") || local.contains("candidate");
        if (applicationSubject || applicationLocal) {
            return new SenderProfile(Kind.JOB_BOARD_APPLICATION, board + " application notification",
                    applicationSubject ? 0.35 : 0.2, 0, board);
        }
        if (local.contains("inmail") || local.contains("recruiter")) {
            return new SenderProfile(Kind.JOB_BOARD_OTHER, board + " recruiter message", 0.1, 0, board);
        }
        return new SenderProfile(Kind.JOB_BOARD_OTHER, board + " notification", 0, 0.5, board);
    }

    static String matchSuffix(String domain, Map<String, String> map) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : map.entrySet()) {
            String d = e.getKey();
            if (domain.equals(d) || domain.endsWith("." + d)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static boolean isIntermediaryDomain(String domain) {
        if (domain == null) {
            return true;
        }
        String d = domain.toLowerCase(Locale.ROOT);
        return PERSONAL_PROVIDERS.contains(d) || matchSuffix(d, ATS_DOMAINS) != null
                || matchSuffix(d, ASSESSMENT_DOMAINS) != null || matchSuffix(d, JOB_BOARD_DOMAINS) != null
                || matchSuffix(d, NON_JOB_DOMAINS) != null;
    }

    public static boolean isNoReply(String local) {
        String l = local == null ? "" : local.toLowerCase(Locale.ROOT);
        return l.contains("noreply") || l.contains("no-reply") || l.contains("donotreply") || l.contains("do-not-reply")
                || l.contains("notification") || l.contains("mailer") || l.contains("bounce")
                || RECRUITING_LOCAL.matcher(l).matches();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
