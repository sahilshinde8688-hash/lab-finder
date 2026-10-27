package com.sakec.smartspace.security;

import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.util.AppException;

/** Role-based access control. Services call require(...) before every protected action. */
public final class Permissions {
    private Permissions() { }

    public static User require(Role... allowed) {
        User u = Session.user();
        for (Role r : allowed) {
            if (u.role() == r) return u;
        }
        throw new AppException("You don't have permission to do that.");
    }

    public static boolean isCoordinator() {
        return Session.isSignedIn() && Session.role() == Role.COORDINATOR;
    }
}
