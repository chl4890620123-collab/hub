package com.hub.connector;

import java.util.List;

public interface ReadOnlyConnector {
    String type();
    List<ExternalContent> fetch(String scope, String accessToken);
}
