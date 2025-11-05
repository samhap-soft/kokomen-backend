package com.samhap.kokomen.recruit.service;

import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Recruit;
import com.samhap.kokomen.recruit.domain.Region;
import com.samhap.kokomen.recruit.repository.RecruitRepository;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import com.samhap.kokomen.recruit.service.dto.RecruitPageResponse;
import com.samhap.kokomen.recruit.service.dto.RecruitSummaryResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RecruitService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String CAREER_MIN_FIELD = "careerMin";
    private static final String CAREER_MAX_FIELD = "careerMax";

    private final RecruitRepository recruitRepository;

    public FiltersResponse getFilters() {
        List<String> deadlineTypes = DeadlineType.getNames();
        List<String> educations = Education.getNames();
        List<String> employeeTypes = EmployeeType.getNames();
        List<String> employments = Employment.getNames();
        List<String> regions = Region.getNames();

        return new FiltersResponse(
                deadlineTypes,
                educations,
                employeeTypes,
                employments,
                regions
        );
    }

    @Transactional(readOnly = true)
    public RecruitPageResponse getRecruits(
            List<String> region,
            List<String> employeeType,
            List<String> education,
            List<String> employment,
            List<String> deadlineType,
            Integer careerMin,
            Integer careerMax,
            Pageable pageable
    ) {
        Specification<Recruit> spec = buildSpecification(
                region, employeeType, education, employment, deadlineType, careerMin, careerMax
        );

        Page<Recruit> recruitPage = recruitRepository.findAll(spec, pageable);

        List<RecruitSummaryResponse> data = recruitPage.getContent().stream()
                .filter(recruit -> Objects.equals(recruit.getAffiliate().getName(), "V1"))
                .map(RecruitSummaryResponse::from)
                .toList();

        return new RecruitPageResponse(
                data,
                recruitPage.getNumber(),
                recruitPage.getTotalPages(),
                recruitPage.hasNext()
        );
    }

    private Specification<Recruit> buildSpecification(
            List<String> regionNames,
            List<String> employeeTypeNames,
            List<String> educationNames,
            List<String> employmentNames,
            List<String> deadlineTypeNames,
            Integer careerMin,
            Integer careerMax
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            addCollectionFilter(predicates, root, regionNames, Region::findByName, "regions");
            addCollectionFilter(predicates, root, employeeTypeNames, EmployeeType::findByName, "employeeTypes");
            addCollectionFilter(predicates, root, educationNames, Education::findByName, "educations");
            addCollectionFilter(predicates, root, employmentNames, Employment::findByName, "employments");
            addDeadlineTypeFilter(predicates, root, deadlineTypeNames);
            addCareerFilter(predicates, root, cb, careerMin, careerMax);

            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private <T extends Enum<T>> void addCollectionFilter(
            List<Predicate> predicates,
            Root<Recruit> root,
            List<String> names,
            Function<String, T> converter,
            String joinField
    ) {
        if (names == null || names.isEmpty()) {
            return;
        }

        List<T> enums = names.stream()
                .map(converter)
                .toList();
        Join<Object, Object> join = root.join(joinField, JoinType.INNER);
        predicates.add(join.in(enums));
    }

    private void addDeadlineTypeFilter(
            List<Predicate> predicates,
            Root<Recruit> root,
            List<String> deadlineTypeNames
    ) {
        if (deadlineTypeNames == null || deadlineTypeNames.isEmpty()) {
            return;
        }

        List<DeadlineType> deadlineTypes = deadlineTypeNames.stream()
                .map(DeadlineType::valueOf)
                .toList();
        predicates.add(root.get("deadlineType").in(deadlineTypes));
    }

    private void addCareerFilter(
            List<Predicate> predicates,
            Root<Recruit> root,
            CriteriaBuilder cb,
            Integer careerMin,
            Integer careerMax
    ) {
        if (careerMin == null && careerMax == null) {
            return;
        }

        if (careerMin != null && careerMax != null) {
            Predicate careerOverlap = cb.and(
                    cb.or(
                            cb.isNull(root.get(CAREER_MAX_FIELD)),
                            cb.greaterThanOrEqualTo(root.get(CAREER_MAX_FIELD), careerMin)
                    ),
                    cb.or(
                            cb.isNull(root.get(CAREER_MIN_FIELD)),
                            cb.lessThanOrEqualTo(root.get(CAREER_MIN_FIELD), careerMax)
                    )
            );
            predicates.add(careerOverlap);
            return;
        }

        if (careerMin != null) {
            predicates.add(
                    cb.or(
                            cb.isNull(root.get(CAREER_MAX_FIELD)),
                            cb.greaterThanOrEqualTo(root.get(CAREER_MAX_FIELD), careerMin)
                    )
            );
            return;
        }

        predicates.add(
                cb.or(
                        cb.isNull(root.get(CAREER_MIN_FIELD)),
                        cb.lessThanOrEqualTo(root.get(CAREER_MIN_FIELD), careerMax)
                )
        );
    }
}
