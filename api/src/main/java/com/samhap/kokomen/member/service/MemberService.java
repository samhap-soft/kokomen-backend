package com.samhap.kokomen.member.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import com.samhap.kokomen.member.service.dto.ProfileUpdateRequest;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

    public Member readById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    public MyProfileResponse findMember(MemberAuth memberAuth) {
        Member member = readById(memberAuth.memberId());
        long rank = memberRepository.findRankByScore(member.getScore());
        long totalMemberCount = memberRepository.count();
        return new MyProfileResponse(member, totalMemberCount, rank);
    }

    public List<RankingResponse> findRanking(Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        return RankingResponse.createRankingResponses(memberRepository.findRankings(limit, offset));
    }

    public void validateEnoughTokenCount(Long memberId, int tokenCount) {
        Member member = readById(memberId);
        if (!member.hasEnoughTokenCount(tokenCount)) {
            throw new BadRequestException("토큰 갯수가 부족합니다.");
        }
    }

    @Transactional
    public void updateProfile(MemberAuth memberAuth, ProfileUpdateRequest profileUpdateRequest) {
        Member member = readById(memberAuth.memberId());
        member.updateProfile(profileUpdateRequest.nickname());
    }

    @Transactional
    public void useToken(Long memberId) {
        Member member = readById(memberId);
        member.useToken();
    }

    @Transactional
    public void withdraw(Member member) {
        member.withdraw();
    }
}
