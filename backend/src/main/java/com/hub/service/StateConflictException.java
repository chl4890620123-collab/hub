package com.hub.service;

/** Domain state conflict: the request was valid, but the entity already moved to another state. */
public class StateConflictException extends RuntimeException {
    public StateConflictException(String message) { super(message); }
}
