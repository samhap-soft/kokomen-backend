package com.samhap.kokomen.recruit.schedular;

import com.samhap.kokomen.recruit.schedular.service.RecruitmentDataService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecruitmentScheduler {

    private final RecruitmentDataService recruitmentDataService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void collectRecruitmentData() {
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("====================================================================");
        log.info("  Scheduled Recruitment Data Collection Started at {}", startTime);
        log.info("====================================================================");

        try {
            recruitmentDataService.fetchAndSaveAllRecruitments();

            String endTime = LocalDateTime.now().format(FORMATTER);
            log.info("====================================================================");
            log.info("  Scheduled Recruitment Data Collection Completed at {}", endTime);
            log.info("====================================================================");

        } catch (Exception e) {
            log.error("====================================================================");
            log.error("  Scheduled Recruitment Data Collection FAILED", e);
            log.error("  Error: {}", e.getMessage());
            log.error("====================================================================");
        }
    }
}
