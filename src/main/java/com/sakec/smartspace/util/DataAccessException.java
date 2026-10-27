package com.sakec.smartspace.util;

/** Wraps SQLException. The cause is logged, but the user only sees a generic message. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
