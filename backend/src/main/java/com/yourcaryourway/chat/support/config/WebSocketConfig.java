package com.yourcaryourway.chat.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time channel: WebSocket + STOMP.
 * <ul>
 *   <li>the browser connects on {@code /ws};</li>
 *   <li>it sends to {@code /app/...} (handled by {@code ChatMessagingController});</li>
 *   <li>it receives on {@code /topic/...} (broadcast) and {@code /user/queue/...} (personal replies).</li>
 * </ul>
 * The broker is Spring's in-memory one (enough for the PoC, a single back end instance).
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatProperties properties;
    private final StompSecurityInterceptor securityInterceptor;

    WebSocketConfig(ChatProperties properties, StompSecurityInterceptor securityInterceptor) {
        this.properties = properties;
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(properties.allowedOrigins().split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
        heartbeatScheduler.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[] {10_000, 10_000}) // detects dead connections, keeps the proxy awake
            .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(securityInterceptor);
    }
}
