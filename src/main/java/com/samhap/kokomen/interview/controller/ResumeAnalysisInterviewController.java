package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.service.InterviewStartFacadeService;
import com.samhap.kokomen.interview.service.dto.resumeanalysis.ResumeAnalysisInterviewStartRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews/resume-analyses")
@RestController
public class ResumeAnalysisInterviewController {

    private final InterviewStartFacadeService interviewStartFacadeService;

    /**
     * 평가와 생성 질문은 게스트에게도 공개되지만, 그 질문으로 면접을 시작하는 것은 회원만 가능하다.
     * 그래서 {@code @Authentication}을 {@code required = true}(기본값)로 둔다.
     */
    @PostMapping("/{analysisId}")
    public ResponseEntity<InterviewStartResponse> startResumeAnalysisInterview(
            @PathVariable String analysisId,
            @RequestBody @Valid ResumeAnalysisInterviewStartRequest request,
            @Authentication MemberAuth memberAuth
    ) {
        InterviewStartResponse response = interviewStartFacadeService.startResumeAnalysisInterview(
                parseAnalysisId(analysisId), request, memberAuth);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 자원을 지목하지 못하는 경로 변수는 잘못된 입력(400)이 아니라 미존재(404)로 답하고 값을 되싣지 않는다.
    private Long parseAnalysisId(String analysisId) {
        try {
            return Long.parseLong(analysisId.trim());
        } catch (NumberFormatException e) {
            throw new NotFoundException("존재하지 않는 이력서 분석입니다.");
        }
    }
}
