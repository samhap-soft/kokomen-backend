package com.samhap.kokomen.member.service;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberSchedulerService {

    private final MemberRepository memberRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void rechargeDailyFreeToken() {
        memberRepository.rechargeDailyFreeToken(Member.DAILY_FREE_TOKEN_COUNT);
    }
}
