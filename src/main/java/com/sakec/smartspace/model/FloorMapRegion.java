package com.sakec.smartspace.model;

/** Normalized floor-map geometry associated with an optional configured room. */
public record FloorMapRegion(int floor, String key, String number, String type, double x, double y,
                             double width, double height, Integer roomId) { }
