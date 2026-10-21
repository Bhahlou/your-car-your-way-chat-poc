package com.yourcaryourway.chat.support.web;

import com.yourcaryourway.chat.support.service.ChatException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates business errors into HTTP responses in the standard ProblemDetail format (RFC 9457). */
@RestControllerAdvice(basePackageClasses = ConversationController.class)
class ChatExceptionHandler {

    @ExceptionHandler(ChatException.class)
    ProblemDetail onChatException(ChatException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    /** Two simultaneous openings for the same customer: the partial unique index lets only one through. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflit : l'opération a déjà été effectuée");
    }
}
