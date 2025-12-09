package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@AllArgsConstructor
public class ResumeEvaluationAsyncRequest {

    @NotNull(message = "이력서 파일은 필수입니다.")
    private MultipartFile resume;

    private MultipartFile portfolio;

    @NotBlank(message = "직무는 필수입니다.")
    private String jobPosition;

    private String jobDescription;

    @NotBlank(message = "경력은 필수입니다.")
    private String jobCareer;
}
