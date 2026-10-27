package com.sakec.smartspace.util;

import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import java.util.List;
import java.util.Locale;

/** Returns candidate room records with an exact map-label room number and compatible room type. */
public final class RoomLabelMatcher {
    private RoomLabelMatcher() { }

    public static List<Room> matches(List<Room> rooms, int floor, String label, String mapType) {
        String key = normalize(label);
        if (key.isEmpty()) return List.of();
        return rooms.stream()
                .filter(room -> room.floor() == floor && matchesName(room.name(), key))
                .filter(room -> compatible(room.type(), mapType))
                .toList();
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        if (normalized.isEmpty()) return "";
        return String.join(" ", java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.equals("SAMPLE"))
                .toList());
    }

    private static boolean matchesName(String roomName, String label) {
        String normalizedName = normalize(roomName);
        if (label.matches("\\d+[A-Z]?")) {
            String roomNumber = java.util.Arrays.stream(normalizedName.split("\\s+"))
                    .filter(token -> token.matches("\\d+[A-Z]?"))
                    .reduce((first, last) -> last)
                    .orElse(normalizedName);
            return roomNumber.equals(label);
        }
        return normalizedName.equals(label);
    }

    private static boolean compatible(RoomType roomType, String mapType) {
        return switch (mapType.toLowerCase(Locale.ROOT)) {
            case "classroom", "pg classroom" -> roomType == RoomType.CLASSROOM;
            case "laboratory", "lab" -> roomType == RoomType.LABORATORY || roomType == RoomType.COMPUTER_LAB;
            case "seminar hall", "seminar room" -> roomType == RoomType.SEMINAR_ROOM;
            default -> false;
        };
    }
}
