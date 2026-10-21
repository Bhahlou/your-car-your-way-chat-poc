package com.yourcaryourway.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chat end to end: real WebSocket, real PostgreSQL. This is the test that demonstrates that the solution
 * (STOMP + persistence before broadcast + subscription access control) works.
 */
class ChatWebSocketIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/support/conversations";

    @Autowired
    private SimpleBrokerMessageHandler broker;

    private SubscriptionWaiter waiter;

    @BeforeEach
    void createWaiter() {
        waiter = new SubscriptionWaiter(broker);
    }

    @Test
    @DisplayName("Un client et un conseiller échangent en temps réel, et les messages sont conservés (US-8.2)")
    void clientAndAgentChatInRealTime() throws Exception {
        TestApi api = new TestApi(port);
        String clientToken = api.login("client1@example.com");
        String agentToken = api.login("agent1@example.com");

        try (StompTestClient agent = StompTestClient.connect(port, agentToken, waiter);
             StompTestClient client = StompTestClient.connect(port, clientToken, waiter)) {

            // 1. The agent listens to the waiting queue; the customer opens a conversation
            BlockingQueue<JsonNode> queue = agent.subscribe("/topic/support.queue");
            long id = api.post(BASE, clientToken, null).json().get("id").asLong();
            JsonNode waiting = StompTestClient.next(queue);
            assertThat(waiting.get("type").asString()).isEqualTo("WAITING");
            assertThat(waiting.get("conversation").get("id").asLong()).isEqualTo(id);

            // 2. The customer subscribes to their conversation and writes without waiting for an agent (the request is waiting)
            BlockingQueue<JsonNode> clientFeed = client.subscribe("/topic/conversations." + id);
            client.send("/app/conversations/" + id + "/messages", Map.of("content", "Bonjour, j'ai un souci avec ma réservation"));
            JsonNode echo = StompTestClient.next(clientFeed);
            assertThat(echo.get("type").asString()).isEqualTo("MESSAGE");
            assertThat(echo.get("message").get("content").asString()).isEqualTo("Bonjour, j'ai un souci avec ma réservation");
            assertThat(echo.get("message").get("authorName").asString()).isEqualTo("Camille D.");

            // 3. The agent claims the conversation: the customer is notified, the queue is updated
            api.post(BASE + "/" + id + "/claim", agentToken, null);
            assertThat(StompTestClient.next(queue).get("type").asString()).isEqualTo("CLAIMED");
            JsonNode claimed = StompTestClient.next(clientFeed);
            assertThat(claimed.get("type").asString()).isEqualTo("CLAIMED");
            assertThat(claimed.get("conversation").get("agentName").asString()).isEqualTo("Sophie M.");

            // 4. The agent subscribes, sees the history over REST, then replies; the customer receives it without reloading
            BlockingQueue<JsonNode> agentFeed = agent.subscribe("/topic/conversations." + id);
            assertThat(api.get(BASE + "/" + id + "/messages", agentToken).json().size()).isEqualTo(1);

            agent.send("/app/conversations/" + id + "/typing", Map.of("typing", true));
            JsonNode typing = StompTestClient.next(clientFeed);
            assertThat(typing.get("type").asString()).isEqualTo("TYPING");
            assertThat(typing.get("typing").asBoolean()).isTrue();

            agent.send("/app/conversations/" + id + "/messages", Map.of("content", "Bonjour, je regarde cela tout de suite"));
            JsonNode reply = StompTestClient.next(clientFeed);
            assertThat(reply.get("type").asString()).isEqualTo("MESSAGE");
            assertThat(reply.get("message").get("authorName").asString()).isEqualTo("Sophie M.");
            // the agent also received: typing (echoed to themselves) then the message
            assertThat(StompTestClient.next(agentFeed).get("type").asString()).isEqualTo("TYPING");
            assertThat(StompTestClient.next(agentFeed).get("type").asString()).isEqualTo("MESSAGE");

            // 5. Everything is in the database: a reload (or a reconnection) finds both messages
            JsonNode history = api.get(BASE + "/" + id + "/messages", clientToken).json();
            assertThat(history.size()).isEqualTo(2);
            long firstId = history.get(0).get("id").asLong();
            JsonNode catchUp = api.get(BASE + "/" + id + "/messages?afterId=" + firstId, clientToken).json();
            assertThat(catchUp.size()).isEqualTo(1);
            assertThat(catchUp.get(0).get("content").asString()).isEqualTo("Bonjour, je regarde cela tout de suite");

            // 6. Close: the customer is informed in the chat, and can no longer write in this conversation
            api.post(BASE + "/" + id + "/close", agentToken, null);
            assertThat(StompTestClient.next(clientFeed).get("type").asString()).isEqualTo("CLOSED");
            client.send("/app/conversations/" + id + "/messages", Map.of("content", "Merci !"));
            assertThat(StompTestClient.next(client.errors).get("code").asString()).isEqualTo("CONFLICT");
        }
    }

    @Test
    @DisplayName("Un message vide est refusé et l'erreur n'est renvoyée qu'à son expéditeur")
    void emptyMessageIsRejected() throws Exception {
        TestApi api = new TestApi(port);
        String clientToken = api.login("client1@example.com");
        long id = api.post(BASE, clientToken, null).json().get("id").asLong();

        try (StompTestClient client = StompTestClient.connect(port, clientToken, waiter)) {
            client.send("/app/conversations/" + id + "/messages", Map.of("content", "   "));

            JsonNode error = StompTestClient.next(client.errors);
            assertThat(error.get("code").asString()).isEqualTo("BAD_REQUEST");
            assertThat(api.get(BASE + "/" + id + "/messages", clientToken).json().size()).isZero();
        }
    }

    @Test
    @DisplayName("Un client ne peut pas s'abonner à la conversation d'un autre")
    void cannotSubscribeToSomeoneElsesConversation() throws Exception {
        TestApi api = new TestApi(port);
        String owner = api.login("client1@example.com");
        String intruder = api.login("client2@example.com");
        long id = api.post(BASE, owner, null).json().get("id").asLong();

        try (StompTestClient client = StompTestClient.connect(port, intruder, waiter)) {
            client.subscribeWithoutWaiting("/topic/conversations." + id);

            client.serverRejection.get(StompTestClient.TIMEOUT_SECONDS, TimeUnit.SECONDS); // ERROR frame from the server
        }
    }

    @Test
    @DisplayName("Un client ne peut pas écouter la file d'attente des conseillers")
    void clientCannotSubscribeToQueue() throws Exception {
        TestApi api = new TestApi(port);
        try (StompTestClient client = StompTestClient.connect(port, api.login("client1@example.com"), waiter)) {
            client.subscribeWithoutWaiting("/topic/support.queue");

            client.serverRejection.get(StompTestClient.TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Sans jeton valide, la connexion WebSocket est refusée")
    void connectionRequiresValidToken() {
        assertThatThrownBy(() -> StompTestClient.connect(port, null, waiter)).isNotNull();
        assertThatThrownBy(() -> StompTestClient.connect(port, "jeton.invalide.xyz", waiter)).isNotNull();
    }
}
