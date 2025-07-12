package com.samhap.kokomen.answer.controller;

import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.answer.service.dto.AnswerMemoRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/answers")
@RestController
public class AnswerController {

    private final AnswerService answerService;

    @PostMapping("/{answerId}/like")
    public ResponseEntity<Void> likeAnswer(
            @PathVariable Long answerId,
            @Authentication MemberAuth memberAuth
    ) {
        answerService.likeAnswer(answerId, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{answerId}/memo")
    public ResponseEntity<AnswerMemoResponse> createMemo(
            @PathVariable Long answerId,
            @RequestBody AnswerMemoRequest answerMemoRequest,
            @Authentication MemberAuth memberAuth
    ) {
        AnswerMemoResponse answerMemoResponse = answerService.createMemo(answerId, answerMemoRequest, memberAuth);
        return ResponseEntity.ok(answerMemoResponse);
    }

    @DeleteMapping("/{answerId}/like")
    public ResponseEntity<Void> unlikeAnswer(
            @PathVariable Long answerId,
            @Authentication MemberAuth memberAuth
    ) {
        answerService.unlikeAnswer(answerId, memberAuth);
        return ResponseEntity.noContent().build();
    }
}
