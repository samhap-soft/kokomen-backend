package com.samhap.kokomen.member.service;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.service.TokenService;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class MemberSchedulerService {

    private final MemberRepository memberRepository;
    private final TokenService tokenService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void rechargeDailyFreeToken() {
        List<Member> allMembers = memberRepository.findAll();

        for (Member member : allMembers) {
            tokenService.setFreeTokens(member.getId(), TokenService.DAILY_FREE_TOKEN_COUNT);
        }

        log.info("일일 무료 토큰 {}개 재충전 완료 - 대상 회원 수: {}", TokenService.DAILY_FREE_TOKEN_COUNT, allMembers.size());
    }
}
