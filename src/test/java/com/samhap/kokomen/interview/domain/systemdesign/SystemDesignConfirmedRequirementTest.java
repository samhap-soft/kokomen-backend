package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource.ASSUMED;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource.SEED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SystemDesignConfirmedRequirementTest {

    private static final SystemDesignProblem PROBLEM = SystemDesignProblemFixtureBuilder.builder().build();
    // URL 단축기 문제의 숨은 요구사항(하루 생성량, 조회와 생성 비율, 보관 기간, 단축 키 길이, 클릭 통계). 저장소가 표시 순서로 돌려준다.
    private static final List<SystemDesignHiddenRequirement> REQUIREMENTS = urlShortenerRequirements();
    private static final String DAILY_FACT = "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.";
    private static final String RATIO_FACT = "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요.";
    private static final String RETENTION_FACT = "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요.";

    @Test
    void 역질문이_없으면_빈_목록이다() {
        assertThat(SystemDesignConfirmedRequirement.collect(List.of(), REQUIREMENTS)).isEmpty();
    }

    // 요구사항을 묻는 질문이 아니었던 역질문(DECLINED)은 확정 항목이 비어 있어 아무것도 더하지 않는다.
    @Test
    void 확정_항목을_물은_순서대로_잇는다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.SEED,
                        item("하루 생성량", "하루 평균 10만 개가 만들어진다.", SEED)),
                row(2, List.of(), SystemDesignRequirementAnswerType.DECLINED),
                row(3, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                        item("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED)),
                row(4, List.of("RETENTION_PERIOD"), SystemDesignRequirementAnswerType.MIXED,
                        item("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED),
                        item("가용성 목표", "가용성 목표는 99.9%다.", ASSUMED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("하루 생성량", "하루 평균 10만 개가 만들어진다.", SEED),
                new SystemDesignConfirmedRequirement("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                new SystemDesignConfirmedRequirement("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED),
                new SystemDesignConfirmedRequirement("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED),
                new SystemDesignConfirmedRequirement("가용성 목표", "가용성 목표는 99.9%다.", ASSUMED));
    }

    // 같은 항목을 다시 확정했으면 마지막 말이 기준이다. 이름은 앞뒤 공백을 지운 값으로 견주고, 목록에는 지운 이름을 쓴다.
    @Test
    void 이름이_같은_항목은_첫_자리에_두고_나중_값으로_덮는다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.MIXED,
                        item("하루 생성량", "하루 평균 10만 개가 만들어진다.", SEED),
                        item("가용성 목표", "가용성 목표는 99.9%다.", ASSUMED)),
                row(2, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        item("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED)),
                row(3, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        item(" 가용성 목표　", "가용성 목표는 99.95%로 다시 정했다.", ASSUMED)),
                row(4, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.SEED,
                        item("하루 생성량", "하루 생성량은 10만 개 그대로다.", SEED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("하루 생성량", "하루 생성량은 10만 개 그대로다.", SEED),
                new SystemDesignConfirmedRequirement("가용성 목표", "가용성 목표는 99.95%로 다시 정했다.", ASSUMED),
                new SystemDesignConfirmedRequirement("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED));
    }

    // 모델이 확정 항목에 적지 않은 시드는 그 시드를 처음 물은 행 자리에 정해 둔 사실로 넣는다. 한 행에서 처음 물은 시드는 그 행의
    // 확정 항목 앞에 문제의 순서로 둔다. 모델이 그 시드를 적었으면(다른 행이어도) 적은 값을 쓴다.
    @Test
    void 모델이_적지_않은_시드는_처음_물은_자리에_정해_둔_사실로_넣는다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED)),
                row(2, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.MIXED,
                        item("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED)),
                row(3, List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO", "RETENTION_PERIOD"),
                        SystemDesignRequirementAnswerType.SEED,
                        item("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                new SystemDesignConfirmedRequirement("하루 생성량", DAILY_FACT, SEED),
                new SystemDesignConfirmedRequirement("서비스 지역", "국내 사용자만 대상으로 한다.", ASSUMED),
                new SystemDesignConfirmedRequirement("조회와 생성 비율", RATIO_FACT, SEED),
                new SystemDesignConfirmedRequirement("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED));
    }

    // 확정 항목을 저장하기 전(V58 전)에 만든 행은 답의 종류와 확정 항목이 NULL이다. 항목은 건너뛰고, 물은 숨은 요구사항은 폴백으로
    // 정해 둔 사실이 나온다(처음 물은 순서). 정해 두지 않은 내용을 물은 행에서는 아무것도 나오지 않는다.
    @Test
    void 저장하기_전에_만든_행은_물은_숨은_요구사항만_정해_둔_사실로_나온다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                legacyRow(1, List.of("RETENTION_PERIOD")),
                legacyRow(2, List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO", "RETENTION_PERIOD")),
                legacyRow(3, List.of()));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("보관 기간", RETENTION_FACT, SEED),
                new SystemDesignConfirmedRequirement("하루 생성량", DAILY_FACT, SEED),
                new SystemDesignConfirmedRequirement("조회와 생성 비율", RATIO_FACT, SEED));
    }

    // 문제에서 빠진 숨은 요구사항의 code가 행에 남아 있을 수 있다. 이름과 사실을 알 수 없으니 건너뛴다.
    @Test
    void 이_문제에_없는_code는_건너뛴다() {
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of("RETIRED_REQUIREMENT", "DAILY_CREATION_VOLUME"),
                        SystemDesignRequirementAnswerType.SEED));

        assertThat(SystemDesignConfirmedRequirement.collect(requirementQuestions, REQUIREMENTS))
                .containsExactly(new SystemDesignConfirmedRequirement("하루 생성량", DAILY_FACT, SEED));
    }

    // 숨은 요구사항의 이름은 확인한 숨은 요구사항의 SEED 항목만 쓴다. 확인한 시드의 이름이 아닌 SEED 항목(묻지 않은 시드나 이름을 바꿔
    // 적은 시드)과 시드의 이름을 단 ASSUMED 항목은 넣지 않는다. 확인한 시드는 폴백으로 정해 둔 사실이 나온다.
    @Test
    void 확인한_시드가_아닌_SEED_항목과_시드_이름의_ASSUMED_항목은_넣지_않는다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.SEED,
                        item("하루 생성량", "하루 평균 10만 개가 만들어진다.", SEED),
                        item("클릭 통계", "통계 기능은 이번 범위에서 뺀다.", SEED),
                        item("일일 생성량", "하루에 10만 개가 만들어진다.", SEED)),
                row(2, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        item("보관 기간", "3년 동안 보관한다.", ASSUMED),
                        item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED)),
                row(3, List.of("READ_WRITE_RATIO"), SystemDesignRequirementAnswerType.SEED,
                        item("조회와 생성 비율", "조회는 생성의 10배다.", ASSUMED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("하루 생성량", "하루 평균 10만 개가 만들어진다.", SEED),
                new SystemDesignConfirmedRequirement("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                new SystemDesignConfirmedRequirement("조회와 생성 비율", RATIO_FACT, SEED));
    }

    // 모델이 적은 SEED 항목에 정해 둔 사실에 없는 '숫자+단위'가 있으면(값을 바꾸거나 면접자가 할 계산을 대신한 항목) 그 항목을 쓰지 않고
    // 정해 둔 사실을 둔다. 역질문 답은 서버가 시드 답으로 바꿨는데 같은 이름의 SEED 항목만 남은 행이다. 이름은 시드 이름이라 보지 않는다.
    @Test
    void 정해_둔_사실에_없는_수치를_담은_SEED_항목은_정해_둔_사실로_둔다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.SEED, DAILY_FACT,
                        item("하루 생성량", "하루 평균 20만 개가 만들어진다.", SEED)),
                row(2, List.of("READ_WRITE_RATIO"), SystemDesignRequirementAnswerType.SEED, RATIO_FACT,
                        item("조회와 생성 비율", "조회는 생성의 100배라 초당 약 116건이다.", SEED)),
                row(3, List.of("RETENTION_PERIOD"), SystemDesignRequirementAnswerType.SEED, RETENTION_FACT,
                        item("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("하루 생성량", DAILY_FACT, SEED),
                new SystemDesignConfirmedRequirement("조회와 생성 비율", RATIO_FACT, SEED),
                new SystemDesignConfirmedRequirement("보관 기간", "만든 날부터 5년 동안 열려야 한다.", SEED));
    }

    // ASSUMED 항목은 그 역질문의 답(면접자가 들은 문장)에 없는 '숫자+단위'가 이름이나 내용에 있으면 넣지 않는다. 면접자가 듣지 않은 수치가
    // 리포트와 채점 입력에 들어가지 않게 한다.
    @Test
    void 답에_없는_수치를_담은_ASSUMED_항목은_넣지_않는다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                row(1, List.of(), SystemDesignRequirementAnswerType.ASSUMED,
                        "가용성은 99.9% 이상을 목표로 보면 돼요. 커스텀 별칭은 이번 범위에서 빼도 돼요.",
                        item("가용성 목표", "가용성은 99.9% 이상이고 장애 복구는 5분 안이다.", ASSUMED),
                        item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                        item("3단계 인증", "인증은 이번 범위에서 뺀다.", ASSUMED)),
                row(2, List.of(), SystemDesignRequirementAnswerType.ASSUMED, "서비스 가용성은 99.9% 이상을 목표로 해요.",
                        item("가용성 목표", "서비스 가용성은 99.9% 이상을 목표로 한다.", ASSUMED)));

        // when
        List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                requirementQuestions, REQUIREMENTS);

        // then
        assertThat(confirmed).containsExactly(
                new SystemDesignConfirmedRequirement("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", ASSUMED),
                new SystemDesignConfirmedRequirement("가용성 목표", "서비스 가용성은 99.9% 이상을 목표로 한다.", ASSUMED));
    }

    /**
     * 불변식: 역질문으로 확인한 시드(모든 행의 물은 code 중 이 문제에 있는 것) = 목록의 SEED 항목. 이름마다 한 번씩이고, SEED 항목의
     * 이름과 놓친 요구사항(확인하지 않은 시드)의 이름을 합치면 문제의 숨은 요구사항 전부이고 겹치지 않는다. ASSUMED 항목은 시드의
     * 이름을 쓰지 않고, 목록에 같은 이름은 없다. 정해 둔 사실이 나오는 SEED 항목은 확인한 시드의 것뿐이다.
     * 정해진 시드의 난수로 만든 역질문 기록(배포 전 행, 요구사항이 아닌 질문, 모델이 시드 항목을 빠뜨리거나 이름을 바꾸거나 묻지 않은
     * 시드를 적은 경우, 같은 이름을 공백만 바꿔 다시 적은 경우, 문제에 없는 code)에서 모두 성립하는지 본다.
     */
    @Test
    void 무작위로_만든_역질문_기록에서도_SEED_항목은_확인한_숨은_요구사항과_같다() {
        Random random = new Random(20261128L);
        List<String> allNames = REQUIREMENTS.stream().map(SystemDesignHiddenRequirement::getName).toList();
        for (int caseIndex = 0; caseIndex < 500; caseIndex++) {
            // given
            List<SystemDesignRequirementQuestion> requirementQuestions = randomRequirementQuestions(random);
            Set<String> confirmedNames = new LinkedHashSet<>();
            for (SystemDesignRequirementQuestion requirementQuestion : requirementQuestions) {
                for (SystemDesignHiddenRequirement requirement : REQUIREMENTS) {
                    if (requirementQuestion.getAskedRequirementCodes().contains(requirement.getCode())) {
                        confirmedNames.add(requirement.getName());
                    }
                }
            }
            List<String> missedNames = allNames.stream().filter(name -> !confirmedNames.contains(name)).toList();

            // when
            List<SystemDesignConfirmedRequirement> confirmed = SystemDesignConfirmedRequirement.collect(
                    requirementQuestions, REQUIREMENTS);

            // then
            List<String> seedNames = confirmed.stream()
                    .filter(requirement -> requirement.source() == SEED)
                    .map(SystemDesignConfirmedRequirement::name)
                    .toList();
            List<String> assumedNames = confirmed.stream()
                    .filter(requirement -> requirement.source() == ASSUMED)
                    .map(SystemDesignConfirmedRequirement::name)
                    .toList();
            List<String> seedNamesWithMissed = new ArrayList<>(seedNames);
            seedNamesWithMissed.addAll(missedNames);
            String description = "case " + caseIndex + ": " + describe(requirementQuestions);
            assertAll(
                    () -> assertThat(seedNames).as(description).hasSameElementsAs(confirmedNames)
                            .doesNotHaveDuplicates(),
                    () -> assertThat(seedNamesWithMissed).as(description).containsExactlyInAnyOrderElementsOf(allNames),
                    () -> assertThat(assumedNames).as(description).doesNotContainAnyElementsOf(allNames),
                    () -> assertThat(confirmed).as(description)
                            .extracting(SystemDesignConfirmedRequirement::name).doesNotHaveDuplicates(),
                    () -> assertThat(confirmed).as(description)
                            .filteredOn(requirement -> isSeedFact(requirement.detail()))
                            .allSatisfy(requirement -> assertThat(requirement.source()).isEqualTo(SEED))
                            .extracting(SystemDesignConfirmedRequirement::name)
                            .isSubsetOf(confirmedNames)
            );
        }
    }

    private static List<SystemDesignRequirementQuestion> randomRequirementQuestions(Random random) {
        List<String> codes = new ArrayList<>(REQUIREMENTS.stream()
                .map(SystemDesignHiddenRequirement::getCode)
                .toList());
        codes.add("RETIRED_REQUIREMENT");
        int rowCount = random.nextInt(SystemDesignRequirementQuestion.MAX_COUNT + 1);
        List<SystemDesignRequirementQuestion> rows = new ArrayList<>();
        for (int order = 1; order <= rowCount; order++) {
            List<String> askedCodes = new ArrayList<>();
            for (String code : codes) {
                if (random.nextInt(4) == 0) {
                    askedCodes.add(code);
                }
            }
            Collections.shuffle(askedCodes, random);
            if (random.nextInt(5) == 0) {
                rows.add(legacyRow(order, askedCodes));
                continue;
            }
            if (askedCodes.isEmpty() && random.nextInt(4) == 0) {
                rows.add(row(order, List.of(), SystemDesignRequirementAnswerType.DECLINED));
                continue;
            }
            rows.add(row(order, askedCodes, askedCodes.isEmpty()
                    ? SystemDesignRequirementAnswerType.ASSUMED : SystemDesignRequirementAnswerType.MIXED,
                    randomItems(random, askedCodes)));
        }
        return rows;
    }

    // 물은 시드의 항목(그대로, 빠뜨림, 이름을 바꿈), 묻지 않은 시드의 항목, 시드 이름의 ASSUMED 항목, 시드가 아닌 ASSUMED 항목을 섞는다.
    private static SystemDesignRequirementItem[] randomItems(Random random, List<String> askedCodes) {
        List<SystemDesignRequirementItem> items = new ArrayList<>();
        for (SystemDesignHiddenRequirement requirement : REQUIREMENTS) {
            boolean asked = askedCodes.contains(requirement.getCode());
            int dice = random.nextInt(10);
            if (asked && dice < 5) {
                items.add(item(requirement.getName(), requirement.getName() + "을 확정했다.", SEED));
            } else if (asked && dice == 5) {
                items.add(item("일일 " + requirement.getName(), "이름을 바꿔 적었다.", SEED));
            } else if (!asked && dice == 0) {
                items.add(item(requirement.getName(), "묻지 않은 시드를 적었다.", SEED));
            } else if (dice == 9) {
                items.add(item(" " + requirement.getName(), "시드 이름으로 정했다.", ASSUMED));
            }
        }
        List<String> assumedNames = List.of("커스텀 별칭", "서비스 지역", "가용성 목표");
        for (int count = random.nextInt(3); count > 0; count--) {
            String name = assumedNames.get(random.nextInt(assumedNames.size()));
            items.add(item(random.nextBoolean() ? name : name + " ", name + "을 정했다(" + count + ").", ASSUMED));
        }
        Collections.shuffle(items, random);
        return items.toArray(SystemDesignRequirementItem[]::new);
    }

    private static boolean isSeedFact(String detail) {
        return REQUIREMENTS.stream().anyMatch(requirement -> requirement.getAnswer().equals(detail));
    }

    private static String describe(List<SystemDesignRequirementQuestion> requirementQuestions) {
        return requirementQuestions.stream()
                .map(row -> row.getQuestionOrder() + row.getAskedRequirementCodes().toString()
                        + (row.getRequirementItems() == null ? "legacy" : row.getRequirementItems().toString()))
                .toList()
                .toString();
    }

    // 답은 확정 항목 내용을 이은 문장이다(면접자가 들은 답에 항목 내용이 있다). 항목이 없으면 픽스처의 기본 답이다.
    private static SystemDesignRequirementQuestion row(int questionOrder, List<String> askedCodes,
                                                       SystemDesignRequirementAnswerType answerType,
                                                       SystemDesignRequirementItem... items) {
        String answer = items.length == 0 ? null : String.join(" ", Arrays.stream(items)
                .map(SystemDesignRequirementItem::detail)
                .toList());
        return row(questionOrder, askedCodes, answerType, answer, items);
    }

    private static SystemDesignRequirementQuestion row(int questionOrder, List<String> askedCodes,
                                                       SystemDesignRequirementAnswerType answerType, String answer,
                                                       SystemDesignRequirementItem... items) {
        return SystemDesignRequirementQuestionFixtureBuilder.builder()
                .questionOrder(questionOrder)
                .askedRequirementCodes(askedCodes)
                .answerType(answerType)
                .answer(answer)
                .requirementItems(List.of(items))
                .build();
    }

    // 엔티티는 새 행에 NULL을 받지 않으므로 만든 뒤 두 값을 NULL로 바꾼다(DB의 배포 전 행과 같은 상태).
    private static SystemDesignRequirementQuestion legacyRow(int questionOrder, List<String> askedCodes) {
        SystemDesignRequirementQuestion row = row(questionOrder, askedCodes, SystemDesignRequirementAnswerType.SEED);
        ReflectionTestUtils.setField(row, "answerType", null);
        ReflectionTestUtils.setField(row, "requirementItems", null);
        return row;
    }

    private static SystemDesignRequirementItem item(String name, String detail, SystemDesignRequirementSource source) {
        return new SystemDesignRequirementItem(name, detail, source);
    }

    private static List<SystemDesignHiddenRequirement> urlShortenerRequirements() {
        List<SystemDesignHiddenRequirement> requirements = new ArrayList<>();
        List<Requirement> values = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;
        for (int index = 0; index < values.size(); index++) {
            requirements.add(SystemDesignHiddenRequirementFixtureBuilder.builder()
                    .problem(PROBLEM)
                    .code(values.get(index).code())
                    .name(values.get(index).name())
                    .answer(values.get(index).answer())
                    .displayOrder(index + 1)
                    .build());
        }
        return List.copyOf(requirements);
    }
}
