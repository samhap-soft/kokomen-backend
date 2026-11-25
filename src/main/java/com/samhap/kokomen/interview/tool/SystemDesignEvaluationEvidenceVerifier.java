package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Block;
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
 *     <li>근거가 되는 큰따옴표 인용이 없다. 다듬은 뒤 {@value #MIN_QUOTE_LENGTH}자 미만인 인용(조각)은 어느 답변에나 들어 있을
 *     만해 근거로 치지 않는다. 그런 조각은 원문에 없어도 내리지 않는다(따로 쓴 짧은 인용과 같다)</li>
 *     <li>근거가 되는 인용(조각) 하나라도 이번 면접의 면접자 답변(1~6번)이나 마지막 다이어그램을 그린 줄 어디에도 없다</li>
 * </ol>
 * 인용 안의 말줄임표(…, 마침표 세 개 이상, ⋯)는 떨어진 구절 사이의 경계로 보고, 인용을 말줄임표에서 나눈 조각마다 비교한다.
 * 말줄임표 없는 인용은 조각 하나다. 조각과 출처는 같은 규칙으로 다듬어 부분 문자열로 비교한다. NFKC로 전각·호환 문자를 맞추고
 * 소문자로 바꾼 뒤 공백·줄바꿈, 보이지 않는 글자, 따옴표, 문장부호(. , ! ? · 등)를 지운다. 조각은 다듬기 전에 나눈다. 다듬으면
 * 말줄임표가 지워져 떨어진 구절이 붙기 때문이다. 조각 하나는 출처 하나(답변 하나, 다이어그램 한 줄) 안에 있어야 해서, 말줄임표 없이
 * 떨어진 구절을 이어 붙인 인용은 근거가 아니다. 조각끼리는 서로 다른 출처에 있어도 된다. 다이어그램 블록 줄은 연속된 일부가 아니어도,
 * 그 줄에서 속성 몇 개를 통째로 빼고 괄호를 다시 닫은 인용이면 그 줄에 있는 것으로 본다({@link BlockLine}). 이 블록 줄 규칙은 나누기
 * 전의 인용 전체에 먼저 적용한다. 뺀 속성 자리를 말줄임표로 표시하거나 속성 값에 말줄임표가 든 블록 줄 인용은 나누면 여는 괄호와
 * 닫는 괄호가 다른 조각으로 갈라지기 때문이다. 면접관의 질문과 역질문 기록은 면접자의 설계 근거가 아니어서 출처에 넣지 않는다.
 * 인용이 그 요소를 실제로 충족하는지(뜻)는 보지 않는다. 그 판단은 LLM 판정에 남는다.
 */
public final class SystemDesignEvaluationEvidenceVerifier {

    static final int MIN_QUOTE_LENGTH = 4;

    private static final Pattern MISSING_ELEMENT = Pattern.compile("없음\\s*:");
    // 곧은 큰따옴표와 둥근 큰따옴표를 짝짓는 순서대로 묶는다. 전각 큰따옴표는 NFKC가 곧은 큰따옴표로 바꾼다.
    private static final Pattern QUOTE = Pattern.compile("[\"“”]([^\"“”]*)[\"“”]");
    // 인용 안의 말줄임표. NFKC가 …(U+2026)를 마침표 세 개로 바꾸므로 마침표 세 개 이상으로 찾는다. NFKC가 바꾸지 않는 ⋯(U+22EF)와
    // NFKC 전의 …도 함께 본다.
    private static final Pattern ELLIPSIS = Pattern.compile("\\.{3,}|[…⋯]+");
    // 공백 문자(유니코드 공백·줄바꿈), 보이지 않는 글자(서식 문자, 이모지 변이 선택자), 따옴표, 문장부호.
    // NFKC 뒤에 지우므로 가운뎃점으로 쓰는 ㆍ(U+318D)는 NFKC가 바꾼 U+119E로 적는다.
    private static final Pattern IGNORED = Pattern.compile(
            "[\\s\\p{Z}\\p{Cf}\\x{85}\\x{FE00}-\\x{FE0F}\"'“”‘’`.,!?…⋯·\\x{119E}‧・。、]");

    private final List<String> normalizedSources;
    private final List<BlockLine> blockLines;

    private SystemDesignEvaluationEvidenceVerifier(List<String> normalizedSources, List<BlockLine> blockLines) {
        this.normalizedSources = normalizedSources;
        this.blockLines = blockLines;
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
        List<BlockLine> blockLines = command.currentDiagram().blocks().stream()
                .filter(block -> !block.props().isEmpty())
                .map(BlockLine::from)
                .toList();
        return new SystemDesignEvaluationEvidenceVerifier(Stream.concat(answers, diagramLines)
                .map(SystemDesignEvaluationEvidenceVerifier::normalize)
                .toList(), blockLines);
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
        List<String> fragments = findQuoteFragments(evidence);
        if (fragments.isEmpty()) {
            return DowngradeReason.NO_QUOTE;
        }
        if (!fragments.stream().allMatch(this::isInSources)) {
            return DowngradeReason.UNMATCHED_QUOTE;
        }
        return null;
    }

    // 큰따옴표로 묶은 구간을 말줄임표에서 나눈 뒤 조각마다 다듬어 모은다. 다듬은 뒤 짧은 조각은 뺀다. 인용 전체를 다듬은 값(말줄임표가
    // 지워진 값)이 블록 줄에서 속성을 통째로 뺀 모양이면 나누지 않고 그 값을 조각 하나로 둔다.
    private List<String> findQuoteFragments(String evidence) {
        Matcher matcher = QUOTE.matcher(evidence);
        List<String> fragments = new ArrayList<>();
        while (matcher.find()) {
            String whole = normalize(matcher.group(1));
            if (isLongEnough(whole) && matchesBlockLineWithPropsLeftOut(whole)) {
                fragments.add(whole);
                continue;
            }
            for (String fragment : ELLIPSIS.split(matcher.group(1))) {
                String normalized = normalize(fragment);
                if (isLongEnough(normalized)) {
                    fragments.add(normalized);
                }
            }
        }
        return fragments;
    }

    private static boolean isLongEnough(String normalized) {
        return normalized.codePointCount(0, normalized.length()) >= MIN_QUOTE_LENGTH;
    }

    private boolean isInSources(String fragment) {
        return normalizedSources.stream().anyMatch(source -> source.contains(fragment))
                || matchesBlockLineWithPropsLeftOut(fragment);
    }

    private boolean matchesBlockLineWithPropsLeftOut(String fragment) {
        return blockLines.stream().anyMatch(blockLine -> blockLine.matchesWithPropsLeftOut(fragment));
    }

    private static String normalize(String text) {
        String compatible = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return IGNORED.matcher(compatible).replaceAll("");
    }

    /**
     * 속성이 있는 다이어그램 블록 줄을 다듬은 앞부분({@code - id: 이름 [종류]})과 속성({@code 이름: 값})들로 나눈 것. 다듬으면 공백과
     * 쉼표가 지워지므로 다듬은 블록 줄은 앞부분 + "(" + 속성들을 이은 것 + ")"이다. 렌더러와 같은 글자로 나누고 출처 줄처럼 엔티티를
     * 되돌려 다듬는다.
     */
    private record BlockLine(String head, List<String> props) {

        private static BlockLine from(Block block) {
            return new BlockLine(normalizeRendered(SystemDesignDiagramRenderer.blockHead(block)), block.props().stream()
                    .map(prop -> normalizeRendered(SystemDesignDiagramRenderer.propText(prop)))
                    .toList());
        }

        private static String normalizeRendered(String rendered) {
            return normalize(SystemDesignUserMessages.unescape(rendered));
        }

        /**
         * 모델은 속성이 많은 블록 줄에서 요소에 맞는 속성만 남기고 괄호를 다시 닫아 인용하곤 한다. 다듬은 조각이 이 줄에서 속성 몇 개를
         * 통째로 뺀 것인지 본다. 괄호 앞은 앞부분의 끝부분(비어도 된다)이어야 하고, 괄호 안은 남긴 속성 하나 이상을 원래 순서대로 그대로
         * 이은 것이어야 한다. 속성의 일부만 남기거나, 순서를 바꾸거나, 다른 블록의 속성을 넣으면 아니다. 다듬으면 말줄임표가 지워지므로
         * 뺀 속성 자리를 말줄임표로 표시한 인용 전체도 같은 모양으로 본다.
         */
        private boolean matchesWithPropsLeftOut(String fragment) {
            if (!fragment.endsWith(")")) {
                return false;
            }
            for (int open = fragment.indexOf('('); open >= 0; open = fragment.indexOf('(', open + 1)) {
                if (head.endsWith(fragment.substring(0, open))
                        && isPropSequence(fragment.substring(open + 1, fragment.length() - 1), 0)) {
                    return true;
                }
            }
            return false;
        }

        // text가 from번째부터의 속성 중 하나 이상을 원래 순서대로 그대로 이은 것인지 본다. 속성은 블록마다 10개 이하다.
        private boolean isPropSequence(String text, int from) {
            for (int index = from; index < props.size(); index++) {
                String prop = props.get(index);
                if (text.startsWith(prop) && (text.length() == prop.length()
                        || isPropSequence(text.substring(prop.length()), index + 1))) {
                    return true;
                }
            }
            return false;
        }
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
        // 근거가 되는 큰따옴표 인용(조각)이 없다
        NO_QUOTE,
        // 이번 면접의 답변·다이어그램에 없는 인용(조각)이 있다
        UNMATCHED_QUOTE,
    }
}
