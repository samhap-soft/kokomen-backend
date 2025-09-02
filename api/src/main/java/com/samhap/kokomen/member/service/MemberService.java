package com.samhap.kokomen.member.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponseV2;
import com.samhap.kokomen.member.service.dto.ProfileUpdateRequest;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.service.TokenService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final TokenService tokenService;
    
    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Transactional
    public Member saveKakaoMember(Long kakaoId, String nickname) {
        return memberRepository.save(new Member(kakaoId, nickname));
    }

    public Member readById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    public Member readByKakaoId(Long kakaoId) {
        return memberRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    public Optional<Member> findByKakaoId(Long kakaoId) {
        return memberRepository.findByKakaoId(kakaoId);
    }

    public MyProfileResponse findMember(MemberAuth memberAuth) {
        Member member = readById(memberAuth.memberId());
        long rank = memberRepository.findRankByScore(member.getScore());
        long totalMemberCount = memberRepository.count();
        Token freeToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.FREE);
        Token paidToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.PAID);
        boolean isTestUser = isTestUser(memberAuth.memberId());
        return new MyProfileResponse(member, totalMemberCount, rank, freeToken.getTokenCount() + paidToken.getTokenCount(), isTestUser);
    }

    public MyProfileResponseV2 findMemberV2(MemberAuth memberAuth) {
        Member member = readById(memberAuth.memberId());
        long rank = memberRepository.findRankByScore(member.getScore());
        long totalMemberCount = memberRepository.count();
        Token freeToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.FREE);
        Token paidToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.PAID);
        return new MyProfileResponseV2(member, totalMemberCount, rank, freeToken.getTokenCount(), paidToken.getTokenCount());
    }

    public List<RankingResponse> findRanking(Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        return RankingResponse.createRankingResponses(memberRepository.findRankings(limit, offset));
    }

    @Transactional
    public void updateProfile(MemberAuth memberAuth, ProfileUpdateRequest profileUpdateRequest) {
        Member member = readById(memberAuth.memberId());
        member.updateProfile(profileUpdateRequest.nickname());
    }

    @Transactional
    public void withdraw(Member member) {
        member.withdraw();
    }
    
    private boolean isTestUser(Long memberId) {
        if ("dev".equals(activeProfile)) {
            return memberId.equals(34L);
        } else if ("prod".equals(activeProfile)) {
            return memberId.equals(302L);
        }
        return false;
    }
}
