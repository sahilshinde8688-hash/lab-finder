package com.sakec.smartspace.supabase;

import com.sakec.smartspace.util.Log;

/**
 * Supabase Auth operations used by the student sign-up flow.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Only the anon key is used — no service-role key anywhere in client code.</li>
 *   <li>Passwords are sent only over HTTPS to Supabase; never logged, never stored locally.</li>
 *   <li>The {@code user_metadata} block carries {@code full_name} and {@code branch}
 *       so Supabase can store them on the auth.users record and trigger a profile insert.</li>
 *   <li>Error messages from Supabase are sanitised before reaching the UI to avoid
 *       leaking internal detail (e.g. "User already registered" → friendly message).</li>
 * </ul>
 *
 * <p>Supabase Auth endpoints used:
 * <ul>
 *   <li>{@code POST /auth/v1/signup}      — create account</li>
 *   <li>{@code POST /auth/v1/resend}       — resend confirmation email</li>
 * </ul>
 */
public final class SupabaseAuthService {

    private final SupabaseClient client = SupabaseClient.get();

    /**
     * Registers a new student with Supabase Auth.
     *
     * <p>If Supabase email confirmation is enabled, the user receives a confirmation
     * email and cannot sign in until they click the link. If confirmation is disabled
     * (Supabase project settings → Auth → Email → "Confirm email" off), the account
     * is active immediately.
     *
     * @param email     normalised (lowercase, trimmed) SAKEC student email
     * @param password  plaintext password (sent over HTTPS, never stored or logged here)
     * @param fullName  display name stored in user_metadata
     * @param branch    selected branch stored in user_metadata
     * @return {@link SignUpResult} describing the outcome
     * @throws SupabaseException if the network call fails or Supabase returns an error
     */
    public SignUpResult signUp(String email, String password, String fullName, String branch) {
        if (!client.isConfigured()) {
            Log.warn("Supabase is not configured; skipping cloud auth registration for " + email);
            return SignUpResult.SUPABASE_SKIPPED;
        }

        // Build JSON manually — avoids pulling in a JSON library just for one call.
        // Passwords are never logged; only the email is recorded.
        String body = "{"
                + "\"email\":" + jsonStr(email) + ","
                + "\"password\":" + jsonStr(password) + ","
                + "\"data\":{"
                +   "\"full_name\":" + jsonStr(fullName) + ","
                +   "\"branch\":" + jsonStr(branch)
                + "}"
                + "}";

        Log.info("Supabase sign-up attempt: " + email);
        SupabaseResponse resp = client.post("/auth/v1/signup", body);

        if (resp.isSuccess()) {
            // Supabase returns the user object on success.
            // If "identities" is an empty array, the email is already registered.
            if (resp.body().contains("\"identities\":[]")) {
                Log.warn("Supabase sign-up: email already registered: " + email);
                return SignUpResult.ALREADY_EXISTS;
            }
            Log.info("Supabase sign-up success: " + email);
            return SignUpResult.SUCCESS;
        }

        // ---- Error handling: map Supabase error codes to user-friendly messages
        String msg = resp.jsonString("message");
        String code = resp.jsonString("error_code");
        if (msg == null) msg = resp.jsonString("error");
        Log.warn("Supabase sign-up failed [" + resp.statusCode() + "]: " + msg + " (code=" + code + ")");

        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("already registered") || lower.contains("already exists")
                    || lower.contains("duplicate") || "user_already_exists".equals(code)) {
                return SignUpResult.ALREADY_EXISTS;
            }
            if (lower.contains("password") || "weak_password".equals(code)) {
                throw new SupabaseException("Password does not meet Supabase requirements. Use at least 12 characters with letters and numbers.");
            }
            if (lower.contains("rate limit") || resp.statusCode() == 429) {
                throw new SupabaseException("Too many registration attempts. Please wait a minute and try again.");
            }
            if (lower.contains("invalid email") || lower.contains("unable to validate")) {
                throw new SupabaseException("This email address was not accepted. Check the address and try again.");
            }
        }

        // Generic fallback — don't show raw Supabase internals to the user
        throw new SupabaseException("Registration could not be completed right now. Please try again in a moment.");
    }

    /**
     * Checks whether the given email has been confirmed in Supabase Auth by attempting
     * a password sign-in. A successful sign-in response means the account is confirmed
     * (Supabase blocks unverified sign-ins when email confirmation is enabled).
     *
     * <p>Returns {@code true} if confirmed, {@code false} if not yet confirmed or if
     * Supabase is not configured. Throws {@link SupabaseException} only on hard errors
     * (network failure, rate limit, etc.).
     *
     * @param email    normalised student email
     * @param password the student's password (sent over HTTPS only)
     */
    public boolean checkEmailConfirmed(String email, String password) {
        if (!client.isConfigured()) {
            // No Supabase → treat as verified (don't block login)
            return true;
        }
        String body = "{"
                + "\"email\":" + jsonStr(email) + ","
                + "\"password\":" + jsonStr(password)
                + "}";
        try {
            SupabaseResponse resp = client.post("/auth/v1/token?grant_type=password", body);
            if (resp.isSuccess() && resp.hasField("access_token")) {
                Log.info("Supabase email confirmed for: " + email);
                return true;
            }
            // Supabase returns 400 with error_code "email_not_confirmed" when unverified
            String code = resp.jsonString("error_code");
            String msg  = resp.jsonString("message");
            if (msg == null) msg = resp.jsonString("error");
            if ("email_not_confirmed".equals(code)
                    || (msg != null && msg.toLowerCase().contains("email not confirmed"))) {
                Log.info("Supabase email not yet confirmed for: " + email);
                return false;
            }
            if (resp.statusCode() == 429) {
                throw new SupabaseException("Too many attempts. Please wait a minute and try again.");
            }
            // Any other error (wrong password etc.) — don't expose, just return false
            Log.warn("Supabase checkEmailConfirmed unexpected response [" + resp.statusCode() + "]: " + msg);
            return false;
        } catch (SupabaseException e) {
            throw e;
        } catch (Exception e) {
            Log.error("Supabase checkEmailConfirmed failed", e);
            // Network error → don't block the user; let them try again later
            return false;
        }
    }

    /**
     * Requests Supabase to resend the confirmation email for the given address.
     * No-op if Supabase is not configured.
     *
     * @param email normalised student email
     */
    public void resendConfirmation(String email) {
        if (!client.isConfigured()) return;
        String body = "{\"type\":\"signup\",\"email\":" + jsonStr(email) + "}";
        try {
            SupabaseResponse resp = client.post("/auth/v1/resend", body);
            if (!resp.isSuccess()) {
                Log.warn("Supabase resend confirmation failed [" + resp.statusCode() + "]: " + resp.body());
            } else {
                Log.info("Supabase resend confirmation sent to: " + email);
            }
        } catch (SupabaseException e) {
            Log.warn("Supabase resend confirmation error: " + e.getMessage());
            // Don't surface to the user — best effort
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Minimal JSON string escaper — only handles characters that appear in names/emails/branches. */
    private static String jsonStr(String value) {
        if (value == null) return "null";
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    // ---- Result enum -----------------------------------------------------------------------

    public enum SignUpResult {
        /** Supabase accepted the registration. */
        SUCCESS,
        /**
         * Supabase reports the email is already registered.
         * The local SQLite account has already been created; this is informational only.
         */
        ALREADY_EXISTS,
        /**
         * Supabase is not configured (missing URL or key).
         * Registration proceeds locally only.
         */
        SUPABASE_SKIPPED
    }
}
