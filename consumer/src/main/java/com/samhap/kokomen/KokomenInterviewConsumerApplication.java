package com.samhap.kokomen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class KokomenInterviewConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KokomenInterviewConsumerApplication.class, args);
    }
}
