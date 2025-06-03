package com.samhap.kokomen.interview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.InterviewService;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewTotalResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
@RestController
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<InterviewResponse> startInterview(
            @RequestBody InterviewRequest interviewRequest
    ) {
        // TODO: MemberAuth를 활용해서 인터뷰를 시작하는 유저의 정보를 가져와야 함
        return ResponseEntity.ok(interviewService.startInterview(interviewRequest, new MemberAuth(1L)));
    }

    // TODO: MemberAuth를 활용해서 인터뷰를 진행하는 유저의 정보를 가져와야 함
    @PostMapping("/{interviewId}/questions/{curQuestionId}/answers")
    public ResponseEntity<?> proceedInterview(
            @PathVariable Long interviewId,
            @PathVariable Long curQuestionId,
            @RequestBody AnswerRequest answerRequest
    ) {
        return interviewService.proceedInterview(interviewId, curQuestionId, answerRequest, new MemberAuth(1L))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{interviewId}/result")
    public ResponseEntity<InterviewTotalResponse> findTotalFeedbacks(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(interviewService.findTotalFeedbacks(interviewId, new MemberAuth(1L)));
    }
}
