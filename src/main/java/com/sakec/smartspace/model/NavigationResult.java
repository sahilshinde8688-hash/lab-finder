package com.sakec.smartspace.model;

public record NavigationResult(NavigationPath path, String diagnostic) {
    public boolean found() { return path != null; }
}
