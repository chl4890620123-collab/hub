package com.hub.model;

import java.util.List;
import java.util.Map;

/**
 * One work-context card groups the answer, related source locations and work records.
 * This is the product's core view: users should not have to reopen Slack, Drive and GitHub one by one.
 */
public record WorkContextBundle(
        String query,
        String summary,
        List<MaterialHit> sources,
        List<TodoItem> todos,
        List<Map<String, Object>> decisions,
        List<Map<String, Object>> changes,
        List<TimelineEvent> timeline
) {}
