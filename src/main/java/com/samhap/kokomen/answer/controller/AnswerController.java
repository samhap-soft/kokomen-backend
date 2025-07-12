package com.samhap.kokomen.answer.controller;

import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<AnswerMemoResponse> createAnswerMemo(
            @PathVariable Long answerId,
            @RequestBody AnswerMemoCreateRequest answerMemoCreateRequest,
            @Authentication MemberAuth memberAuth
    ) {
        AnswerMemoResponse answerMemoResponse = answerService.createAnswerMemo(answerId, answerMemoCreateRequest, memberAuth);
        return ResponseEntity.ok(answerMemoResponse);
    }

    @PatchMapping("/{answerId}/memo")
    public ResponseEntity<Void> updateMemo(
            @PathVariable Long answerId,
            @RequestBody AnswerMemoUpdateRequest answerMemoUpdateRequest,
            @Authentication MemberAuth memberAuth
    ) {
        answerService.updateAnswerMemo(answerId, answerMemoUpdateRequest, memberAuth);
        return ResponseEntity.noContent().build();
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
