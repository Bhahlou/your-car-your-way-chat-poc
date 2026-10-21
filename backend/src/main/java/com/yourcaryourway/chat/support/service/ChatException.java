package com.yourcaryourway.chat.support.service;

import org.springframework.http.HttpStatus;

/** Chat business error. The associated HTTP status is used by {@code ChatExceptionHandler}. */
public class ChatException extends RuntimeException {

    private final HttpStatus status;

    private ChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ChatException notFound(String message) {
        return new ChatException(HttpStatus.NOT_FOUND, message);
    }

    public static ChatException forbidden(String message) {
        return new ChatException(HttpStatus.FORBIDDEN, message);
    }

    /** The conversation's state does not allow the action (already claimed, already closed...). */
    public static ChatException conflict(String message) {
        return new ChatException(HttpStatus.CONFLICT, message);
    }

    public static ChatException invalid(String message) {
        return new ChatException(HttpStatus.BAD_REQUEST, message);
    }
}
