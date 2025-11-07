package com.samhap.kokomen.recruit.schedular.service;

import com.samhap.kokomen.recruit.domain.Affiliate;
import com.samhap.kokomen.recruit.domain.Company;
import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Recruit;
import com.samhap.kokomen.recruit.domain.Region;
import com.samhap.kokomen.recruit.repository.AffiliateRepository;
import com.samhap.kokomen.recruit.repository.CompanyRepository;
import com.samhap.kokomen.recruit.repository.RecruitRepository;
import com.samhap.kokomen.recruit.schedular.dto.CompanyDto;
import com.samhap.kokomen.recruit.schedular.dto.RecruitmentDto;
import com.samhap.kokomen.recruit.schedular.dto.mapper.DeadlineTypeMapper;
import com.samhap.kokomen.recruit.schedular.dto.mapper.EducationMapper;
import com.samhap.kokomen.recruit.schedular.dto.mapper.EmployeeTypeMapper;
import com.samhap.kokomen.recruit.schedular.dto.mapper.EmploymentMapper;
import com.samhap.kokomen.recruit.schedular.dto.mapper.RegionMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentDataService {

    private final RecruitmentApiClient apiClient;
    private final AffiliateRepository affiliateRepository;
    private final CompanyRepository companyRepository;
    private final RecruitRepository recruitRepository;
    private final ImageDownloadService imageDownloadService;

    @Transactional
    public void fetchAndSaveAllRecruitments() {
        log.info("=== 채용 데이터 수집 및 저장 시작 ===");

        List<RecruitmentDto> recruitments = apiClient.fetchAllRecruitments();

        int savedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        List<String> failedImageUrls = new ArrayList<>();

        int i = 0;
        for (RecruitmentDto dto : recruitments) {
            log.info("현재 순서: {}", ++i);
            try {
                if (recruitRepository.existsByExternalId(dto.getId())) {
                    log.debug("이미 존재하는 채용공고: {}", dto.getId());
                    skippedCount++;
                    continue;
                }

                Affiliate affiliate = getOrCreateAffiliate(dto.getAffiliate());

                Company company = getOrCreateCompany(dto.getCompany(), failedImageUrls);

                Recruit recruit = convertToEntity(dto, affiliate, company);
                recruitRepository.save(recruit);

                log.debug("채용공고 저장 완료: {} - {}", dto.getId(), dto.getTitle());
                savedCount++;

            } catch (Exception e) {
                log.error("채용공고 처리 실패: {} - {}", dto.getId(), e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("=== 채용 데이터 수집 및 저장 완료 ===");
        log.info("총 조회: {} 건", recruitments.size());
        log.info("저장 성공: {} 건", savedCount);
        log.info("중복 스킵: {} 건", skippedCount);
        log.info("처리 실패: {} 건", errorCount);

        if (!failedImageUrls.isEmpty()) {
            log.warn("=================================================");
            log.warn("이미지 다운로드 실패 목록 ({} 건):", failedImageUrls.size());
            log.warn("=================================================");
            failedImageUrls.forEach(url -> log.warn("  - {}", url));
            log.warn("=================================================");
        } else {
            log.info("모든 이미지 다운로드 성공!");
        }
    }

    private Affiliate getOrCreateAffiliate(String affiliateName) {
        return affiliateRepository.findByName(affiliateName)
                .orElseGet(() -> {
                    log.info("새로운 Affiliate 생성: {}", affiliateName);

                    String image = getAffiliateImage(affiliateName);

                    Affiliate newAffiliate = new Affiliate(null, affiliateName, image);
                    return affiliateRepository.save(newAffiliate);
                });
    }

    private String getAffiliateImage(String affiliateName) {
        switch (affiliateName) {
            case "원티드":
                return "https://d2ftfzru2cd49g.cloudfront.net/recruit/affiliate/wanted.svg";
            case "그룹바이":
                return "https://d2ftfzru2cd49g.cloudfront.net/recruit/affiliate/groupby.svg";
            case "랠릿":
                return "https://d2ftfzru2cd49g.cloudfront.net/recruit/affiliate/rallit.svg";
            case "로켓펀치":
                return "https://d2ftfzru2cd49g.cloudfront.net/recruit/affiliate/rocket.svg";
            default:
                return null;
        }
    }

    private Company getOrCreateCompany(CompanyDto dto, List<String> failedImageUrls) {
        return companyRepository.findByExternalId(dto.getId())
                .orElseGet(() -> {
                    log.debug("새로운 Company 생성: {} ({})", dto.getName(), dto.getId());

                    String imageUrl = null;

                    if (dto.getImage() != null && !dto.getImage().isEmpty()) {
                        String relativePath = imageDownloadService.downloadAndSaveImage(dto.getImage(), dto.getId());

                        if (relativePath != null) {
                            imageUrl = "https://d2ftfzru2cd49g.cloudfront.net/recruit/company/" + relativePath;
                        } else {
                            failedImageUrls.add(dto.getImage());
                            imageUrl = null;
                        }
                    }

                    Company newCompany = new Company(null, dto.getId(), dto.getName(), imageUrl);
                    return companyRepository.save(newCompany);
                });
    }

    private Recruit convertToEntity(RecruitmentDto dto, Affiliate affiliate, Company company) {
        String url = "https://zighang.com/recruitment/" + dto.getId();

        LocalDateTime endDate = parseEndDate(dto.getEndDate());

        DeadlineType deadlineType = DeadlineTypeMapper.mapDeadlineType(dto.getDeadlineType());

        Set<Region> regions = mapToEnumSet(dto.getRegion(), RegionMapper::mapRegion);
        Set<EmployeeType> employeeTypes = mapToEnumSet(dto.getEmployeeType(), EmployeeTypeMapper::mapEmployeeType);
        Set<Education> educations = mapToEnumSet(dto.getEducation(), EducationMapper::mapEducation);
        Set<Employment> employments = mapToEnumSet(dto.getDepthTwo(), EmploymentMapper::mapEmployment);

        return new Recruit(
                null,
                dto.getId(),
                affiliate,
                dto.getTitle(),
                endDate,
                deadlineType,
                dto.getCareerMin(),
                dto.getCareerMax(),
                url,
                company,
                regions,
                employeeTypes,
                educations,
                employments,
                null,
                null
        );
    }

    private LocalDateTime parseEndDate(String endDateStr) {
        if (endDateStr == null || endDateStr.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(endDateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("endDate 파싱 실패: {}", endDateStr);
            return null;
        }
    }

    private <T extends Enum<T>> Set<T> mapToEnumSet(List<String> stringList,
                                                    java.util.function.Function<String, T> mapper) {
        if (stringList == null || stringList.isEmpty()) {
            return Set.of();
        }

        return stringList.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
