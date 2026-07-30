package com.samhap.kokomen.resume.domain;

import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.JD_FIT;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROBLEM_SOLVING;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROJECT_EXPERIENCE;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.SOFT_SKILLS;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.TECHNICAL_SKILLS;

import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ResumeAnalysisWeights {

    JD_PROVIDED(new EnumMap<>(Map.of(
            PROBLEM_SOLVING, 0.25, PROJECT_EXPERIENCE, 0.25, TECHNICAL_SKILLS, 0.25,
            SOFT_SKILLS, 0.10, JD_FIT, 0.15))),
    JD_ABSENT(new EnumMap<>(Map.of(
            PROBLEM_SOLVING, 0.30, PROJECT_EXPERIENCE, 0.30, TECHNICAL_SKILLS, 0.30,
            SOFT_SKILLS, 0.10))),
    ;

    private final Map<ResumeAnalysisDimension, Double> weights;

    ResumeAnalysisWeights(Map<ResumeAnalysisDimension, Double> weights) {
        this.weights = weights;
    }

    public static ResumeAnalysisWeights of(boolean jdProvided) {
        return jdProvided ? JD_PROVIDED : JD_ABSENT;
    }

    public Double weightOf(ResumeAnalysisDimension dimension) {
        return weights.get(dimension);
    }

    public List<ResumeAnalysisDimension> dimensions() {
        return Arrays.stream(ResumeAnalysisDimension.values())
                .filter(weights::containsKey)
                .toList();
    }

    public int calculateTotalScore(ResumeAnalysisEvaluation evaluation) {
        Map<ResumeAnalysisDimension, Integer> scores = evaluation.scores();
        if (!scores.keySet().equals(weights.keySet())) {
            throw new ExternalApiException("이력서 분석 차원이 가중치 세트와 일치하지 않습니다. scores=" + scores.keySet());
        }
        double weightedSum = 0.0;
        for (Map.Entry<ResumeAnalysisDimension, Double> entry : weights.entrySet()) {
            Integer score = scores.get(entry.getKey());
            if (score == null) {
                throw new ExternalApiException("차원 점수가 비어 있습니다. dimension=" + entry.getKey());
            }
            weightedSum += entry.getValue() * score;
        }
        return (int) Math.round(weightedSum);
    }
}
