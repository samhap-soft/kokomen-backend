package com.samhap.kokomen.recruit.controller;

import com.samhap.kokomen.recruit.service.RecruitService;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import com.samhap.kokomen.recruit.service.dto.RecruitPageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/recruits")
@RestController
public class RecruitController {

    private final RecruitService recruitService;

    @GetMapping("/filters")
    public ResponseEntity<FiltersResponse> getFilters() {
        return ResponseEntity.ok(recruitService.getFilters());
    }

    @GetMapping
    public ResponseEntity<RecruitPageResponse> getRecruits(
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) List<String> employeeType,
            @RequestParam(required = false) List<String> education,
            @RequestParam(required = false) List<String> employment,
            @RequestParam(required = false) List<String> deadlineType,
            @RequestParam(required = false) Integer careerMin,
            @RequestParam(required = false) Integer careerMax,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(recruitService.getRecruits(
                region,
                employeeType,
                education,
                employment,
                deadlineType,
                careerMin,
                careerMax,
                pageable
        ));
    }
}
