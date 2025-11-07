package com.samhap.kokomen.recruit.schedular.service;

import com.samhap.kokomen.recruit.schedular.dto.ApiResponse;
import com.samhap.kokomen.recruit.schedular.dto.PagedData;
import com.samhap.kokomen.recruit.schedular.dto.RecruitmentDto;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
        PaginationState state = new PaginationState();

        while (state.hasMore()) {
            PagedData<RecruitmentDto> pagedData = fetchPageSafely(state.getCurrentPage());

            if (pagedData == null) {
                state.stop();
                continue;
            }

            processPageData(pagedData, allRecruitments, state);
        }

        return allRecruitments;
    }

    private PagedData<RecruitmentDto> fetchPageSafely(int page) {
        try {
            return fetchPage(page);
        } catch (Exception e) {
            log.error("페이지 {} 수집 실패: {}", page, e.getMessage(), e);
            return null;
        }
    }

    private void processPageData(PagedData<RecruitmentDto> pagedData, List<RecruitmentDto> accumulator,
                                 PaginationState state) {
        if (isEmptyPage(pagedData)) {
            log.info("페이지 {} 데이터가 비어있음", state.getCurrentPage());
            state.stop();
            return;
        }

        List<RecruitmentDto> content = pagedData.getContent();
        accumulator.addAll(content);

        if (Boolean.TRUE.equals(pagedData.getLast())) {
            state.stop();
        } else {
            state.nextPage();
        }
    }

    private boolean isEmptyPage(PagedData<RecruitmentDto> pagedData) {
        return pagedData == null || pagedData.getContent() == null || pagedData.getContent().isEmpty();
    }

    private PagedData<RecruitmentDto> fetchPage(int page) {
        URI uri = buildApiUri(page);

        ApiResponse<PagedData<RecruitmentDto>> response = executeApiRequest(uri);
        validateResponse(response, page);

        return Boolean.TRUE.equals(response.getSuccess()) ? response.getData() : null;
    }

    private URI buildApiUri(int page) {
        return UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("page", page)
                .queryParam("size", PAGE_SIZE)
                .queryParam("depthOnes[]", DEPTH_ONE)
                .queryParam("sortCondition", SORT_CONDITION)
                .queryParam("orderCondition", ORDER_CONDITION)
                .build()
                .encode()
                .toUri();
    }

    private ApiResponse<PagedData<RecruitmentDto>> executeApiRequest(URI uri) {
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void validateResponse(ApiResponse<PagedData<RecruitmentDto>> response, int page) {
        if (response == null) {
            throw new IllegalStateException("API 응답이 null입니다 (페이지: " + page + ")");
        }
        if (response.getData() == null) {
            throw new IllegalStateException("API 응답 데이터가 null입니다 (페이지: " + page + ")");
        }
        if (response.getData().getContent() == null) {
            throw new IllegalStateException("API 응답 컨텐츠가 null입니다 (페이지: " + page + ")");
        }
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            log.error("API 응답 실패 (페이지: {}): {}", page, response);
        }
    }
}
