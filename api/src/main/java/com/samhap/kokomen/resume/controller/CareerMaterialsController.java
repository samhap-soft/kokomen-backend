package com.samhap.kokomen.resume.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.service.CareerMaterialsFacadeService;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
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
}
