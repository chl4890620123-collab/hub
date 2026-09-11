// duplicate hints must be deterministic because they are only a human-review aid, not an AI decision.
package com.hub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TodoDuplicateDetectorTest {
    @Test
    void identicalNormalizedTitlesArePerfectMatch(){
        assertEquals(1.0,TodoDuplicateDetector.dice("driveoauth테스트","driveoauth테스트"));
    }

    @Test
    void unrelatedTitlesStayBelowReviewThreshold(){
        assertTrue(TodoDuplicateDetector.dice("driveoauth테스트","점심메뉴정리")<0.5);
    }
}
