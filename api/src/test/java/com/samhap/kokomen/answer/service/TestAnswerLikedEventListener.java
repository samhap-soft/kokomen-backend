package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.service.event.AnswerLikedEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TestAnswerLikedEventListener {

    private final List<AnswerLikedEvent> events = new ArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnswerLikedEvent event) {
        events.add(event);
    }

    public List<AnswerLikedEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
