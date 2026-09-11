package com.hub.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingTest {
    @Test
    void sha256IsStable() {
        assertEquals(Hashing.sha256("Hub"), Hashing.sha256("Hub"));
        assertEquals(64, Hashing.sha256("Hub").length());
    }
}
