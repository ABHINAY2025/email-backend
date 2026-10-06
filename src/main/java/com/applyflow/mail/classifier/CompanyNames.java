package com.applyflow.mail.classifier;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Company-name normalisation helpers shared by extraction, matching and persistence. */
public final class CompanyNames {

    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(inc|incorporated|llc|l\\.l\\.c|ltd|limited|corp|corporation|co|company|gmbh|ag|sa|plc|pvt|private|"
                    + "pte|bv|nv|llp|srl|oy|ab|as|kk|careers|jobs|recruiting|recruitment|india|us|usa|global)\\b\\.?");

    private static final Set<String> MULTI_PART_TLDS = Set.of("co", "com", "ac", "org", "net", "gov", "edu", "ltd");

    private CompanyNames() {
    }

    /** Canonical key: lowercase, no punctuation, no legal suffixes ("Amazon.com, Inc." and "Amazon" both → amazon). */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase(Locale.ROOT).replace("&", " and ");
        n = n.replaceAll("\\.(com|io|ai|co|in|net|org)\\b", " ");
        n = n.replaceAll("[^a-z0-9 ]", " ");
        String stripped = LEGAL_SUFFIX.matcher(n).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (stripped.isEmpty()) {
            stripped = n.replaceAll("\\s+", " ").trim();
        }
        return stripped;
    }

    /** Registrable domain, e.g. "mail.careers.amazon.co.uk" → "amazon.co.uk". */
    public static String registrableDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String[] parts = domain.toLowerCase(Locale.ROOT).trim().split("\\.");
        if (parts.length <= 2) {
            return String.join(".", parts);
        }
        String tld = parts[parts.length - 1];
        String sld = parts[parts.length - 2];
        if (tld.length() == 2 && MULTI_PART_TLDS.contains(sld) && parts.length >= 3) {
            return parts[parts.length - 3] + "." + sld + "." + tld;
        }
        return sld + "." + tld;
    }

    /** Brand label of a domain: "careers.amazon.co.uk" → "amazon". */
    public static String domainLabel(String domain) {
        String reg = registrableDomain(domain);
        if (reg == null) {
            return null;
        }
        int dot = reg.indexOf('.');
        return dot < 0 ? reg : reg.substring(0, dot);
    }

    /** Human company name from a domain label: "flipkart" → "Flipkart", "acme-labs" → "Acme Labs". */
    public static String nameFromDomain(String domain) {
        String label = domainLabel(domain);
        if (label == null || label.isBlank()) {
            return null;
        }
        String[] words = label.replace('_', '-').split("-");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Pretty name from a URL slug: "acme-labs" → "Acme Labs". */
    public static String nameFromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        String s = slug.replaceAll("[0-9]+$", "").replace('_', '-');
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("-")) {
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
