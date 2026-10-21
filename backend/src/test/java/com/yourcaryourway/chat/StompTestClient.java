package com.yourcaryourway.chat;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test STOMP client: behaves like the browser (connection with JWT, subscriptions, sending). */
final class StompTestClient implements AutoCloseable {

    static final long TIMEOUT_SECONDS = 5;
    private static final String ERRORS_QUEUE = "/user/queue/errors";

    private final WebSocketStompClient client;
    private final StompSession session;
    private final SubscriptionWaiter waiter;

    /** Completes when the server sends an ERROR frame (subscription or send refused) or closes the connection. */
    final CompletableFuture<Void> serverRejection;

    /** Business errors received on /user/queue/errors (e.g. empty message). */
    final BlockingQueue<JsonNode> errors = new LinkedBlockingQueue<>();

    private StompTestClient(WebSocketStompClient client, StompSession session, CompletableFuture<Void> serverRejection,
                           SubscriptionWaiter waiter) {
        this.waiter = waiter;
        this.client = client;
        this.session = session;
        this.serverRejection = serverRejection;
    }

    /** Connects with the given token. Throws if the server refuses the connection. */
    static StompTestClient connect(int port, String token, SubscriptionWaiter waiter) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler); // required for heartbeats
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        CompletableFuture<Void> rejection = new CompletableFuture<>();
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
            new StompSessionHandlerAdapter() {
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    rejection.complete(null); // ERROR frame
                }

                @Override
                public void handleTransportError(StompSession s, Throwable e) {
                    rejection.complete(null);
                }
            }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        StompTestClient result = new StompTestClient(client, session, rejection, waiter);
        result.subscribe(ERRORS_QUEUE);
        return result;
    }

    /** Subscribes and waits until the server has registered the subscription: after that no message can be missed. */
    BlockingQueue<JsonNode> subscribe(String destination) throws Exception {
        BlockingQueue<JsonNode> received = destination.equals(ERRORS_QUEUE) ? errors : new LinkedBlockingQueue<>();
        boolean userQueue = destination.startsWith("/user/");
        int before = userQueue ? 0 : waiter.subscribers(destination);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((JsonNode) payload);
            }
        });
        if (userQueue) {
            Thread.sleep(150); // user destination: translated by Spring, no simple counter
        } else {
            waiter.awaitMoreThan(destination, before);
        }
        return received;
    }

    /** Subscription request without waiting for confirmation (used to check that it is refused). */
    void subscribeWithoutWaiting(String destination) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // nothing: a refusal is expected
            }
        });
    }

    void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    /** Waits for the next entry of the queue; fails if nothing arrives within the timeout. */
    static JsonNode next(BlockingQueue<JsonNode> queue) throws InterruptedException {
        JsonNode node = queue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (node == null) {
            throw new AssertionError("Aucun message reçu en " + TIMEOUT_SECONDS + " s");
        }
        return node;
    }

    @Override
    public void close() {
        try {
            session.disconnect();
        } catch (RuntimeException alreadyClosedByServer) {
            // the server has already closed the connection (refused subscriptions): nothing to do
        }
        client.stop();
    }
}
