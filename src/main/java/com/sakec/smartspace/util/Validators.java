package com.sakec.smartspace.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Input validation shared by the UI and the services. */
public final class Validators {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private Validators() { }

    public static List<String> allowedDomains() {
        return Arrays.stream(Config.get("auth.allowedDomains", "sakec.ac.in").split(","))
                .map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean isEmailFormatValid(String email) {
        return email != null && email.length() <= 120 && EMAIL.matcher(email.trim()).matches();
    }

    public static boolean isAllowedDomain(String email) {
        if (!isEmailFormatValid(email)) return false;
        String domain = email.trim().substring(email.trim().indexOf('@') + 1).toLowerCase();
        return allowedDomains().contains(domain);
    }

    /** Returns an error message, or null if the e-mail is acceptable. */
    public static String checkEmail(String email) {
        if (email == null || email.isBlank()) return "Enter your SAKEC email address.";
        if (!isEmailFormatValid(email)) return "That doesn't look like a valid email address.";
        if (!isAllowedDomain(email)) return "Use your institutional email (@" + String.join(", @", allowedDomains()) + ").";
        return null;
    }

    /** Returns an error message, or null if the password satisfies the policy. */
    public static String checkPassword(String pw) {
        if (pw == null || pw.length() < 10) return "Use at least 10 characters.";
        if (pw.length() > 128) return "Password is too long.";
        boolean letter = pw.chars().anyMatch(Character::isLetter);
        boolean digit = pw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) return "Include at least one letter and one number.";
        return null;
    }
}
