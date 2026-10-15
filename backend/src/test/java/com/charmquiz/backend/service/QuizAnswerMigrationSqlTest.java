package com.charmquiz.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAnswerMigrationSqlTest {

    private static final Path MIGRATION =
            Path.of("sql", "20261015_be_quiz_02_answer_submission.sql");

    @Test
    void migrationFailsOnDuplicatesBeforeAddingIdempotentUniqueConstraint()
            throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        int preflight = sql.indexOf("having count(*) > 1");
        int constraint = sql.indexOf("add constraint quiz_answer_session_question_key");
        assertThat(preflight).isGreaterThanOrEqualTo(0);
        assertThat(constraint).isGreaterThan(preflight);
        assertThat(sql)
                .contains("if not exists")
                .contains("unique (session_id, question_id)")
                .doesNotContain("delete from public.quiz_answer");
    }

    @Test
    void transactionalRpcSerializesSessionAnswersAndRecomputesFinalState()
            throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql)
                .contains("create or replace function public.submit_quiz_answer")
                .contains("for update")
                .contains("answer_already_submitted")
                .contains("count(distinct answer.question_id)")
                .contains("count(*) filter (where answer.is_correct)")
                .contains("when v_answered_count = 10 then v_score >= 6")
                .contains("when v_answered_count = 10 then clock_timestamp()")
                .contains("if v_answered_count > 10")
                .contains("exception")
                .contains("when unique_violation");
    }

    @Test
    void rpcUsesDefinerFixedSearchPathAndBackendOnlyExecution()
            throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql)
                .contains("security definer")
                .contains("set search_path to 'public'")
                .contains("revoke all on function public.submit_quiz_answer(uuid, uuid, uuid) from public")
                .contains("revoke execute on function public.submit_quiz_answer(uuid, uuid, uuid) from anon")
                .contains("revoke execute on function public.submit_quiz_answer(uuid, uuid, uuid) from authenticated")
                .contains("grant execute on function public.submit_quiz_answer(uuid, uuid, uuid) to service_role");
    }
}
