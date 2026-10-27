package com.sakec.smartspace.util;

/** Converts any exception into a message that is safe to show to a user. */
public final class Errors {
    private Errors() { }

    public static String message(Throwable t) {
        if (t instanceof AppException) return t.getMessage();
        if (t instanceof DataAccessException) {
            return "The database is not reachable right now. Please try again in a moment.";
        }
        return "Something went wrong. Please try again.";
    }
}
