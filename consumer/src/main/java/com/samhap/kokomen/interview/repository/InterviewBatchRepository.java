package com.samhap.kokomen.interview.repository;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class InterviewBatchRepository {

    private static final String INTERVIEW_VIEW_COUNT_UPDATE_SQL = "UPDATE interview SET view_count = ? WHERE id = ?";
    private static final String INTERVIEW_LIKE_COUNT_UPDATE_SQL = "UPDATE interview SET like_count = ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchUpdateInterviewViewCount(Map<Long, Long> interviewViewCounts, int batchSize) {
        List<Entry<Long, Long>> entries = new ArrayList<>(interviewViewCounts.entrySet());

        jdbcTemplate.batchUpdate(
                INTERVIEW_VIEW_COUNT_UPDATE_SQL,
                entries,
                batchSize,
                (ps, entry) -> {
                    ps.setLong(1, entry.getValue());
                    ps.setLong(2, entry.getKey());
                }
        );
    }

    @Transactional
    public void batchUpdateInterviewLikeCount(Map<Long, Long> interviewLikeCounts, int batchSize) {
        List<Entry<Long, Long>> entries = new ArrayList<>(interviewLikeCounts.entrySet());

        jdbcTemplate.batchUpdate(
                INTERVIEW_LIKE_COUNT_UPDATE_SQL,
                entries,
                batchSize,
                (ps, entry) -> {
                    ps.setLong(1, entry.getValue());
                    ps.setLong(2, entry.getKey());
                }
        );
    }
}
