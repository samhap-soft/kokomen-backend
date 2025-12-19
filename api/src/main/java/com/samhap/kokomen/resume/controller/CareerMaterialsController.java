package com.samhap.kokomen.resume.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.service.CareerMaterialsFacadeService;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationAsyncRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationDetailResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationHistoryResponses;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationStateResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationSubmitResponse;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RequestMapping("/api/v1/resumes")
@RestController
public class CareerMaterialsController {

    private final CareerMaterialsFacadeService careerMaterialsFacadeService;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Void> uploadCareerMaterials(
            @Valid @ModelAttribute ResumeSaveRequest request,
            @Authentication MemberAuth memberAuth
    ) {
        careerMaterialsFacadeService.saveCareerMaterials(request, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<CareerMaterialsResponse> getCareerMaterials(
            @RequestParam(defaultValue = "ALL") CareerMaterialsType type,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(careerMaterialsFacadeService.getCareerMaterials(type, memberAuth));
    }

    @PostMapping(value = "/evaluations", consumes = {"multipart/form-data"})
    public ResponseEntity<ResumeEvaluationSubmitResponse> submitResumeEvaluationAsync(
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @RequestPart(value = "resume_id", required = false) String resumeIdStr,
            @RequestPart(value = "portfolio_id", required = false) String portfolioIdStr,
            @RequestPart(value = "job_position") String jobPosition,
            @RequestPart(value = "job_description", required = false) String jobDescription,
            @RequestPart(value = "job_career") String jobCareer,
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        Long resumeId = parseIdOrNull(resumeIdStr);
        Long portfolioId = parseIdOrNull(portfolioIdStr);
        ResumeEvaluationAsyncRequest request = new ResumeEvaluationAsyncRequest(
                resume, portfolio, resumeId, portfolioId, jobPosition, jobDescription, jobCareer);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(careerMaterialsFacadeService.submitResumeEvaluationAsync(request, memberAuth));
    }

    private Long parseIdOrNull(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(fileId.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("잘못된 파일 id 형식입니다: " + fileId);
        }
    }

    @GetMapping("/evaluations/{evaluationId}/state")
    public ResponseEntity<ResumeEvaluationStateResponse> findResumeEvaluationState(
            @PathVariable String evaluationId,
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(careerMaterialsFacadeService.findResumeEvaluationState(evaluationId, memberAuth));
    }

    @GetMapping("/evaluations")
    public ResponseEntity<ResumeEvaluationHistoryResponses> findResumeEvaluationHistory(
            @Authentication MemberAuth memberAuth,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(careerMaterialsFacadeService.findResumeEvaluationHistory(memberAuth, pageable));
    }

    @GetMapping("/evaluations/{evaluationId}")
    public ResponseEntity<ResumeEvaluationDetailResponse> findResumeEvaluationDetail(
            @PathVariable Long evaluationId,
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(careerMaterialsFacadeService.findResumeEvaluationDetail(evaluationId, memberAuth));
    }
}
