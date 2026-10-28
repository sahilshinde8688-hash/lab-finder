package com.sakec.smartspace.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Input validation shared by the UI and the services. */
public final class Validators {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private Validators() { }

    /** All domains accepted for any login (students + staff). */
    public static List<String> allowedDomains() {
        return Arrays.stream(Config.get("auth.allowedDomains", "sakec.onmicrosoft.com,sakec.ac.in").split(","))
                .map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Domains accepted for student self-registration only.
     * Defaults to the Microsoft 365 tenant domain.
     */
    public static List<String> studentDomains() {
        return Arrays.stream(Config.get("auth.studentDomains", "sakec.onmicrosoft.com").split(","))
                .map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Configurable branch list for the sign-up dropdown.
     * Pipe-separated in config so branch names can contain commas.
     */
    public static List<String> branches() {
        String raw = Config.get("auth.branches",
                "Computer Engineering|Information Technology|"
                + "Computer Science and Engineering (Cyber Security)|"
                + "Artificial Intelligence and Data Science|"
                + "Electronics and Computer Science|"
                + "Electronics and Telecommunication Engineering|"
                + "Electronics Engineering (VLSI Design and Technology)|"
                + "Electronics and Communication (Advanced Communication Technology)");
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean isEmailFormatValid(String email) {
        return email != null && email.length() <= 120 && EMAIL.matcher(email.trim()).matches();
    }

    public static boolean isAllowedDomain(String email) {
        if (!isEmailFormatValid(email)) return false;
        String domain = email.trim().substring(email.trim().indexOf('@') + 1).toLowerCase();
        return allowedDomains().contains(domain);
    }

    /** Returns true when the email domain is permitted for student self-registration. */
    public static boolean isStudentDomain(String email) {
        if (!isEmailFormatValid(email)) return false;
        String domain = email.trim().substring(email.trim().indexOf('@') + 1).toLowerCase();
        return studentDomains().contains(domain);
    }

    /** Returns an error message, or null if the e-mail is acceptable for any login. */
    public static String checkEmail(String email) {
        if (email == null || email.isBlank()) return "Enter your SAKEC email address.";
        if (!isEmailFormatValid(email)) return "That doesn't look like a valid email address.";
        if (!isAllowedDomain(email)) return "Use your institutional email (@" + String.join(", @", allowedDomains()) + ").";
        return null;
    }

    /**
     * Stricter check used during self-registration: requires the student Microsoft 365 domain.
     * Returns an error message, or null if the email is acceptable.
     */
    public static String checkStudentEmail(String email) {
        if (email == null || email.isBlank()) return "Enter your SAKEC email address.";
        String norm = email.trim().toLowerCase();
        if (!isEmailFormatValid(norm)) return "That doesn't look like a valid email address.";
        if (!isStudentDomain(norm)) {
            return "Use your SAKEC student email (@" + String.join(", @", studentDomains()) + ").";
        }
        return null;
    }

    /**
     * Password policy for self-registration: at least 12 characters, with letters and digits.
     * Returns an error message, or null if the password satisfies the policy.
     */
    public static String checkPassword(String pw) {
        if (pw == null || pw.length() < 12) return "Use at least 12 characters.";
        if (pw.length() > 128) return "Password is too long.";
        boolean letter = pw.chars().anyMatch(Character::isLetter);
        boolean digit = pw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) return "Include at least one letter and one number.";
        return null;
    }
}
