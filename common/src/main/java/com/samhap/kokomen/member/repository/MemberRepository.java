package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.dto.RankingProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(Long kakaoId);

    @Query(value = """
            SELECT COUNT(*) + 1
            FROM member
            WHERE score > :score
            """, nativeQuery = true)
    long findRankByScore(@Param("score") int score);


    @Query(value = """
                SELECT 
                    m.id AS id,
                    m.nickname AS nickname,
                    m.score AS score,
                    COUNT(i.id) AS finishedInterviewCount
                FROM (
                    SELECT id, nickname, score
                    FROM member
                    ORDER BY score DESC
                    LIMIT :limit OFFSET :offset
                ) m
                LEFT JOIN interview i
                    ON i.member_id = m.id AND i.interview_state = 'FINISHED'
                GROUP BY m.id, m.nickname, m.score
                ORDER BY m.score DESC
            """, nativeQuery = true)
    List<RankingProjection> findRankings(@Param("limit") int limit, @Param("offset") int offset);
}
