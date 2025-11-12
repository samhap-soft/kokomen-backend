package com.samhap.kokomen.recruit.schedular;

import com.samhap.kokomen.recruit.schedular.service.RecruitmentDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecruitmentScheduler {

    private final RecruitmentDataService recruitmentDataService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void collectRecruitmentData() {
        try {
            log.info("채용 공고 수집 작업 시작");
            recruitmentDataService.fetchAndSaveAllRecruitments();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
