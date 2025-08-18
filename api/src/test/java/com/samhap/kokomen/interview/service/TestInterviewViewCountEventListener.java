package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.service.event.InterviewViewCountEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TestInterviewViewCountEventListener {

    private final List<InterviewViewCountEvent> events = new ArrayList<>();

    @EventListener(condition = "T(com.samhap.kokomen.interview.service.event.InterviewNotificationListener).isOneOrPowerOfTen(#event.viewCount())")
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
