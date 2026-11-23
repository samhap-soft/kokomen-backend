package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.SystemDesignInterviewFacadeService;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews/system-design")
@RestController
public class SystemDesignInterviewController {

    private final SystemDesignInterviewFacadeService systemDesignInterviewFacadeService;

    // 요청 본문은 받지 않는다. 문제는 서버가 ACTIVE BASIC 문제 중에서 고른다.
    @PostMapping
    public ResponseEntity<SystemDesignInterviewStartResponse> startInterview(
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(systemDesignInterviewFacadeService.startInterview(memberAuth));
    }
}
