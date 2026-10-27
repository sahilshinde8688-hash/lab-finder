package com.sakec.smartspace.model;

/** A room together with its computed status for a time window and a short human readable explanation. */
public record RoomAvailability(Room room, RoomStatus status, String detail) { }
