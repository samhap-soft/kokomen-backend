package com.samhap.kokomen.resume.controller;

import com.samhap.kokomen.resume.service.CareerMaterialsFacadeService;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v3/resume")
@RestController
public class ResumeEvaluationController {

    private final CareerMaterialsFacadeService careerMaterialsFacadeService;

    @PostMapping("/evaluation")
    public ResponseEntity<ResumeEvaluationResponse> evaluateResume(
            @RequestBody @Valid ResumeEvaluationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(careerMaterialsFacadeService.evaluateResume(request));
    }
}
