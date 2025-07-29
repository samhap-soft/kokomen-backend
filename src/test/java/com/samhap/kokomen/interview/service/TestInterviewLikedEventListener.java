package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.service.event.InterviewLikedEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TestInterviewLikedEventListener {

    private final List<InterviewLikedEvent> events = new ArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(InterviewLikedEvent event) {
        events.add(event);
    }

    public List<InterviewLikedEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
