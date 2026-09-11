package com.hub.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalOAuthTokenStore {
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    public void put(long userId, String type, String token) { tokens.put(key(userId, type), token); }
    public String get(long userId, String type) { return tokens.get(key(userId, type)); }
    public boolean connected(long userId, String type) { return tokens.containsKey(key(userId, type)); }
    private static String key(long userId, String type) { return userId + ":" + type; }
}