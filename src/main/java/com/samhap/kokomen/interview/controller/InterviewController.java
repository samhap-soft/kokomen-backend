package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.service.InterviewService;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.InterviewTotalResponse;
import com.samhap.kokomen.interview.service.dto.MyInterviewResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// TODO: request DTO 변환 시 API 스펙에 맞지 않게 요청이 오면 적절한 예외메시지가 나가도록 처리
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
@RestController
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<InterviewStartResponse> startInterview(
            @RequestBody InterviewRequest interviewRequest,
            MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(interviewService.startInterview(interviewRequest, memberAuth));
    }

    @PostMapping("/{interviewId}/questions/{curQuestionId}/answers")
    public ResponseEntity<?> proceedInterview(
            @PathVariable Long interviewId,
            @PathVariable Long curQuestionId,
            @RequestBody AnswerRequest answerRequest,
            MemberAuth memberAuth
    ) {
        return interviewService.proceedInterview(interviewId, curQuestionId, answerRequest, memberAuth)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{interviewId}/result")
    public ResponseEntity<InterviewTotalResponse> findTotalFeedbacks(
            @PathVariable Long interviewId,
            MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(interviewService.findTotalFeedbacks(interviewId, memberAuth));
    }

    @GetMapping("/{interviewId}")
    public ResponseEntity<InterviewResponse> findInterview(
            @PathVariable Long interviewId,
            MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(interviewService.findInterview(interviewId, memberAuth));
    }

    @GetMapping("/me")
    public ResponseEntity<List<MyInterviewResponse>> findMyInterviews(
            @RequestParam(required = false) InterviewState state,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(interviewService.findMyInterviews(memberAuth, state, pageable));
    }
}
