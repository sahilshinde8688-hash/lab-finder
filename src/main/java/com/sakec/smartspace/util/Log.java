package com.sakec.smartspace.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Thin wrapper over java.util.logging. Never pass passwords or other secrets to these methods. */
public final class Log {
    private static final Logger LOG = Logger.getLogger("smartspace");

    private Log() { }

    public static void info(String msg) { LOG.info(msg); }

    public static void warn(String msg) { LOG.warning(msg); }

    public static void error(String msg, Throwable t) { LOG.log(Level.SEVERE, msg, t); }
}
