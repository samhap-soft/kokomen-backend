package com.samhap.kokomen.member.service;

import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class MemberSchedulerService {

    private final TokenRepository tokenRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void rechargeDailyFreeToken() {
        int updatedCount = tokenRepository.updateAllMembersFreeTokens(
                TokenService.DAILY_FREE_TOKEN_COUNT,
                TokenType.FREE
        );

        log.info("일일 무료 토큰 {}개 재충전 완료 - 업데이트된 회원 수: {}", TokenService.DAILY_FREE_TOKEN_COUNT, updatedCount);
    }
}
