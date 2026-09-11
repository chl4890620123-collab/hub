package com.hub.model;

import java.util.List;

/** Answer plus the exact Hub/Slack/Drive/GitHub locations used as evidence. */
public record MaterialAskResponse(String answer, List<MaterialHit> sources) {
}
