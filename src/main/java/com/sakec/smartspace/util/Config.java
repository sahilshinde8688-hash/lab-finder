package com.sakec.smartspace.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings from (lowest to highest priority): bundled app.properties,
 * ./smartspace.properties, environment variables (SMARTSPACE_...).
 */
public final class Config {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getResourceAsStream("/config/app.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException e) {
            Log.error("Could not read bundled configuration", e);
        }
        Path external = Path.of("smartspace.properties");
        if (Files.isReadable(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                PROPS.load(in);
            } catch (IOException e) {
                Log.error("Could not read smartspace.properties", e);
            }
        }
    }

    private Config() { }

    public static String get(String key, String fallback) {
        String sys = System.getProperty("smartspace." + key);
        if (sys != null && !sys.isBlank()) return sys.trim();
        String env = System.getenv("SMARTSPACE_" + key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env.trim();
        return PROPS.getProperty(key, fallback).trim();
    }

    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean getBool(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }
}
