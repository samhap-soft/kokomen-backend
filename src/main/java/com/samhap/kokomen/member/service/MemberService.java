package com.samhap.kokomen.member.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public MemberResponse findOrCreateByKakaoId(Long kakaoId, String nickname) {
        Member member = memberRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> memberRepository.save(new Member(kakaoId, nickname)));

        return new MemberResponse(member);
    }

    public MyProfileResponse findMember(MemberAuth memberAuth) {
        Member member = memberRepository.findById(memberAuth.memberId())
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));

        return new MyProfileResponse(member);
    }
}
