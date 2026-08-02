package com.samhap.kokomen.resume.external.dto;

import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.JD_FIT;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROBLEM_SOLVING;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROJECT_EXPERIENCE;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.SOFT_SKILLS;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.TECHNICAL_SKILLS;

import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 이력서 분석 tool 스키마의 provider 공용 사양. JD 제공 여부에 따라 차원 목록이 두 가지로 갈리며,
 * 런타임 재정규화 대신 두 목록을 명시적으로 선언한다.
 * 지표 키는 ResumeAnalysisDimension.toolKey()가 단일 소스다.
 */
public final class ResumeAnalysisSchema {

    public static final int SCORE_MIN = 0;
    public static final int SCORE_MAX = 100;
    public static final int BULLET_MIN_ITEMS = 2;
    public static final int BULLET_MAX_ITEMS = 6;
    public static final int QUESTION_MIN_ITEMS = 5;
    public static final int QUESTION_MAX_ITEMS = 7;
    public static final int QUESTION_MAX_LENGTH = 300;        // generated_question.content VARCHAR(1000)
    public static final int QUESTION_REASON_MAX_LENGTH = 600; // generated_question.reason VARCHAR(1000)
    public static final int FIELDS_PER_DIMENSION = 4;

    private static final Map<ResumeAnalysisDimension, String> SCORE_DESCRIPTIONS = new EnumMap<>(Map.of(
            PROBLEM_SOLVING, "0-100 점수. score_anchors 기준.",
            PROJECT_EXPERIENCE, "0-100 점수. score_anchors 기준.",
            TECHNICAL_SKILLS, "0-100 점수. score_anchors 기준.",
            SOFT_SKILLS, "0-100 점수. score_anchors 기준. 관찰 근거가 없으면 감점하지 않고 중립 기준점 50-59에서 시작한다.",
            JD_FIT, "0-100 점수. score_anchors 기준. 채용 공고 대조 결과만을 근거로 산출한다."));

    private ResumeAnalysisSchema() {
    }

    public static List<ResumeAnalysisDimension> dimensions(boolean jdProvided) {
        return ResumeAnalysisWeights.of(jdProvided).dimensions();
    }

    public static List<String> dimensionKeys(boolean jdProvided) {
        return dimensions(jdProvided).stream()
                .map(ResumeAnalysisDimension::toolKey)
                .toList();
    }

    public static String scoreDescription(ResumeAnalysisDimension dimension) {
        return SCORE_DESCRIPTIONS.get(dimension);
    }

    public static int requiredFieldCount(boolean jdProvided) {
        return dimensions(jdProvided).size() * FIELDS_PER_DIMENSION + 1;
    }
}
