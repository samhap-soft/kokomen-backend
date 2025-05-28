package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.interview.service.InterviewService;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.NextQuestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
@RestController
public class InterviewController {

    private final InterviewService interviewService;

    // TODO: MemberAuth를 활용해서 인터뷰를 진행하는 유저의 정보를 가져와야 함
    @PostMapping("/{interviewId}/questions/{questionId}/answers")
    public NextQuestionResponse proceedInterview(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @RequestBody AnswerRequest answerRequest
    ) {
        return interviewService.proceedInterview(interviewId, questionId, answerRequest, null);
    }
}
