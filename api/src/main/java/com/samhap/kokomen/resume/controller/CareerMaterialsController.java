package com.samhap.kokomen.resume.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/evaluations")
    public ResponseEntity<ResumeEvaluationSubmitResponse> submitResumeEvaluationAsync(
            @RequestBody @Valid ResumeEvaluationAsyncRequest request,
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(careerMaterialsFacadeService.submitResumeEvaluationAsync(request, memberAuth));
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
            Pageable pageable
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
