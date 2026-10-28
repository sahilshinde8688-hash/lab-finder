package com.sakec.smartspace.supabase;

/**
 * Raw HTTP response from a Supabase REST call.
 * JSON is parsed on demand by {@link SupabaseAuthService}.
 */
public record SupabaseResponse(int statusCode, String body) {

    /** Returns true for 2xx status codes. */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Extracts a simple top-level string value from the JSON body without
     * a full JSON library. Supabase error objects always have a {@code "message"} field.
     *
     * <p>Example: {@code {"message":"User already registered"}} → {@code "User already registered"}
     */
    public String jsonString(String key) {
        // Pattern: "key":"value"  (handles escaped quotes inside values too)
        String search = "\"" + key + "\"";
        int idx = body.indexOf(search);
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = body.indexOf('"', colon + 1);
        if (start < 0) return null;
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(i + 1);
                if (next == '"') { sb.append('"'); i += 2; continue; }
                if (next == 'n') { sb.append('\n'); i += 2; continue; }
                if (next == '\\') { sb.append('\\'); i += 2; continue; }
                sb.append(next); i += 2; continue;
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Returns true when the body contains a field with the given key. */
    public boolean hasField(String key) {
        return body.contains("\"" + key + "\"");
    }
}
