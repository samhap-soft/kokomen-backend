package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewProceedGptFlowAsyncService {

    private final GptClient gptClient;

    public GptResponse asyncRequestToGptFlow(QuestionAndAnswers questionAndAnswers) {
        return gptClient.requestToGpt(questionAndAnswers);
    }
}
