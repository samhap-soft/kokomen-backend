package com.samhap.kokomen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// cd 테스트를 위해 변경사항 만들기 용 주석
@EnableRetry
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class KokomenApplication {

    public static void main(String[] args) {
        SpringApplication.run(KokomenApplication.class, args);
    }
}
