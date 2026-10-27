package com.sakec.smartspace.model;

import java.util.Arrays;
import java.util.List;

public record Room(int id, String name, int floor, RoomType type, int capacity, String facilities,
                   boolean active, boolean sample) {

    public List<String> facilityList() {
        if (facilities == null || facilities.isBlank()) return List.of();
        return Arrays.stream(facilities.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override public String toString() { return name + " (Floor " + floor + ")"; }
}
