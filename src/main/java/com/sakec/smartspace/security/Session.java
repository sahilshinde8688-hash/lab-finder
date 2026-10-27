package com.sakec.smartspace.security;

import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.util.AppException;

/** Holds the signed-in user. Cleared completely on logout. */
public final class Session {
    private static volatile User current;

    private Session() { }

    public static void start(User user) { current = user; }

    public static void clear() { current = null; }

    public static User user() {
        User u = current;
        if (u == null) throw new AppException("Your session has ended. Please sign in again.");
        return u;
    }

    public static boolean isSignedIn() { return current != null; }

    public static Role role() { return user().role(); }
}
