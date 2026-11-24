package com.samhap.kokomen.interview.tool;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 마지막 답변 평가(L3)의 평가 포인트 판정을 그 포인트의 증거 필드로 검증한다. 판정을 LLM 문장에 맡기지 않으려고, MET인데 증거가
 * 근거가 되지 않으면 NOT_MET으로 내린다. 응답 실패가 아니라 판정을 바꾸는 것이고, NOT_MET은 증거와 상관없이 그대로 둔다.
 *
 * <p>MET을 내리는 경우(이 순서로 본다):
 * <ol>
 *     <li>인용 밖에 확인되지 않은 요소 표시({@code 없음:})가 있다. 원문을 옮긴 인용 안의 {@code 없음:}은 표시가 아니다</li>
 *     <li>근거가 되는 큰따옴표 인용이 없다. 다듬은 뒤 {@value #MIN_QUOTE_LENGTH}자 미만인 인용은 어느 답변에나 들어 있을 만해
 *     근거로 치지 않는다</li>
 *     <li>근거가 되는 인용 하나라도 이번 면접의 면접자 답변(1~6번)이나 마지막 다이어그램을 그린 줄 어디에도 없다</li>
 * </ol>
 * 인용과 출처는 같은 규칙으로 다듬어 부분 문자열로 비교한다. NFKC로 전각·호환 문자를 맞추고 소문자로 바꾼 뒤 공백·줄바꿈, 보이지
 * 않는 글자, 따옴표, 문장부호(. , ! ? … · 등)를 지운다. 인용 하나는 출처 하나(답변 하나, 다이어그램 한 줄) 안에 있어야 해서, 떨어진 구절을 이어 붙인
 * 인용은 근거가 아니다. 면접관의 질문과 역질문 기록은 면접자의 설계 근거가 아니어서 출처에 넣지 않는다.
 * 인용이 그 요소를 실제로 충족하는지(뜻)는 보지 않는다. 그 판단은 LLM 판정에 남는다.
 */
public final class SystemDesignEvaluationEvidenceVerifier {

    static final int MIN_QUOTE_LENGTH = 4;

    private static final Pattern MISSING_ELEMENT = Pattern.compile("없음\\s*:");
    // 곧은 큰따옴표와 둥근 큰따옴표를 짝짓는 순서대로 묶는다. 전각 큰따옴표는 NFKC가 곧은 큰따옴표로 바꾼다.
    private static final Pattern QUOTE = Pattern.compile("[\"“”]([^\"“”]*)[\"“”]");
    // 공백 문자(유니코드 공백·줄바꿈), 보이지 않는 글자(서식 문자, 이모지 변이 선택자), 따옴표, 문장부호.
    // NFKC 뒤에 지우므로 가운뎃점으로 쓰는 ㆍ(U+318D)는 NFKC가 바꾼 U+119E로 적는다.
    private static final Pattern IGNORED = Pattern.compile(
            "[\\s\\p{Z}\\p{Cf}\\x{85}\\x{FE00}-\\x{FE0F}\"'“”‘’`.,!?…⋯·\\x{119E}‧・。、]");

    private final List<String> normalizedSources;

    private SystemDesignEvaluationEvidenceVerifier(List<String> normalizedSources) {
        this.normalizedSources = normalizedSources;
    }

    /**
     * 출처는 이 면접의 면접자 답변(질문 순서)과, 마지막 다이어그램을 user 메시지와 같은 형식으로 그린 줄이다. 렌더러는 user 메시지에
     * 넣으려고 이스케이프하므로 원래 문자로 되돌려 쓴다. 답변은 저장한 원문 그대로다.
     */
    public static SystemDesignEvaluationEvidenceVerifier of(SystemDesignEndCommand command) {
        Stream<String> answers = Stream.concat(command.previousTurns().stream(), Stream.of(command.currentTurn()))
                .map(SystemDesignAnswerTurn::answer);
        Stream<String> diagramLines = SystemDesignUserMessages.unescape(
                SystemDesignDiagramRenderer.render(command.currentDiagram())).lines();
        return new SystemDesignEvaluationEvidenceVerifier(Stream.concat(answers, diagramLines)
                .map(SystemDesignEvaluationEvidenceVerifier::normalize)
                .toList());
    }

    /**
     * @param met      LLM 판정(MET이면 true)
     * @param evidence LLM이 쓴 증거 필드 값. 엔티티는 원래 문자로 되돌려 읽는다
     */
    public VerifiedVerdict verify(boolean met, String evidence) {
        if (!met) {
            return new VerifiedVerdict(false, null);
        }
        DowngradeReason downgradeReason = findDowngradeReason(
                Normalizer.normalize(SystemDesignUserMessages.unescape(evidence), Normalizer.Form.NFKC));
        return new VerifiedVerdict(downgradeReason == null, downgradeReason);
    }

    private DowngradeReason findDowngradeReason(String evidence) {
        if (MISSING_ELEMENT.matcher(QUOTE.matcher(evidence).replaceAll(" ")).find()) {
            return DowngradeReason.MISSING_ELEMENT;
        }
        List<String> quotes = findQuotes(evidence);
        if (quotes.isEmpty()) {
            return DowngradeReason.NO_QUOTE;
        }
        if (!quotes.stream().allMatch(this::isInSources)) {
            return DowngradeReason.UNMATCHED_QUOTE;
        }
        return null;
    }

    // 큰따옴표로 묶은 구간을 다듬어 모은다. 다듬은 뒤 짧은 인용은 뺀다.
    private static List<String> findQuotes(String evidence) {
        Matcher matcher = QUOTE.matcher(evidence);
        List<String> quotes = new ArrayList<>();
        while (matcher.find()) {
            String quote = normalize(matcher.group(1));
            if (quote.codePointCount(0, quote.length()) >= MIN_QUOTE_LENGTH) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    private boolean isInSources(String quote) {
        return normalizedSources.stream()
                .anyMatch(source -> source.contains(quote));
    }

    private static String normalize(String text) {
        String compatible = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return IGNORED.matcher(compatible).replaceAll("");
    }

    /**
     * @param met             서버가 정한 최종 판정
     * @param downgradeReason MET을 NOT_MET으로 내린 이유. 내리지 않았으면 null
     */
    public record VerifiedVerdict(boolean met, DowngradeReason downgradeReason) {

        public boolean isDowngraded() {
            return downgradeReason != null;
        }
    }

    public enum DowngradeReason {
        // 확인되지 않은 요소 표시(없음:)가 있다
        MISSING_ELEMENT,
        // 근거가 되는 큰따옴표 인용이 없다
        NO_QUOTE,
        // 이번 면접의 답변·다이어그램에 없는 인용이 있다
        UNMATCHED_QUOTE,
    }
}
