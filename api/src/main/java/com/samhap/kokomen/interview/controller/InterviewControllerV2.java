package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.InterviewFacadeService;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedStateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/interviews")
@RestController
public class InterviewControllerV2 {

    private final InterviewFacadeService interviewFacadeService;

    @PostMapping("/{interviewId}/questions/{curQuestionId}/answers")
    public ResponseEntity<Void> proceedInterviewBlockAsync(
            @PathVariable Long interviewId,
            @PathVariable Long curQuestionId,
            @RequestBody AnswerRequest answerRequest,
            @Authentication MemberAuth memberAuth
    ) {
        interviewFacadeService.proceedInterviewByBedrockFlow(interviewId, curQuestionId, answerRequest, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{interviewId}/questions/{curQuestionId}")
    public ResponseEntity<InterviewProceedStateResponse> findInterviewProceedState(
            @PathVariable Long interviewId,
            @PathVariable Long curQuestionId,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(interviewFacadeService.findInterviewProceedState(interviewId, curQuestionId, memberAuth));
    }
}
