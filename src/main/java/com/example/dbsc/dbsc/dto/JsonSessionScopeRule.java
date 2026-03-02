package com.example.dbsc.dbsc.dto;

/**
 * JSON Session Scope Rule per W3C DBSC § 9.8.
 */
public record JsonSessionScopeRule(String type, String domain, String path) {}
