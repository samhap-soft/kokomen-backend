package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import com.samhap.kokomen.interview.service.InterviewFacadeService;
import com.samhap.kokomen.interview.service.ResumeBasedInterviewService;
import com.samhap.kokomen.interview.service.dto.GeneratedQuestionsResponse;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationStateResponse;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationSubmitResponse;
import com.samhap.kokomen.interview.service.dto.ResumeBasedInterviewStartRequest;
import com.samhap.kokomen.interview.service.dto.ResumeBasedQuestionGenerateRequest;
import com.samhap.kokomen.interview.service.dto.ResumeQuestionGenerationPageResponse;
import com.samhap.kokomen.interview.service.dto.ResumeQuestionUsageStatusResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews/resume-based")
@RestController
public class ResumeBasedInterviewController {

    private final ResumeBasedInterviewService resumeBasedInterviewService;
    private final InterviewFacadeService interviewFacadeService;

    @PostMapping(value = "/questions/generate", consumes = {"multipart/form-data"})
    public ResponseEntity<QuestionGenerationSubmitResponse> generateQuestions(
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @RequestPart(value = "resume_id", required = false) String resumeIdStr,
            @RequestPart(value = "portfolio_id", required = false) String portfolioIdStr,
            @RequestPart(value = "job_career") String jobCareer,
            @Authentication MemberAuth memberAuth
    ) {
        ResumeBasedQuestionGenerateRequest request = new ResumeBasedQuestionGenerateRequest(
                resume,
                portfolio,
                parseIdOrNull(resumeIdStr),
                parseIdOrNull(portfolioIdStr),
                jobCareer
        );

        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                memberAuth.memberId(),
                request
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/usage-status")
    public ResponseEntity<ResumeQuestionUsageStatusResponse> getUsageStatus(
            @Authentication MemberAuth memberAuth
    ) {
        ResumeQuestionUsageStatusResponse response = resumeBasedInterviewService.getUsageStatus(
                memberAuth.memberId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/questions/generations")
    public ResponseEntity<ResumeQuestionGenerationPageResponse> findMyQuestionGenerations(
            @RequestParam(required = false) ResumeQuestionGenerationState state,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Authentication MemberAuth memberAuth
    ) {
        ResumeQuestionGenerationPageResponse response = resumeBasedInterviewService.findMyQuestionGenerations(
                memberAuth.memberId(),
                state,
                pageable
        );
        return ResponseEntity.ok(response);
    }

    private Long parseIdOrNull(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(idStr.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("잘못된 ID 형식입니다: " + idStr);
        }
    }

    @GetMapping("/{resumeBasedInterviewResultId}/check")
    public ResponseEntity<QuestionGenerationStateResponse> getGenerationStatus(
            @PathVariable Long resumeBasedInterviewResultId,
            @Authentication MemberAuth memberAuth
    ) {
        QuestionGenerationStateResponse response = resumeBasedInterviewService.getQuestionGenerationStatus(
                resumeBasedInterviewResultId,
                memberAuth.memberId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{resumeBasedInterviewResultId}")
    public ResponseEntity<List<GeneratedQuestionsResponse>> getGeneratedQuestions(
            @PathVariable Long resumeBasedInterviewResultId,
            @Authentication MemberAuth memberAuth
    ) {
        List<GeneratedQuestionsResponse> response = resumeBasedInterviewService.getGeneratedQuestions(
                resumeBasedInterviewResultId,
                memberAuth.memberId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{resumeBasedInterviewResultId}")
    public ResponseEntity<InterviewStartResponse> startResumeBasedInterview(
            @PathVariable Long resumeBasedInterviewResultId,
            @RequestBody @Valid ResumeBasedInterviewStartRequest request,
            @Authentication MemberAuth memberAuth
    ) {
        InterviewStartResponse response = interviewFacadeService.startResumeBasedInterview(
                resumeBasedInterviewResultId,
                request,
                memberAuth
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
