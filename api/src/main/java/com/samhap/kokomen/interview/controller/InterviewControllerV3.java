package com.samhap.kokomen.interview.controller;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.service.InterviewFacadeService;
import com.samhap.kokomen.interview.service.dto.RootQuestionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v3/interview")
@RestController
public class InterviewControllerV3 {

    private final InterviewFacadeService interviewFacadeService;

    @GetMapping("/questions")
    public ResponseEntity<List<RootQuestionResponse>> getRootQuestions(
            @RequestParam(name = "category") Category category
    ) {
        return ResponseEntity.ok(interviewFacadeService.getRootQuestionsByCategory(category));
    }
}
