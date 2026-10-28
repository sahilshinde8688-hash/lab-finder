package com.sakec.smartspace.supabase;

import com.sakec.smartspace.util.AppException;

/**
 * Thrown when a Supabase Auth call fails in a way that should be shown to the user.
 * Extends {@link AppException} so the existing {@code Errors.message()} handler picks it up.
 */
public class SupabaseException extends AppException {
    public SupabaseException(String message) {
        super(message);
    }

    public SupabaseException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
