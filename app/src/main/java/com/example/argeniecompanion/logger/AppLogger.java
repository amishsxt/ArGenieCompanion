package com.example.argeniecompanion.logger;

import android.util.Log;

import com.example.argeniecompanion.app.ArGenieApp;

public class AppLogger {

    // --- Log levels ---
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, NONE
    }

    // --- Config ---
    private static LogLevel globalLogLevel = LogLevel.DEBUG;
    private static boolean isLoggingEnabled =
            ArGenieApp.getInstance().getConfig().isLoggingEnabled();

    private static final String DEFAULT_TAG = AppLogger.class.getSimpleName();

    // --- Config setters ---
    public static void setLogLevel(LogLevel logLevel) {
        globalLogLevel = logLevel;
    }

    public static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
    }

    // --- Core log method ---
    private static void log(LogLevel level, String tag, String message) {
        log(level, tag, message, null);
    }

    private static void log(LogLevel level, String tag, String message, Throwable throwable) {
        if (!isLoggingEnabled || level.ordinal() < globalLogLevel.ordinal()) {
            return;
        }

        if (message == null) message = "null";

        switch (level) {
            case DEBUG:
                Log.d(tag, message, throwable);
                break;
            case INFO:
                Log.i(tag, message, throwable);
                break;
            case WARN:
                Log.w(tag, message, throwable);
                break;
            case ERROR:
                Log.e(tag, message, throwable);
                break;
            default:
                break;
        }
    }

    // --- Logging with custom tag ---
    public static void d(String tag, String message) { log(LogLevel.DEBUG, tag, message); }
    public static void i(String tag, String message) { log(LogLevel.INFO, tag, message); }
    public static void w(String tag, String message) { log(LogLevel.WARN, tag, message); }
    public static void e(String tag, String message) { log(LogLevel.ERROR, tag, message); }

    // --- Logging with default tag ---
    public static void d(String message) { d(DEFAULT_TAG, message); }
    public static void i(String message) { i(DEFAULT_TAG, message); }
    public static void w(String message) { w(DEFAULT_TAG, message); }
    public static void e(String message) { e(DEFAULT_TAG, message); }

    // --- Logging with Throwable (custom tag) ---
    public static void d(String tag, String message, Throwable throwable) {
        log(LogLevel.DEBUG, tag, message, throwable);
    }
    public static void i(String tag, String message, Throwable throwable) {
        log(LogLevel.INFO, tag, message, throwable);
    }
    public static void w(String tag, String message, Throwable throwable) {
        log(LogLevel.WARN, tag, message, throwable);
    }
    public static void e(String tag, String message, Throwable throwable) {
        log(LogLevel.ERROR, tag, message, throwable);
    }

    // --- Logging with Throwable (default tag) ---
    public static void d(String message, Throwable throwable) { d(DEFAULT_TAG, message, throwable); }
    public static void i(String message, Throwable throwable) { i(DEFAULT_TAG, message, throwable); }
    public static void w(String message, Throwable throwable) { w(DEFAULT_TAG, message, throwable); }
    public static void e(String message, Throwable throwable) { e(DEFAULT_TAG, message, throwable); }

}

