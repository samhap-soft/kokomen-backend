package com.samhap.kokomen.recruit.schedular.service;

import com.samhap.kokomen.recruit.schedular.dto.ApiResponse;
import com.samhap.kokomen.recruit.schedular.dto.PagedData;
import com.samhap.kokomen.recruit.schedular.dto.RecruitmentDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class RecruitmentApiClient {

    private static final String BASE_URL = "https://v2-api.zighang.com/api/recruitments";
    private static final int PAGE_SIZE = 11;
    private static final String DEPTH_ONE = "IT_개발";
    private static final String SORT_CONDITION = "LATEST";
    private static final String ORDER_CONDITION = "DESC";

    private final RestClient restClient;

    public RecruitmentApiClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public List<RecruitmentDto> fetchAllRecruitments() {
        List<RecruitmentDto> allRecruitments = new ArrayList<>();
        int currentPage = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                PagedData<RecruitmentDto> pagedData = fetchPage(currentPage);

                if (pagedData != null && pagedData.getContent() != null) {
                    int fetchedCount = pagedData.getContent().size();
                    allRecruitments.addAll(pagedData.getContent());

                    log.info("페이지 {}/{} 수집 완료 - {} 건 (총 누적: {} 건)",
                            currentPage + 1,
                            pagedData.getTotalPages(),
                            fetchedCount,
                            allRecruitments.size());

                    hasMore = !pagedData.getLast();
                    currentPage++;
                } else {
                    log.info("페이지 {} 데이터가 비어있음", currentPage);
                    hasMore = false;
                }
            } catch (Exception e) {
                log.error("페이지 {} 수집 실패: {}", currentPage, e.getMessage(), e);
                hasMore = false;
            }
        }

        log.info("=== API 데이터 수집 완료 - 총 {} 건 ===", allRecruitments.size());
        return allRecruitments;
    }

    private PagedData<RecruitmentDto> fetchPage(int page) {
        java.net.URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("page", page)
                .queryParam("size", PAGE_SIZE)
                .queryParam("depthOnes[]", DEPTH_ONE)
                .queryParam("sortCondition", SORT_CONDITION)
                .queryParam("orderCondition", ORDER_CONDITION)
                .build()
                .encode()
                .toUri();

        log.info("API 요청: {}", uri);

        ApiResponse<PagedData<RecruitmentDto>> response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assert response != null;
        log.info("받은 내용: {}", Objects.requireNonNull(response.getData().getContent()));

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return response.getData();
        } else {
            log.error("API 응답 실패: {}", response);
            return null;
        }
    }
}
