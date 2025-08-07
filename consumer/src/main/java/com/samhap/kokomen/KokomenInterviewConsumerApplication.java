package com.samhap.kokomen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// cd 테스트를 위해 변경사항 만들기 용 주석
@EnableJpaAuditing
@SpringBootApplication
public class KokomenInterviewConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KokomenInterviewConsumerApplication.class, args);
    }
}
