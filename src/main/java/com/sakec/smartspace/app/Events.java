package com.sakec.smartspace.app;

/** Tiny hook so any page can ask the shell to refresh the notification badge. */
public final class Events {
    private static Runnable notificationsChanged = () -> { };

    private Events() { }

    public static void onNotificationsChanged(Runnable r) { notificationsChanged = r == null ? () -> { } : r; }

    public static void notificationsChanged() { notificationsChanged.run(); }
}
