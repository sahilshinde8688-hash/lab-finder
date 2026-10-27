package com.sakec.smartspace.model;

import java.time.LocalDateTime;

/** Filters for the room search. Null fields mean "no filter". The window must be on a single day. */
public record SearchCriteria(Integer floor, RoomType type, LocalDateTime from, LocalDateTime to,
                             Integer minCapacity, String equipment, boolean availableOnly,
                             boolean includeInactive) {
    public static SearchCriteria nowOnly() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return new SearchCriteria(null, null, now, now.plusMinutes(1), null, null, false, false);
    }
}
