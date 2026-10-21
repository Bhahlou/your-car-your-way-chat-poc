package com.yourcaryourway.chat.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat settings ({@code app.chat.*}).
 *
 * @param allowedOrigins origins allowed to open a WebSocket connection, comma-separated
 */
@ConfigurationProperties("app.chat")
public record ChatProperties(String allowedOrigins) {
}
