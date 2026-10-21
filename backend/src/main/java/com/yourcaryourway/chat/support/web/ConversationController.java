package com.yourcaryourway.chat.support.web;

import com.yourcaryourway.chat.support.service.Actor;
import com.yourcaryourway.chat.support.service.ConversationService;
import com.yourcaryourway.chat.support.service.ConversationView;
import com.yourcaryourway.chat.support.service.MessageView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Support REST API. Controllers hold no business rule: they extract the user
 * from the JWT and delegate
 * to the service, which checks data access rights. {@code @PreAuthorize} only
 * filters by role.
 */
@RestController
@RequestMapping("/api/v1/support/conversations")
class ConversationController {

    private final ConversationService service;

    ConversationController(ConversationService service) {
        this.service = service;
    }

    /**
     * Optional body: {@code reservationId} when the chat is opened from a
     * reservation's detail page.
     */
    record OpenConversationRequest(Long reservationId) {
    }

    // --- Customer ---

    /**
     * 201 if a conversation is created, 200 if the customer already had an ongoing
     * one.
     */
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    ResponseEntity<ConversationView> open(@RequestBody(required = false) OpenConversationRequest request,
            Authentication auth) {
        Long reservationId = request == null ? null : request.reservationId();
        ConversationService.OpenResult result = service.open(Actor.from(auth), reservationId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.conversation());
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    List<ConversationView> mine(Authentication auth) {
        return service.listForClient(Actor.from(auth));
    }

    // --- Agent ---

    @GetMapping("/queue")
    @PreAuthorize("hasRole('AGENT')")
    List<ConversationView> queue(Authentication auth) {
        return service.listQueue(Actor.from(auth));
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('AGENT')")
    List<ConversationView> assigned(Authentication auth) {
        return service.listAssigned(Actor.from(auth));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    ConversationView claim(@PathVariable long id, Authentication auth) {
        return service.claim(Actor.from(auth), id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('AGENT')")
    ConversationView close(@PathVariable long id, Authentication auth) {
        return service.close(Actor.from(auth), id);
    }

    // --- Participants ---

    @GetMapping("/{id}")
    ConversationView get(@PathVariable long id, Authentication auth) {
        return service.get(Actor.from(auth), id);
    }

    /**
     * Full history, or catch-up after a disconnection with
     * {@code ?afterId=<last message received>}.
     */
    @GetMapping("/{id}/messages")
    List<MessageView> messages(@PathVariable long id, @RequestParam(required = false) Long afterId,
            Authentication auth) {
        return service.listMessages(Actor.from(auth), id, afterId);
    }
}
