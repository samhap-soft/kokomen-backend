package com.samhap.kokomen.interview.service.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.constant.AwsConstant;
import com.samhap.kokomen.global.exception.BadRequestException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
    // 실제로는 같은 PDF라 두 추출 결과가 같다. 덮어쓰기를 관측하려고 늦게 끝난 쪽만 다른 값을 돌려준다.
    private static final String LATE_EXTRACTED_TEXT = "늦게 끝난 추출의 원문";

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

    // content가 비어 있는 것을 본 뒤 추출하는 사이에 다른 제출이 캐시를 먼저 채운 경우다. 늦게 끝난 쪽이 행을
    // 통째로 다시 쓰면(준영속 엔티티를 save로 merge하는 식) 먼저 쓴 캐시가 덮인다.
    @Test
    void 먼저_채워진_이력서_캐시는_늦게_끝난_추출이_덮어쓰지_않는다() {
        // given
        Long resumeId = saveResumeWithoutContent().getId();
        MemberResume first = readDetachedResume(resumeId);
        MemberResume late = readDetachedResume(resumeId);
        given(pdfTextExtractor.extractTextWithLinks(any(byte[].class)))
                .willReturn(EXTRACTED_TEXT, LATE_EXTRACTED_TEXT);
        resumeContentService.getOrExtractResumeContent(first);

        // when
        resumeContentService.getOrExtractResumeContent(late);

        // then
        assertThat(readDetachedResume(resumeId).getContent()).isEqualTo(EXTRACTED_TEXT);
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

    @Test
    void 먼저_채워진_포트폴리오_캐시는_늦게_끝난_추출이_덮어쓰지_않는다() {
        // given
        Long portfolioId = savePortfolioWithoutContent().getId();
        MemberPortfolio first = readDetachedPortfolio(portfolioId);
        MemberPortfolio late = readDetachedPortfolio(portfolioId);
        given(pdfTextExtractor.extractTextWithLinks(any(byte[].class)))
                .willReturn(EXTRACTED_TEXT, LATE_EXTRACTED_TEXT);
        resumeContentService.getOrExtractPortfolioContent(first);

        // when
        resumeContentService.getOrExtractPortfolioContent(late);

        // then
        assertThat(readDetachedPortfolio(portfolioId).getContent()).isEqualTo(EXTRACTED_TEXT);
    }

    // 다운로드와 PDF 추출은 오래 걸린다. 트랜잭션을 열면 그동안 DB 커넥션을 붙잡으므로 트랜잭션 밖에서 내려받아야 한다.
    @Test
    void 저장_이력서_PDF는_트랜잭션_밖에서_내려받는다() {
        // given
        Long resumeId = saveResumeWithoutContent().getId();
        AtomicBoolean downloadedInTransaction = recordTransactionOnDownload();

        // when
        resumeContentService.getOrExtractResumeContent(readDetachedResume(resumeId));

        // then
        assertThat(downloadedInTransaction.get()).isFalse();
    }

    @Test
    void 저장_포트폴리오_PDF는_트랜잭션_밖에서_내려받는다() {
        // given
        Long portfolioId = savePortfolioWithoutContent().getId();
        AtomicBoolean downloadedInTransaction = recordTransactionOnDownload();

        // when
        resumeContentService.getOrExtractPortfolioContent(readDetachedPortfolio(portfolioId));

        // then
        assertThat(downloadedInTransaction.get()).isFalse();
    }

    @Test
    void 저장_이력서_PDF를_내려받지_못하면_추출_실패_예외가_나고_캐시는_비어_있다() {
        // given
        Long resumeId = saveResumeWithoutContent().getId();
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(S3Exception.builder().message("다운로드 실패").build());

        // when & then
        assertThatThrownBy(() -> resumeContentService.getOrExtractResumeContent(readDetachedResume(resumeId)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("이력서에서 텍스트를 추출하는 데 실패했습니다.");
        assertThat(readDetachedResume(resumeId).getContent()).isNull();
    }

    @Test
    void 저장_포트폴리오_PDF를_내려받지_못하면_추출_실패_예외가_나고_캐시는_비어_있다() {
        // given
        Long portfolioId = savePortfolioWithoutContent().getId();
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(S3Exception.builder().message("다운로드 실패").build());

        // when & then
        assertThatThrownBy(() -> resumeContentService.getOrExtractPortfolioContent(
                readDetachedPortfolio(portfolioId)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("포트폴리오에서 텍스트를 추출하는 데 실패했습니다.");
        assertThat(readDetachedPortfolio(portfolioId).getContent()).isNull();
    }

    private AtomicBoolean recordTransactionOnDownload() {
        AtomicBoolean downloadedInTransaction = new AtomicBoolean(true);
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).willAnswer(invocation -> {
            downloadedInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PDF_BYTES);
        });
        return downloadedInTransaction;
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
