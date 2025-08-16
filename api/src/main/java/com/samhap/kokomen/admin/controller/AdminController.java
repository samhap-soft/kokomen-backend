package com.samhap.kokomen.admin.controller;

import com.samhap.kokomen.admin.service.AdminService;
import com.samhap.kokomen.admin.service.dto.RootQuestionVoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: Admin 기능이 많아진다면, 다른 모듈로 분리
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@RestController
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/root-question/{rootQuestionId}/upload-voice")
    public ResponseEntity<RootQuestionVoiceResponse> uploadRootQuestionVoice(@PathVariable Long rootQuestionId) {
        return ResponseEntity.ok(adminService.uploadRootQuestionVoice(rootQuestionId));
    }
}
