package com.sakec.smartspace.view;

/** Lets a page ask the shell to open another page, e.g. "rooms", "bookings", "requests". */
public interface Navigator {
    void go(String pageKey);
}
