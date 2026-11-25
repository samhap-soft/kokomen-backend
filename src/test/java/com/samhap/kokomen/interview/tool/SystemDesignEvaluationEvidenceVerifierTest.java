package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Input;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputBlock;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputConnection;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputProp;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier.DowngradeReason;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier.VerifiedVerdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemDesignEvaluationEvidenceVerifierTest {

    private static final String FIFTH_QUESTION = "DB 서버가 내려가면 이미 보낸 링크는 어떻게 되나요?";
    private static final String REQUIREMENT_QUESTION = "하루에 몇 개나 만들어지나요?";
    private static final VerifiedVerdict KEPT_MET = new VerifiedVerdict(true, null);
    private static final VerifiedVerdict KEPT_NOT_MET = new VerifiedVerdict(false, null);

    // 출처는 면접자 답변 1~6번과 마지막 다이어그램(SystemDesignDiagramFixture.withCache)을 그린 줄이다.
    // 다이어그램 줄 예: "- cache: Redis 캐시 ⚡ [CACHE] (TTL: 1일, 정책: LRU)", "- api → cache : 조회"
    private final SystemDesignEvaluationEvidenceVerifier verifier = SystemDesignEvaluationEvidenceVerifier.of(
            new SystemDesignEndCommand(77L, SystemDesignProblemContextFixture.urlShortener(),
                    List.of(new SystemDesignRequirementTurn(REQUIREMENT_QUESTION,
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

    // 원문의 쉼표·느낌표·물음표·가운뎃점을 빼거나 바꾸고, 작은따옴표를 둥근 작은따옴표로 바꾼 인용
    @Test
    void 쉼표_느낌표_물음표_가운뎃점_작은따옴표가_달라도_같은_문장이면_인정한다() {
        SystemDesignEvaluationEvidenceVerifier punctuated = verifierOf(
                "정말요? 캐시를 두면 빨라요! 대신 갱신이 늦어요. 읽기·쓰기를 나누고, 'TTL'은 1일이에요.");

        assertAll(
                () -> assertThat(punctuated.verify(true, "[1] \"정말요 캐시를 두면 빨라요 대신 갱신이 늦어요\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(punctuated.verify(true, "[1] \"읽기 쓰기를 나누고 ‘TTL’은 1일이에요\""))
                        .isEqualTo(KEPT_MET)
        );
    }

    // 한국어 글에서 가운뎃점으로 흔히 쓰는 ㆍ(U+318D)는 NFKC가 U+119E로 바꾼다. 그래도 ·(U+00B7)와 같은 문장부호로 지운다.
    @Test
    void 가운뎃점_종류가_달라도_같은_문장으로_본다() {
        SystemDesignEvaluationEvidenceVerifier araea = verifierOf("생성ㆍ조회 요청은 API 서버가 받아요.");
        SystemDesignEvaluationEvidenceVerifier middleDot = verifierOf("생성·조회 요청은 API 서버가 받아요.");

        assertAll(
                () -> assertThat(araea.verify(true, "[1] \"생성·조회 요청은 API 서버가 받아요\"")).isEqualTo(KEPT_MET),
                () -> assertThat(araea.verify(true, "[1] \"생성 조회 요청은 API 서버가 받아요\"")).isEqualTo(KEPT_MET),
                () -> assertThat(middleDot.verify(true, "[1] \"생성ㆍ조회 요청은 API 서버가 받아요\"")).isEqualTo(KEPT_MET)
        );
    }

    // 전각 문자(전각 큰따옴표로 묶은 인용 포함), 줄바꿈 없는 공백·폭 없는 공백, 이모지 변이 선택자, 소프트 하이픈처럼 보이지 않거나
    // 모양만 다른 글자는 같은 글자로 본다.
    @Test
    void 전각_문자나_보이지_않는_글자가_달라도_같은_문장이면_인정한다() {
        SystemDesignEvaluationEvidenceVerifier verifier = verifierOf(
                "ＤＢ는 MySQL을 써요. 앞에는 로드\u00AD밸런서와 ☁\uFE0F 캐시를 둬요.");

        assertAll(
                () -> assertThat(verifier.verify(true, "[1] ＂DB는\u00A0MySQL을\u200B써요＂")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[1] \"앞에는 로드밸런서와 ☁ 캐시를 둬요\"")).isEqualTo(KEPT_MET)
        );
    }

    // 없음 표시는 인용 밖의 "없음:"(콜론은 전각이어도 된다)이다. 원문을 그대로 옮긴 인용 안의 "없음:"과 콜론 없는 "없음"은 표시가 아니다.
    @Test
    void 없음_표시는_인용_밖의_없음_콜론만_본다() {
        SystemDesignEvaluationEvidenceVerifier verifier = verifierOf(
                "다운타임 없음: 복제본을 바로 승격해요. 캐시 없음 상태면 DB를 바로 읽어요.");

        assertAll(
                () -> assertThat(verifier.verify(true, "[1] \"다운타임 없음: 복제본을 바로 승격해요\"")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[1] \"캐시 없음 상태면 DB를 바로 읽어요\"")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[1] \"캐시 없음 상태면 DB를 바로 읽어요\"\n없음： 장애 전환"))
                        .isEqualTo(new VerifiedVerdict(false, DowngradeReason.MISSING_ELEMENT))
        );
    }

    @Test
    void 다이어그램_줄_인용을_인정한다() {
        String evidence = """
                [6] "cache: Redis 캐시 ⚡ [CACHE]"
                [6] "api → cache : 조회\"""";

        assertThat(verifier.verify(true, evidence)).isEqualTo(KEPT_MET);
    }

    // 렌더러는 user 메시지에 넣으려고 다이어그램의 &, <, >를 엔티티로 바꾼다. 출처는 원래 문자로 되돌려 쓰므로, 모델이 원래 문자로
    // 옮긴 인용과 엔티티 그대로 옮긴 인용을 모두 인정한다.
    @Test
    void 다이어그램_줄의_엔티티는_원래_문자로_되돌려_비교한다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(
                List.of(new InputBlock("api", "API_SERVER", "API & 워커", List.of(new InputProp("큐", "<Kafka>")), 0.0,
                        0.0), new InputBlock("db", "DB", "MySQL", List.of(), 100.0, 0.0)),
                List.of(new InputConnection("c1", "api", "db", "R&W"))));
        SystemDesignEvaluationEvidenceVerifier verifier = verifierOf(diagram, "API 서버가 DB에 써요.");

        assertAll(
                () -> assertThat(verifier.verify(true, "[1] \"api: API & 워커 [API_SERVER] (큐: <Kafka>)\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[1] \"api → db : R&amp;W\"")).isEqualTo(KEPT_MET)
        );
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

    // 모델은 떨어진 구절을 말줄임표(…, 마침표 세 개 이상, ⋯)로 이어 인용하곤 한다. 말줄임표를 인용 안의 경계로 보고 조각마다
    // 비교한다. 말줄임표 앞뒤 공백, 이어 쓴 말줄임표, 앞뒤 끝의 말줄임표는 상관없다.
    @Test
    void 말줄임표로_이은_인용은_조각마다_원문에_있으면_인정한다() {
        assertAll(
                () -> assertThat(verifier.verify(true, "[2] \"DB 자동 증가 값을 Base62로 바꿔…키 충돌이 없어요\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[2] \"DB 자동 증가 값을 Base62로 바꿔...키 충돌이 없어요\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[2] \"…DB 자동 증가 값을 …… 단축 키를 만들어요 ..... 키 충돌이 없어요…\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[3] \"한 건을 500바이트로 잡으면⋯MySQL 테이블에 둬요\""))
                        .isEqualTo(KEPT_MET)
        );
    }

    // 조각마다 출처 하나(답변 하나, 다이어그램 한 줄) 안에 있으면 된다. 조각끼리 같은 출처일 필요는 없다.
    @Test
    void 말줄임표_조각은_서로_다른_답변이나_다이어그램_줄에_있어도_된다() {
        assertAll(
                () -> assertThat(verifier.verify(true, "[1] \"API 서버가 받아 단축 키를 만들고…API 서버 앞에 Redis 캐시를 두고\""))
                        .isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[4] \"Redis 캐시를 두고 … cache: Redis 캐시 ⚡ [CACHE]\""))
                        .isEqualTo(KEPT_MET)
        );
    }

    // 조각 하나라도 원문에 없으면(바꿔 쓴 조각, 면접관 질문에서 옮긴 조각) 인용 하나가 원문에 없는 것과 같다.
    @Test
    void 말줄임표_조각_하나라도_원문에_없으면_NOT_MET으로_내린다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.UNMATCHED_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[2] \"DB 자동 증가 값을 Base62로 바꿔…해시 충돌이 나면 다시 만들어요\""))
                        .isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[5] \"DB를 복제해 두고…이미 보낸 링크는 어떻게 되나요\""))
                        .isEqualTo(downgraded)
        );
    }

    // 다듬은 뒤 4자 미만인 조각은 짧은 인용처럼 근거로도 실패로도 치지 않는다. 원문에 없는 짧은 조각이 있어도 나머지 조각으로 본다.
    @Test
    void 말줄임표로_나눈_4자_미만_조각은_무시한다() {
        assertAll(
                () -> assertThat(verifier.verify(true, "[6] \"301로 응답해…줄여요\"")).isEqualTo(KEPT_MET),
                () -> assertThat(verifier.verify(true, "[6] \"301로 응답해…안 써요\"")).isEqualTo(KEPT_MET)
        );
    }

    // 조각이 모두 4자 미만이면 그 인용은 근거가 되는 인용이 아니다. 짧은 조각을 여러 개 이어도 근거가 되지 않는다.
    @Test
    void 말줄임표로_나눈_조각이_모두_4자_미만이면_근거_인용이_아니다() {
        VerifiedVerdict noQuote = new VerifiedVerdict(false, DowngradeReason.NO_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[6] \"301…302\"")).isEqualTo(noQuote),
                () -> assertThat(verifier.verify(true, "[4] \"캐시를…두지…않아요\"")).isEqualTo(noQuote),
                () -> assertThat(verifier.verify(true, "[6] \"301…302\" [6] \"301로 응답해\"")).isEqualTo(KEPT_MET)
        );
    }

    // 근거는 면접자의 설계 답변이어야 한다. 면접관의 질문과 역질문 기록(면접자가 물은 역질문, 그때 돌려준 면접관 답)은 출처가 아니다.
    @Test
    void 면접관_질문이나_역질문_기록을_인용하면_NOT_MET으로_내린다() {
        VerifiedVerdict downgraded = new VerifiedVerdict(false, DowngradeReason.UNMATCHED_QUOTE);

        assertAll(
                () -> assertThat(verifier.verify(true, "[5] \"" + FIFTH_QUESTION + "\"")).isEqualTo(downgraded),
                () -> assertThat(verifier.verify(true, "[1] \"" + REQUIREMENT_QUESTION + "\"")).isEqualTo(downgraded),
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

    // 답변 하나와 시작 다이어그램만 출처로 둔 검증기
    private static SystemDesignEvaluationEvidenceVerifier verifierOf(String answer) {
        return verifierOf(SystemDesignDiagramFixture.urlShortenerStarter(), answer);
    }

    private static SystemDesignEvaluationEvidenceVerifier verifierOf(SystemDesignDiagram diagram, String answer) {
        return SystemDesignEvaluationEvidenceVerifier.of(new SystemDesignEndCommand(78L,
                SystemDesignProblemContextFixture.urlShortener(), List.of(), List.of(), turn(6, "질문이에요.", answer),
                diagram, List.of()));
    }

    private static SystemDesignAnswerTurn turn(int questionOrder, String question, String answer) {
        return new SystemDesignAnswerTurn(questionOrder, SystemDesignStage.ofQuestionOrder(questionOrder), question,
                answer);
    }
}
