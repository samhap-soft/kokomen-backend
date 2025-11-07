package com.samhap.kokomen;

import com.samhap.kokomen.recruit.schedular.service.RecruitmentDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableRetry
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class KokomenApplication {
    public static void main(String[] args) {
        SpringApplication.run(KokomenApplication.class, args);
    }

    @Bean
    CommandLineRunner run(RecruitmentDataService recruitmentDataService) {
        return args -> recruitmentDataService.fetchAndSaveAllRecruitments();
    }
}
