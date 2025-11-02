package com.samhap.kokomen.member.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.dto.DailyInterviewCount;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.MemberSocialLogin;
import com.samhap.kokomen.member.domain.SocialProvider;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.MemberSocialLoginRepository;
import com.samhap.kokomen.member.service.dto.MemberStreakResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponse;
import com.samhap.kokomen.member.service.dto.MyProfileResponseV2;
import com.samhap.kokomen.member.service.dto.ProfileUpdateRequest;
import com.samhap.kokomen.member.service.dto.RankingPageResponse;
import com.samhap.kokomen.member.service.dto.RankingProjection;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.service.TokenService;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberSocialLoginRepository memberSocialLoginRepository;
    private final TokenService tokenService;
    private final InterviewRepository interviewRepository;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Transactional
    public Member saveSocialMember(SocialProvider provider, String socialId, String nickname) {
        Member member = memberRepository.save(new Member(nickname));
        memberSocialLoginRepository.save(new MemberSocialLogin(member, provider, socialId));
        return member;
    }

    public Member readById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    public Member readBySocialLogin(SocialProvider provider, String socialId) {
        MemberSocialLogin socialLogin = memberSocialLoginRepository.findByProviderAndSocialId(provider, socialId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
        return socialLogin.getMember();
    }

    public Optional<Member> findBySocialLogin(SocialProvider provider, String socialId) {
        return memberSocialLoginRepository.findByProviderAndSocialId(provider, socialId)
                .map(MemberSocialLogin::getMember);
    }

    public MyProfileResponse findMember(MemberAuth memberAuth) {
        Member member = readById(memberAuth.memberId());
        long rank = memberRepository.findRankByScore(member.getScore());
        long totalMemberCount = memberRepository.count();
        Token freeToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.FREE);
        Token paidToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.PAID);
        boolean isTestUser = isTestUser(memberAuth.memberId());
        return new MyProfileResponse(member, totalMemberCount, rank, freeToken.getTokenCount() + paidToken.getTokenCount(), isTestUser);
    }

    public MyProfileResponseV2 findMemberV2(MemberAuth memberAuth) {
        Member member = readById(memberAuth.memberId());
        long rank = memberRepository.findRankByScore(member.getScore());
        long totalMemberCount = memberRepository.count();
        Token freeToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.FREE);
        Token paidToken = tokenService.readTokenByMemberIdAndType(memberAuth.memberId(), TokenType.PAID);
        return new MyProfileResponseV2(member, totalMemberCount, rank, freeToken.getTokenCount(), paidToken.getTokenCount());
    }

    public List<RankingResponse> findRanking(Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        return RankingResponse.createRankingResponses(memberRepository.findRankings(limit, offset));
    }

    // 페이지네이션용
    public RankingPageResponse findRankingPage(Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        int currentPage = pageable.getPageNumber();

        List<RankingResponse> rankings = RankingResponse.createRankingResponses(
                memberRepository.findRankings(limit, offset)
        );
        long totalElements = memberRepository.countTotalMembers();

        return RankingPageResponse.of(rankings, currentPage, limit, totalElements);
    }

    // 페이지네이션용 v3(Pageable을 활용한 코드)
    public RankingPageResponse findRankingPageV3(Pageable pageable) {
        Page<RankingProjection> page = memberRepository.findRankingsV3(pageable);
        List<RankingResponse> rankings = RankingResponse.createRankingResponses(page.getContent());

        return RankingPageResponse.of(
                rankings,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public void updateProfile(MemberAuth memberAuth, ProfileUpdateRequest profileUpdateRequest) {
        Member member = readById(memberAuth.memberId());
        member.updateProfile(profileUpdateRequest.nickname());
    }

    @Transactional
    public void withdraw(Member member) {
        // 소셜로그인 정보들을 먼저 삭제
        List<MemberSocialLogin> socialLogins = memberSocialLoginRepository.findByMember_Id(member.getId());
        memberSocialLoginRepository.deleteAll(socialLogins);
        // Member 엔티티 탈퇴 처리
        member.withdraw();
    }

    private boolean isTestUser(Long memberId) {
        if ("dev".equals(activeProfile)) {
            return memberId.equals(34L);
        } else if ("prod".equals(activeProfile)) {
            return memberId.equals(302L);
        }
        return false;
    }

    public MemberStreakResponse findMemberStreaks(MemberAuth memberAuth, LocalDate startDate, LocalDate endDate) {
        List<DailyInterviewCount> allDailyInterviewCount = interviewRepository.countFinishedInterviewsByMemberId(memberAuth.memberId());

        Integer maxStreak = calculateMaxStreak(allDailyInterviewCount);
        Integer currentStreak = calculateCurrentStreak(allDailyInterviewCount, LocalDate.now());
        List<DailyInterviewCount> filteredCounts = filterByDateRange(allDailyInterviewCount, startDate, endDate);

        return new MemberStreakResponse(filteredCounts, maxStreak, currentStreak);
    }

    private List<DailyInterviewCount> filterByDateRange(List<DailyInterviewCount> allDailyInterviewCount, LocalDate startDate, LocalDate endDate) {
        Comparator<DailyInterviewCount> dateComparator = Comparator.comparing(DailyInterviewCount::date);

        int startIndex = findStartIndex(allDailyInterviewCount, startDate, dateComparator);
        int endIndex = findEndIndex(allDailyInterviewCount, endDate, dateComparator);

        return allDailyInterviewCount.subList(startIndex, endIndex);
    }

    private int findStartIndex(List<DailyInterviewCount> allDailyInterviewCount, LocalDate startDate, Comparator<DailyInterviewCount> comparator) {
        int index = Collections.binarySearch(allDailyInterviewCount, new DailyInterviewCount(startDate, 0L), comparator);
        if (index < 0) {
            return -index - 1;
        }
        return index;
    }

    private int findEndIndex(List<DailyInterviewCount> allDailyInterviewCount, LocalDate endDate, Comparator<DailyInterviewCount> comparator) {
        int index = Collections.binarySearch(allDailyInterviewCount, new DailyInterviewCount(endDate.plusDays(1), 0L), comparator);
        if (index < 0) {
            return -index - 1;
        }
        return index;
    }

    private Integer calculateMaxStreak(List<DailyInterviewCount> allDailyInterviewCount) {
        if (allDailyInterviewCount.isEmpty()) {
            return 0;
        }

        int maxStreak = 0;
        int streak = 0;

        LocalDate previousDate = null;
        for (DailyInterviewCount dailyCount : allDailyInterviewCount) {
            LocalDate curDate = dailyCount.date();

            streak = calculateStreakCount(previousDate, curDate, streak);
            maxStreak = Math.max(maxStreak, streak);
            previousDate = curDate;
        }

        return maxStreak;
    }

    private int calculateStreakCount(LocalDate previousDate, LocalDate date, int streak) {
        if (previousDate == null || date.equals(previousDate.plusDays(1))) {
            return streak + 1;
        }
        return 1;
    }

    private Integer calculateCurrentStreak(List<DailyInterviewCount> allDailyInterviewCount, LocalDate today) {
        if (allDailyInterviewCount.isEmpty()) {
            return 0;
        }

        LocalDate lastDate = getLastDate(allDailyInterviewCount);

        if (!isStreakActive(lastDate, today)) {
            return 0;
        }

        return countConsecutiveDays(allDailyInterviewCount, lastDate);
    }

    private LocalDate getLastDate(List<DailyInterviewCount> list) {
        return list.get(list.size() - 1).date();
    }

    private boolean isStreakActive(LocalDate lastDate, LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        return lastDate.equals(today) || lastDate.equals(yesterday);
    }

    private int countConsecutiveDays(List<DailyInterviewCount> list, LocalDate lastDate) {
        List<DailyInterviewCount> reversedList = new ArrayList<>(list);
        Collections.reverse(reversedList);

        int streak = 0;
        LocalDate expectedDate = lastDate;

        for (DailyInterviewCount dailyCount : reversedList) {
            if (!dailyCount.date().equals(expectedDate)) {
                break;
            }
            streak++;
            expectedDate = expectedDate.minusDays(1);
        }

        return streak;
    }
}
