package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.RootQuestion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RootQuestionRepository extends JpaRepository<RootQuestion, Long> {

    @Query(value = """
            SELECT rq.*
            FROM root_question rq
            LEFT JOIN (
                SELECT i.root_question_id
                FROM interview i
                JOIN root_question sub_rq ON sub_rq.id = i.root_question_id
                WHERE i.member_id = :memberId
                  AND sub_rq.category = :category
                ORDER BY i.id DESC
                LIMIT :recentLimit
            ) recent
            ON rq.id = recent.root_question_id
            WHERE rq.category = :category
              AND recent.root_question_id IS NULL
            ORDER BY RAND()
            LIMIT 1
            """, nativeQuery = true)
    Optional<RootQuestion> findRandomByCategoryExcludingRecent(
            @Param("memberId") Long memberId,
            @Param("category") String category,
            @Param("recentLimit") int recentLimit);
}
