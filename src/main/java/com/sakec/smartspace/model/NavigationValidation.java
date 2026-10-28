package com.sakec.smartspace.model;

import java.util.List;

public record NavigationValidation(List<NavigationIssue> issues) {
    public NavigationValidation { issues = List.copyOf(issues); }

    public boolean valid() { return issues.isEmpty(); }
}
