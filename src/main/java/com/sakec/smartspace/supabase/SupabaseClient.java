package com.sakec.smartspace.supabase;

import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.Log;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin HTTP client for Supabase Auth REST API.
 *
 * <p>Only the anon/publishable key is used here — never the service-role key.
 * Row Level Security on the Supabase side enforces access control.
 *
 * <p>Configuration (lowest → highest priority):
 * <ul>
 *   <li>{@code supabase.url}     in app.properties / smartspace.properties</li>
 *   <li>{@code supabase.anonKey} in app.properties / smartspace.properties</li>
 *   <li>Environment variables {@code SMARTSPACE_SUPABASE_URL} / {@code SMARTSPACE_SUPABASE_ANONKEY}</li>
 * </ul>
 */
public final class SupabaseClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Lazily created singleton. */
    private static volatile SupabaseClient INSTANCE;

    private final OkHttpClient http;
    private final String baseUrl;
    private final String anonKey;

    private SupabaseClient() {
        this.baseUrl = Config.get("supabase.url", "").replaceAll("/+$", "");
        this.anonKey = Config.get("supabase.anonKey", "");
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static SupabaseClient get() {
        if (INSTANCE == null) {
            synchronized (SupabaseClient.class) {
                if (INSTANCE == null) INSTANCE = new SupabaseClient();
            }
        }
        return INSTANCE;
    }

    /** Returns true when both supabase.url and supabase.anonKey are configured. */
    public boolean isConfigured() {
        return !baseUrl.isBlank() && !anonKey.isBlank();
    }

    /**
     * POST JSON body to a Supabase Auth endpoint.
     *
     * @param path  e.g. {@code /auth/v1/signup}
     * @param body  raw JSON string
     * @return parsed {@link SupabaseResponse}
     */
    public SupabaseResponse post(String path, String body) {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + anonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
        return execute(req);
    }

    /**
     * POST JSON body to a Supabase Auth endpoint using a user's JWT access token.
     * Used for operations that require the caller to be authenticated (e.g. sign-out).
     */
    public SupabaseResponse postAuthed(String path, String body, String accessToken) {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
        return execute(req);
    }

    private SupabaseResponse execute(Request req) {
        try (Response resp = http.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String json = rb != null ? rb.string() : "";
            return new SupabaseResponse(resp.code(), json);
        } catch (IOException e) {
            Log.error("Supabase HTTP call failed: " + req.url(), e);
            throw new SupabaseException("Network error communicating with Supabase. Check your internet connection.", e);
        }
    }
}
