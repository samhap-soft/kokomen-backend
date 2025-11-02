package com.samhap.kokomen.member.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MemberStreakResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import com.samhap.kokomen.member.service.dto.ProfileUpdateRequest;
import com.samhap.kokomen.member.service.dto.RankingPageResponse;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
@RestController
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me/profile")
    public ResponseEntity<MyProfileResponse> findMyProfile(
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(memberService.findMember(memberAuth));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody @Valid ProfileUpdateRequest profileUpdateRequest,
            @Authentication MemberAuth memberAuth
    ) {
        memberService.updateProfile(memberAuth, profileUpdateRequest);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<RankingResponse>> findRanking(
            @PageableDefault(size = 30, sort = "score", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(memberService.findRanking(pageable));
    }

    @GetMapping("v2/ranking")
    public ResponseEntity<RankingPageResponse> findRankingPage(
            @PageableDefault(size = 30, sort = "score", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(memberService.findRankingPage(pageable));
    }

    @GetMapping("/ranking/v3")
    public ResponseEntity<RankingPageResponse> findRankingPageV3(
            @PageableDefault(size = 30, sort = "score", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(memberService.findRankingPageV3(pageable));
    }

    @GetMapping("/me/streaks")
    public ResponseEntity<MemberStreakResponse> findMemberStreaks(
            @Authentication MemberAuth memberAuth,
            @RequestParam("start_date") LocalDate startDate,
            @RequestParam("end_date") LocalDate endDate
    ) {
        return ResponseEntity.ok(memberService.findMemberStreaks(memberAuth, startDate, endDate));
    }
}
