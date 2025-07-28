package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.service.event.InterviewViewCountEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Component
public class TestInterviewViewCountEventListener {

    private final List<InterviewViewCountEvent> events = new ArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(InterviewViewCountEvent event) {
        events.add(event);
    }

    public List<InterviewViewCountEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
} 