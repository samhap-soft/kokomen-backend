package com.samhap.kokomen;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DummyController {

    // 애플리케이션이 종료되지 않도록 더미 컨트롤러 생성

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
