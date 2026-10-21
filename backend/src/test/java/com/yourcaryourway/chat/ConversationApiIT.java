package com.yourcaryourway.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Support REST flow: opening, waiting queue, claiming, access rights, closing. */
class ConversationApiIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/support/conversations";

    @Test
    @DisplayName("Un client ouvre une conversation ; la rouvrir renvoie la même (US-8.1)")
    void clientOpensOneConversationAtATime() {
        TestApi api = new TestApi(port);
        String client = api.login("client1@example.com");

        TestApi.Response first = api.post(BASE, client, Map.of("reservationId", 42));
        TestApi.Response second = api.post(BASE, client, null);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.json().get("status").asString()).isEqualTo("WAITING");
        assertThat(first.json().get("reservationId").asLong()).isEqualTo(42);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().get("id").asLong()).isEqualTo(first.json().get("id").asLong());
    }

    @Test
    @DisplayName("Sans jeton, l'API répond 401")
    void anonymousIsRejected() {
        assertThat(new TestApi(port).get(BASE, null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("La file d'attente est réservée aux conseillers et triée par ancienneté (US-8.4)")
    void queueIsForAgentsOnly() {
        TestApi api = new TestApi(port);
        String client1 = api.login("client1@example.com");
        String client2 = api.login("client2@example.com");
        String agent = api.login("agent1@example.com");

        long first = api.post(BASE, client1, null).json().get("id").asLong();
        long second = api.post(BASE, client2, null).json().get("id").asLong();

        assertThat(api.get(BASE + "/queue", client1).status()).isEqualTo(403);
        TestApi.Response queue = api.get(BASE + "/queue", agent);
        assertThat(queue.status()).isEqualTo(200);
        assertThat(queue.json().size()).isEqualTo(2);
        assertThat(queue.json().get(0).get("id").asLong()).isEqualTo(first);
        assertThat(queue.json().get(1).get("id").asLong()).isEqualTo(second);
    }

    @Test
    @DisplayName("Une conversation n'est prise en charge que par un seul conseiller (US-8.4)")
    void onlyOneAgentCanClaim() {
        TestApi api = new TestApi(port);
        String client = api.login("client1@example.com");
        String agent1 = api.login("agent1@example.com");
        String agent2 = api.login("agent2@example.com");
        long id = api.post(BASE, client, null).json().get("id").asLong();

        TestApi.Response won = api.post(BASE + "/" + id + "/claim", agent1, null);
        TestApi.Response lost = api.post(BASE + "/" + id + "/claim", agent2, null);

        assertThat(won.status()).isEqualTo(200);
        assertThat(won.json().get("status").asString()).isEqualTo("OPEN");
        assertThat(lost.status()).isEqualTo(409);
        // it left the waiting queue
        assertThat(api.get(BASE + "/queue", agent2).json().size()).isZero();
        assertThat(api.get(BASE + "/assigned", agent1).json().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Chacun ne voit que ses conversations (confidentialité du tchat)")
    void conversationsArePrivate() {
        TestApi api = new TestApi(port);
        String client1 = api.login("client1@example.com");
        String client2 = api.login("client2@example.com");
        String agent1 = api.login("agent1@example.com");
        String agent2 = api.login("agent2@example.com");
        long id = api.post(BASE, client1, null).json().get("id").asLong();
        api.post(BASE + "/" + id + "/claim", agent1, null);

        assertThat(api.get(BASE + "/" + id, client1).status()).isEqualTo(200);
        assertThat(api.get(BASE + "/" + id, agent1).status()).isEqualTo(200);
        assertThat(api.get(BASE + "/" + id, client2).status()).isEqualTo(403);
        assertThat(api.get(BASE + "/" + id + "/messages", client2).status()).isEqualTo(403);
        assertThat(api.get(BASE + "/" + id, agent2).status()).isEqualTo(403); // not their case
        assertThat(api.get(BASE + "/999999", client1).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Le conseiller clôture ; le client peut ensuite en ouvrir une nouvelle (US-8.5)")
    void closeThenReopen() {
        TestApi api = new TestApi(port);
        String client = api.login("client1@example.com");
        String agent = api.login("agent1@example.com");
        long id = api.post(BASE, client, null).json().get("id").asLong();
        api.post(BASE + "/" + id + "/claim", agent, null);

        assertThat(api.post(BASE + "/" + id + "/close", client, null).status()).isEqualTo(403);
        TestApi.Response closed = api.post(BASE + "/" + id + "/close", agent, null);
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.json().get("status").asString()).isEqualTo("CLOSED");
        assertThat(api.post(BASE + "/" + id + "/close", agent, null).status()).isEqualTo(409);

        TestApi.Response reopened = api.post(BASE, client, null);
        assertThat(reopened.status()).isEqualTo(201);
        assertThat(reopened.json().get("id").asLong()).isNotEqualTo(id);
        // the old one stays available in the history (US-8.3)
        assertThat(api.get(BASE, client).json().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Identifiants invalides : message générique, sans dire lequel est faux (US-1.3)")
    void loginErrorIsGeneric() {
        TestApi api = new TestApi(port);
        TestApi.Response wrongPassword = api.post("/api/v1/auth/login", null,
            Map.of("email", "client1@example.com", "password", "mauvais"));
        TestApi.Response unknownEmail = api.post("/api/v1/auth/login", null,
            Map.of("email", "inconnu@example.com", "password", "mauvais"));

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
    }
}
