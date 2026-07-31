package com.samhap.kokomen.resume.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.resume.service.ResumeAnalysisFacadeService;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisClaimRequest;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisClaimResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisPageResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionRetryResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitRequest;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisUsageStatusResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/resume-analyses")
@RestController
public class ResumeAnalysisController {

    private final ResumeAnalysisFacadeService resumeAnalysisFacadeService;

    /**
     * job_position·job_career를 {@code required = false}로 받는 것이 계약이다. {@code required = true}로 두면 파트
     * 누락 시 Spring이 MissingServletRequestPartException을 던져 전역 Exception 핸들러의 500으로 나가고,
     * 400 + "지원 직무는 필수입니다." / "경력 사항은 필수입니다."에 도달할 수 없다.
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ResumeAnalysisSubmitResponse> submitResumeAnalysis(
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @RequestPart(value = "resume_id", required = false) String resumeIdStr,
            @RequestPart(value = "portfolio_id", required = false) String portfolioIdStr,
            @RequestPart(value = "job_position", required = false) String jobPosition,
            @RequestPart(value = "job_description", required = false) String jobDescription,
            @RequestPart(value = "job_career", required = false) String jobCareer,
            @Authentication(required = false) MemberAuth memberAuth,
            ClientIp clientIp
    ) {
        ResumeAnalysisSubmitRequest request = new ResumeAnalysisSubmitRequest(resume, portfolio,
                parseIdOrNull(resumeIdStr), parseIdOrNull(portfolioIdStr), jobPosition, jobDescription, jobCareer);
        if (memberAuth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(resumeAnalysisFacadeService.submitMemberAnalysis(memberAuth.memberId(), request));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(resumeAnalysisFacadeService.submitGuestAnalysis(request, clientIp));
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

    @GetMapping("/usage-status")
    public ResponseEntity<ResumeAnalysisUsageStatusResponse> findUsageStatus(
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(resumeAnalysisFacadeService.findUsageStatus(memberAuth.memberId()));
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<ResumeAnalysisResponse> findResumeAnalysis(
            @PathVariable String analysisId,
            @RequestParam(value = "guest_token", required = false) String guestToken,
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(resumeAnalysisFacadeService.findAnalysis(
                parseAnalysisId(analysisId), memberAuth, guestToken));
    }

    private Long parseAnalysisId(String analysisId) {
        try {
            return Long.parseLong(analysisId.trim());
        } catch (NumberFormatException e) {
            throw new NotFoundException("존재하지 않는 이력서 분석입니다.");
        }
    }

    @GetMapping
    public ResponseEntity<ResumeAnalysisPageResponse> findMyResumeAnalyses(
            @RequestParam(required = false) String state,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(resumeAnalysisFacadeService.findMyAnalyses(memberAuth.memberId(), state, pageable));
    }

    @PostMapping("/claim")
    public ResponseEntity<ResumeAnalysisClaimResponse> claimGuestResumeAnalysis(
            @RequestBody @Valid ResumeAnalysisClaimRequest request,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(resumeAnalysisFacadeService.claimGuestAnalysis(request.guestToken(), memberAuth));
    }

    @PostMapping("/{analysisId}/questions/retry")
    public ResponseEntity<ResumeAnalysisQuestionRetryResponse> retryQuestionGeneration(
            @PathVariable String analysisId,
            @RequestParam(value = "guest_token", required = false) String guestToken,
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        ResumeAnalysisQuestionRetryResponse response = resumeAnalysisFacadeService.retryQuestionGeneration(
                parseAnalysisId(analysisId), memberAuth, guestToken);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
