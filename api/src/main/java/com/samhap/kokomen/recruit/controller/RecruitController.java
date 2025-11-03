package com.samhap.kokomen.recruit.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import com.samhap.kokomen.recruit.service.RecruitService;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/recruits")
@RestController
public class RecruitController {

    private final RecruitService recruitService;

    @GetMapping("/filters")
    public ResponseEntity<FiltersResponse> getFilters() {
        return ResponseEntity.ok(recruitService.getFilters());
    }
}
