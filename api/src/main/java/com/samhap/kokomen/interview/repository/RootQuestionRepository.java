package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RootQuestionRepository extends JpaRepository<RootQuestion, Long> {

    @Query(value = """
            SELECT r
            FROM RootQuestion r
            WHERE r.category = :category AND r.state = :rootQuestionState
              AND NOT EXISTS (
                SELECT 1
                FROM Interview i
                WHERE i.rootQuestion = r AND i.member.id = :memberId
              )
            ORDER BY r.questionOrder ASC
            LIMIT 1
            """)
    Optional<RootQuestion> findFirstRootQuestionMemberNotReceivedByCategory(
            @Param("category") Category category,
            @Param("memberId") Long memberId,
            @Param("rootQuestionState") RootQuestionState rootQuestionState
    );

    @Query(value = """
            SELECT r
            FROM Interview i JOIN RootQuestion r
            ON i.rootQuestion = r
            WHERE i.member.id = :memberId AND r.category = :category AND r.state = :rootQuestionState
            ORDER BY i.id DESC
            LIMIT 1
            """)
    Optional<RootQuestion> findLastRootQuestionMemberReceivedByCategory(
            @Param("category") Category category,
            @Param("memberId") Long memberId,
            @Param("rootQuestionState") RootQuestionState rootQuestionState
    );

    Optional<RootQuestion> findRootQuestionByCategoryAndStateAndQuestionOrder(Category category, RootQuestionState state, Integer questionOrder);
}
