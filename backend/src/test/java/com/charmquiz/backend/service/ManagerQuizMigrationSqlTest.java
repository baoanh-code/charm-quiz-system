package com.charmquiz.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ManagerQuizMigrationSqlTest {

    private static final Path MIGRATION =
            Path.of("sql", "20261015_be_mgr_04_quiz_management.sql");

    @Test
    void migrationAddsStableOptionOrderAndAtomicManagerRpcs() throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql)
                .contains("add column if not exists option_order integer")
                .contains("partition by option.question_id")
                .contains("order by option.id asc")
                .contains("unique (question_id, option_order)")
                .contains("check (option_order between 0 and 3)")
                .contains("create or replace function public.import_manager_quiz")
                .contains("create or replace function public.set_manager_quiz_status")
                .contains("'inactive'")
                .contains("(v_option_number - 1) = v_correct_index")
                .contains("language plpgsql")
                .contains("security definer")
                .contains("set search_path to 'public'")
                .contains("lock table public.quiz in share row exclusive mode")
                .contains("set status = 'inactive'")
                .contains("grant execute on function public.import_manager_quiz(text, text, jsonb) to service_role")
                .contains("grant execute on function public.set_manager_quiz_status(uuid, text) to service_role")
                .contains("revoke execute on function public.import_manager_quiz(text, text, jsonb) from anon")
                .contains("revoke execute on function public.set_manager_quiz_status(uuid, text) from authenticated");
    }
}
