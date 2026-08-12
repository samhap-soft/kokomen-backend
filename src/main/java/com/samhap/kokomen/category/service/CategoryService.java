package com.samhap.kokomen.category.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.category.service.dto.CategoryResponse;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.repository.OnboardingSurveyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CategoryService {

    private final OnboardingSurveyRepository onboardingSurveyRepository;

    public List<CategoryResponse> findCategories(MemberAuth memberAuth) {
        return readOrderedCategories(memberAuth)
                .stream()
                .map(CategoryResponse::new)
                .toList();
    }

    /**
     * 온보딩 설문을 작성한 회원은 선호 순으로, 비회원이거나 설문을 작성하지 않은 회원은 기본 선언 순서로 받는다.
     */
    private List<Category> readOrderedCategories(MemberAuth memberAuth) {
        if (!memberAuth.isAuthenticated()) {
            return Category.getCategories();
        }
        return onboardingSurveyRepository.findByMemberId(memberAuth.memberId())
                .map(onboardingSurvey -> onboardingSurvey.toCategoryPreference()
                        .sortByPreference(Category.getCategories()))
                .orElseGet(Category::getCategories);
    }
}
