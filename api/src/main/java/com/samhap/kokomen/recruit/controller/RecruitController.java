package com.samhap.kokomen.recruit.controller;

import com.samhap.kokomen.recruit.service.RecruitService;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import com.samhap.kokomen.recruit.service.dto.RecruitPageResponse;
import jakarta.websocket.server.PathParam;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
            @PathParam("region") List<String> region,
            @PathParam("employeeType") List<String> employeeType,
            @PathParam("education") List<String> education,
            @PathParam("employment") List<String> employment,
            @PathParam("deadlineType") List<String> deadlineType,
            @PathParam("careerMin") Integer careerMin,
            @PathParam("careerMax") Integer careerMax,
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
