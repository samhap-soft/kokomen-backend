package com.samhap.kokomen.interview.service.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.constant.AwsConstant;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * 저장 자료의 content 캐시가 DB에 실제로 남는지 검증한다.
 * 제출 경로({@code ResumeAnalysisFacadeService.submitMemberAnalysis})는 트랜잭션 없이 저장 자료를 읽어 이 서비스에
 * 넘기고 운영은 OSIV off라, 이 서비스가 받는 엔티티는 언제나 준영속이다. 그래서 테스트도 호출마다 트랜잭션 밖에서
 * 리포지토리로 새로 읽은 엔티티를 넘긴다. 한 인스턴스를 다시 넘기면 메모리에서 바뀐 값만으로 캐시 적중처럼 보여
 * DB에 저장되지 않는 결함이 가려진다.
 */
class ResumeContentServiceTest extends BaseTest {

    private static final byte[] PDF_BYTES = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String EXTRACTED_TEXT = "S3에서 내려받은 PDF에서 추출한 원문";

    @Autowired
    private ResumeContentService resumeContentService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberResumeRepository memberResumeRepository;
    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;

    @BeforeEach
    void setUpDownloadAndExtraction() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PDF_BYTES));
        given(pdfTextExtractor.extractTextWithLinks(any(byte[].class))).willReturn(EXTRACTED_TEXT);
    }

    @Test
    void content가_비어_있는_저장_이력서는_추출한_원문을_DB에_캐시한다() {
        // given
        Long resumeId = saveResumeWithoutContent().getId();

        // when
        String content = resumeContentService.getOrExtractResumeContent(readDetachedResume(resumeId));

        // then
        assertAll(
                () -> assertThat(content).isEqualTo(EXTRACTED_TEXT),
                () -> assertThat(readDetachedResume(resumeId).getContent()).isEqualTo(EXTRACTED_TEXT)
        );
    }

    @Test
    void 같은_저장_이력서로_다시_제출하면_S3에서_다시_내려받지_않는다() {
        // given
        Long resumeId = saveResumeWithoutContent().getId();
        resumeContentService.getOrExtractResumeContent(readDetachedResume(resumeId));

        // when: 제출마다 저장 자료를 새로 읽으므로 두 번째 호출도 DB에서 다시 읽은 엔티티로 한다.
        String content = resumeContentService.getOrExtractResumeContent(readDetachedResume(resumeId));

        // then
        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
        assertThat(content).isEqualTo(EXTRACTED_TEXT);
    }

    @Test
    void content가_비어_있는_저장_포트폴리오는_추출한_원문을_DB에_캐시한다() {
        // given
        Long portfolioId = savePortfolioWithoutContent().getId();

        // when
        String content = resumeContentService.getOrExtractPortfolioContent(readDetachedPortfolio(portfolioId));

        // then
        assertAll(
                () -> assertThat(content).isEqualTo(EXTRACTED_TEXT),
                () -> assertThat(readDetachedPortfolio(portfolioId).getContent()).isEqualTo(EXTRACTED_TEXT)
        );
    }

    @Test
    void 같은_저장_포트폴리오로_다시_제출하면_S3에서_다시_내려받지_않는다() {
        // given
        Long portfolioId = savePortfolioWithoutContent().getId();
        resumeContentService.getOrExtractPortfolioContent(readDetachedPortfolio(portfolioId));

        // when: 제출마다 저장 자료를 새로 읽으므로 두 번째 호출도 DB에서 다시 읽은 엔티티로 한다.
        String content = resumeContentService.getOrExtractPortfolioContent(readDetachedPortfolio(portfolioId));

        // then
        verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
        assertThat(content).isEqualTo(EXTRACTED_TEXT);
    }

    private MemberResume saveResumeWithoutContent() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        return memberResumeRepository.save(MemberResumeFixtureBuilder.builder()
                .member(member)
                .resumeUrl(AwsConstant.CLOUD_FRONT_DOMAIN_URL + "resume/saved.pdf")
                .content(null)
                .build());
    }

    private MemberPortfolio savePortfolioWithoutContent() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        return memberPortfolioRepository.save(MemberPortfolioFixtureBuilder.builder()
                .member(member)
                .portfolioUrl(AwsConstant.CLOUD_FRONT_DOMAIN_URL + "portfolio/saved.pdf")
                .content(null)
                .build());
    }

    // 트랜잭션 밖의 조회라 반환 시점에 영속성 컨텍스트가 이미 닫혀 있다. 제출 경로의 findSavedMaterials와 같은 조건이다.
    private MemberResume readDetachedResume(Long resumeId) {
        return memberResumeRepository.findById(resumeId).orElseThrow();
    }

    private MemberPortfolio readDetachedPortfolio(Long portfolioId) {
        return memberPortfolioRepository.findById(portfolioId).orElseThrow();
    }
}
