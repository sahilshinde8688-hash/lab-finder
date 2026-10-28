package com.sakec.smartspace.util;

/** An error whose message is safe and meant to be shown to the end user. */
public class AppException extends RuntimeException {
    public AppException(String message) { super(message); }
    public AppException(String message, Throwable cause) { super(message, cause); }
}
