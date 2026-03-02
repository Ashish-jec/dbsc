package com.example.dbsc.dbsc.dto;

/**
 * JSON Session Credential per W3C DBSC § 9.9.
 */
public record JsonSessionCredential(String type, String name, String attributes) {}
