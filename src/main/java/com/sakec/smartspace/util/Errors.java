package com.sakec.smartspace.util;

/** Converts any exception into a message that is safe to show to a user. */
public final class Errors {
    private Errors() { }

    public static String message(Throwable t) {
        if (t == null) return "Something went wrong. Please try again.";
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof AppException) return curr.getMessage();
            if (curr instanceof DataAccessException) {
                return "The database is not reachable right now. Please try again in a moment.";
            }
            curr = curr.getCause();
        }
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank() && !msg.startsWith("java.")) {
            return msg;
        }
        return "Something went wrong. Please try again.";
    }
}
