package com.samhap.kokomen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// cd 트리거용 더미 주석
@EnableJpaAuditing
@SpringBootApplication
public class KokomenInterviewConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KokomenInterviewConsumerApplication.class, args);
    }
}
