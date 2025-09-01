package com.samhap.kokomen.member.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MyProfileResponseV2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/members")
@RestController
public class MemberControllerV2 {

    private final MemberService memberService;

    @GetMapping("/me/profile")
    public ResponseEntity<MyProfileResponseV2> findMyProfile(
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(memberService.findMemberV2(memberAuth));
    }
}
