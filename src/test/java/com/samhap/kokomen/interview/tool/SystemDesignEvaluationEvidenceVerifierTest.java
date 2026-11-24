package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier.DowngradeReason;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier.VerifiedVerdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemDesignEvaluationEvidenceVerifierTest {

    private static final String FIFTH_QUESTION = "DB 서버가 내려가면 이미 보낸 링크는 어떻게 되나요?";
    private static final VerifiedVerdict KEPT_MET = new VerifiedVerdict(true, null);
    private static final VerifiedVerdict KEPT_NOT_MET = new VerifiedVerdict(false, null);

    // 출처는 면접자 답변 1~6번과 마지막 다이어그램(SystemDesignDiagramFixture.withCache)을 그린 줄이다.
    // 다이어그램 줄 예: "- cache: Redis 캐시 ⚡ [CACHE] (TTL: 1일, 정책: LRU)", "- api → cache : 조회"
    private final SystemDesignEvaluationEvidenceVerifier verifier = SystemDesignEvaluationEvidenceVerifier.of(
            new SystemDesignEndCommand(77L, SystemDesignProblemContextFixture.urlShortener(),
                    List.of(new SystemDesignRequirementTurn("하루에 몇 개나 만들어지나요?",
                            "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.")),
                    List.of(turn(1, "전체 구조를 그려 주세요.", "생성 요청은 API 서버가 받아 단축 키를 만들고 원래 URL과 함께 DB에 저장해요."),
                            turn(2, "단축 키는 어떻게 만드나요?",
                                    "DB 자동 증가 값을 Base62로 바꿔 단축 키를 만들어요. 값이 겹치지 않으니 키 충돌이 없어요."),
                            turn(3, "데이터가 얼마나 쌓일지 추정해 보세요.",
                                    "한 건을 500바이트로 잡으면 100GB 안팎이에요.\n단축 키를 기본 키로 하는 MySQL 테이블에 둬요."),
                            turn(4, "조회가 크게 늘면 어디가 먼저 병목이 되나요?",
                                    "조회가 많으니 API 서버 앞에 Redis 캐시를 두고, TTL은 1일 & LRU로 둬요."),
                            turn(5, FIFTH_QUESTION, "DB를 복제해 두고 주 DB가 내려가면 \"복제본\"으로 전환해요.")),
                    turn(6, "301과 302 중 무엇을 쓰나요?", "브라우저가 기억하는 301로 응답해 서버 부하를 줄여요."),
                    SystemDesignDiagramFixture.withCache(), List.of("DAILY_CREATION_VOLUME")));

    @Test
    void 인용이_답변에_있으면_MET을_유지한다() {
        String evidence = """
                [2] "DB 자동 증가 값을 Base62로 바꿔"
                [2] "값이 겹치지 않으니 키 충돌이 없어요\"""";

        assertThat(verifier.verify(true, evidence)).isEqualTo(KEPT_MET);
    }

    // 원문과 어미가 다르거나(바꿔 쓴 인용) 원문에 없는 수치를 넣은 인용은 근거가 아니다. 인용 하나만 틀려도 내린다.
    @Test
    void 인용이_답변에_없으면_NOT_MET으로_내린다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.UNMATCHED_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[3] \"한 건을 300바이트로 잡으면\"")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[2] \"DB 자동 증가 값을 Base62로 바꿈\"")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, """
                        [2] "DB 자동 증가 값을 Base62로 바꿔"
                        [2] "해시 충돌이 나면 다시 만들어요\"""")).isEqualTo(downgraded)
        );
    }

    @Test
    void 없음이_있으면_NOT_MET으로_내린다() {
        assertAll(
                () -> assertThat(verifier.verify(true, "없음: 저장 용량 추정"))
                        .isEqualTo(new VerifiedVerdict(false, DowngradeReason.MISSING_ELEMENT)),
                () -> assertThat(verifier.verify(true, "[3] \"MySQL 테이블에 둬요\" 없음 : 저장소 선택의 근거"))
                        .isEqualTo(new VerifiedVerdict(false, DowngradeReason.MISSING_ELEMENT))
        );
    }

    @Test
    void 요소_여러_개_중_하나만_없음이어도_NOT_MET으로_내린다() {
        String evidence = """
                [3] "한 건을 500바이트로 잡으면 100GB 안팎이에요"
                [3] "단축 키를 기본 키로 하는 MySQL 테이블에 둬요"
                없음: 보관 기간""";

        assertThat(verifier.verify(true, evidence))
                .isEqualTo(new VerifiedVerdict(false, DowngradeReason.MISSING_ELEMENT));
    }

    // 큰따옴표로 묶지 않은 문장은 원문 그대로여도 인용으로 치지 않는다. 작은따옴표도 인용 표시가 아니다.
    @Test
    void 인용이_없으면_NOT_MET으로_내린다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.NO_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[2] DB 자동 증가 값을 Base62로 바꿔 단축 키를 만들어요"))
                        .isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[2] 'DB 자동 증가 값을 Base62로 바꿔'")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "")).isEqualTo(downgraded)
        );
    }

    // 줄바꿈을 건너 이은 인용, 띄어쓰기·대소문자·끝 말줄임표가 다른 인용, 둥근 큰따옴표로 묶고 안의 따옴표를 작은따옴표로 바꾼 인용,
    // 원문의 큰따옴표가 그대로 끼어 둘로 나뉜 인용
    @Test
    void 공백_문장부호_따옴표_종류가_달라도_같은_문장이면_인정한다() {
        assertAll(
                () -> assertThat(verifier.verify(true, "[3] \"100gb 안팎이에요  단축 키를 기본키로 하는 mysql 테이블에 둬요…\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[5] “주 DB가 내려가면 '복제본'으로 전환해요.”")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[5] \"주 DB가 내려가면 \"복제본\"으로 전환해요\"")).isEqualTo(KEPT_MET)
        );
    }

    @Test
    void 다이어그램_줄_인용을_인정한다() {
        String evidence = """
                [6] "cache: Redis 캐시 ⚡ [CACHE]"
                [6] "api → cache : 조회\"""";

        assertThat(verifier.verify(true, evidence)).isEqualTo(KEPT_MET);
    }

    // 인용 하나는 답변 하나나 다이어그램 한 줄 안에 있어야 한다. 출처를 이어 붙여 비교하면 떨어진 구절을 이은 인용이 통과한다.
    @Test
    void 답변_여러_개나_다이어그램_여러_줄에_걸친_인용은_인정하지_않는다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.UNMATCHED_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[1] \"함께 DB에 저장해요 DB 자동 증가 값을\"")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[6] \"정책: LRU) - db: MySQL [DB]\"")).isEqualTo(downgraded)
        );
    }

    // 근거는 면접자의 답변이어야 한다. 면접관의 질문과 역질문에 돌려준 면접관 답은 출처가 아니다.
    @Test
    void 면접관_질문이나_면접관_답을_인용하면_NOT_MET으로_내린다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.UNMATCHED_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[5] \"" + FIFTH_QUESTION + "\"")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[1] \"하루 평균 10만 개 정도예요\"")).isEqualTo(downgraded)
        );
    }

    // 다듬은 뒤 4자 미만인 인용은 아무 답변에나 들어 있을 만해 근거로 치지 않는다. 그런 인용만 있으면 인용이 없는 것과 같고,
    // 다른 인용이 근거가 되면 짧은 인용은 판정에 영향을 주지 않는다.
    @Test
    void 정규화_후_4자_미만_인용은_근거가_아니다() {
        VerifiedVerdict noQuote = new VerifiedVerdict(false, DowngradeReason.NO_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[6] \"301\"")).isEqualTo(noQuote),
                () -> assertThat(verifier.verify(true, "[1] \"D B.\"")).isEqualTo(noQuote),
                () -> assertThat(verifier.verify(true, "[6] \"301로\"")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[6] \"302\" [6] \"301로 응답해\"")).isEqualTo(KEPT_MET)
        );
    }

    // 서버는 MET을 내리기만 하고 NOT_MET을 올리지 않는다. 내린 것이 아니므로 내린 이유도 없다.
    @Test
    void NOT_MET은_증거와_상관없이_그대로_둔다() {
        assertAll(
                () -> assertThat(verifier.verify(false, "[2] \"DB 자동 증가 값을 Base62로 바꿔\"")).isEqualTo(KEPT_NOT_MET),
                () -> assertThat(verifier.verify(false, "없음: 키 충돌 처리")).isEqualTo(KEPT_NOT_MET),
                () -> assertThat(verifier.verify(false, "")).isEqualTo(KEPT_NOT_MET)
        );
    }

    // 모델은 user 메시지의 엔티티(&amp; 등)를 출력에 옮겨 쓸 수 있다. 원래 문자로 되돌린 뒤 답변 원문과 비교한다.
    @Test
    void 엔티티로_적은_인용도_원래_문자로_되돌려_비교한다() {
        assertThat(verifier.verify(true, "[4] \"TTL은 1일 &amp; LRU로 둬요\"")).isEqualTo(KEPT_MET);
    }

    private static SystemDesignAnswerTurn turn(int questionOrder, String question, String answer) {
        return new SystemDesignAnswerTurn(questionOrder, SystemDesignStage.ofQuestionOrder(questionOrder), question,
                answer);
    }
}
