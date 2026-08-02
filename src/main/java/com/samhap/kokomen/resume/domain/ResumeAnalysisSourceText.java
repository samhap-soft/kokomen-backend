package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추출 원문 보관용 1:1 사이드 테이블. 고빈도 폴링이 부모 행만 읽도록 LONGTEXT를 분리한다.
 * analysis_id를 공유 PK로 쓰지 않고 별도 id AUTO_INCREMENT PK를 두는 이유는
 * H2AutoIncrementCleaner가 ALTER TABLE resume_analysis_source_text ALTER COLUMN ID를 실행하기 때문이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "resume_analysis_source_text",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rast_analysis_id", columnNames = "analysis_id")
        })
public class ResumeAnalysisSourceText extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    private ResumeAnalysis analysis;

    @Lob
    @Column(name = "resume_content", nullable = false, columnDefinition = "LONGTEXT")
    private String resumeContent;

    @Lob
    @Column(name = "portfolio_content", columnDefinition = "LONGTEXT")
    private String portfolioContent;

    public ResumeAnalysisSourceText(ResumeAnalysis analysis, String resumeContent, String portfolioContent) {
        this.analysis = analysis;
        this.resumeContent = resumeContent;
        this.portfolioContent = portfolioContent;
    }

    public boolean hasPortfolioContent() {
        return portfolioContent != null && !portfolioContent.isBlank();
    }
}
