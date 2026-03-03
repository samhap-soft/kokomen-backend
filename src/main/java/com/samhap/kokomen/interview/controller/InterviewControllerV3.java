package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.InterviewQueryService;
import com.samhap.kokomen.interview.service.InterviewStartFacadeService;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v3/interview")
@RestController
public class InterviewControllerV3 {

    private final InterviewStartFacadeService interviewStartFacadeService;
    private final InterviewQueryService interviewQueryService;

    @GetMapping("/questions")
    public ResponseEntity<List<RootQuestionResponse>> getRootQuestions(
            @RequestParam(name = "category") Category category
    ) {
        return ResponseEntity.ok(interviewQueryService.getRootQuestionsByCategory(category));
    }

    @PostMapping("/custom")
    public ResponseEntity<InterviewStartResponse> createCustomInterview(
            @RequestBody @Valid RootQuestionCustomInterviewRequest request,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewStartFacadeService.startRootQuestionCustomInterview(request, memberAuth));
    }
}
