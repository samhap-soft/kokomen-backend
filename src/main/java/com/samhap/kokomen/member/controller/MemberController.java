package com.samhap.kokomen.member.controller;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
@RestController
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me/profile")
    public ResponseEntity<MyProfileResponse> findMyProfile(
            MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(memberService.findMember(memberAuth));
    }
}
