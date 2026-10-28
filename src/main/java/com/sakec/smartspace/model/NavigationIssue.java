package com.sakec.smartspace.model;

public record NavigationIssue(String code, String message) {
    @Override public String toString() { return message; }
}
