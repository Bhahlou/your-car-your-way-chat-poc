package com.yourcaryourway.chat;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.TimeUnit;

/**
 * The in-memory broker sends no STOMP RECEIPT: a test that sends right after "subscribe" could therefore
 * precede the registration of the subscription on the server side. We poll the broker's subscription registry
 * until one more subscriber shows up. (In a real browser, the front end does "subscribe then REST catch-up".)
 */
final class SubscriptionWaiter {

    private final SimpleBrokerMessageHandler broker;

    SubscriptionWaiter(SimpleBrokerMessageHandler broker) {
        this.broker = broker;
    }

    int subscribers(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return broker.getSubscriptionRegistry().findSubscriptions(probe).size();
    }

    void awaitMoreThan(String destination, int before) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(StompTestClient.TIMEOUT_SECONDS);
        while (subscribers(destination) <= before) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Abonnement à " + destination + " non enregistré par le serveur");
            }
            Thread.sleep(10);
        }
    }
}
