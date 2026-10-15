package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.QuizOptionResponse;
import com.charmquiz.backend.dto.QuizQuestionResponse;
import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SupabaseQuizService {

    static final int QUIZ_LENGTH = 10;
    static final int OPTIONS_PER_QUESTION = 4;
    static final String QUESTION_COLUMNS = "id,question_text,created_at";
    static final String OPTION_COLUMNS = "id,question_id,option_text";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseQuizService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseQuizService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public QuizSessionResponse startSession() {
        UUID quizId = getOnlyActiveQuizId();
        List<QuestionRow> questionRows = getValidQuestions(quizId);
        List<QuizQuestionResponse> questions = new ArrayList<>(QUIZ_LENGTH);

        for (QuestionRow question : questionRows) {
            questions.add(new QuizQuestionResponse(
                    question.id().toString(),
                    question.prompt(),
                    getValidOptions(question.id())
            ));
        }

        UUID sessionId = insertSession(quizId);
        return new QuizSessionResponse(sessionId.toString(), List.copyOf(questions));
    }

    private UUID getOnlyActiveQuizId() {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz?status=eq.ACTIVE&select=id&limit=2")
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != 1) {
            throw new QuizUnavailableException();
        }
        return parseUuid(rows.getFirst(), "id");
    }

    private List<QuestionRow> getValidQuestions(UUID quizId) {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_question?quiz_id=eq." + quizId
                        + "&status=eq.ACTIVE"
                        + "&select=" + QUESTION_COLUMNS
                        + "&order=created_at.asc,id.asc"
                        + "&limit=" + (QUIZ_LENGTH + 1))
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != QUIZ_LENGTH) {
            throw new QuizUnavailableException();
        }

        Set<UUID> questionIds = new HashSet<>();
        List<QuestionRow> questions = new ArrayList<>(QUIZ_LENGTH);
        for (Map<String, Object> row : rows) {
            UUID id = parseUuid(row, "id");
            String prompt = requiredContent(row, "question_text");
            if (!questionIds.add(id)) {
                throw new QuizUnavailableException();
            }
            questions.add(new QuestionRow(id, prompt));
        }
        return List.copyOf(questions);
    }

    private List<QuizOptionResponse> getValidOptions(UUID questionId) {
        String rawJson = execute(() -> restClient.post()
                .uri("/rpc/get_quiz_options?select=" + OPTION_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("p_question_id", questionId.toString()))
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != OPTIONS_PER_QUESTION) {
            throw new QuizUnavailableException();
        }

        Set<UUID> optionIds = new HashSet<>();
        List<QuizOptionResponse> options = new ArrayList<>(OPTIONS_PER_QUESTION);
        for (Map<String, Object> row : rows) {
            UUID optionId = parseUuid(row, "id");
            UUID returnedQuestionId = parseUuid(row, "question_id");
            String label = requiredContent(row, "option_text");
            if (!questionId.equals(returnedQuestionId)
                    || !optionIds.add(optionId)) {
                throw new QuizUnavailableException();
            }
            options.add(new QuizOptionResponse(optionId.toString(), label));
        }
        return List.copyOf(options);
    }

    private UUID insertSession(UUID quizId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quiz_id", quizId.toString());
        body.put("total_questions", QUIZ_LENGTH);
        body.put("score", 0);
        body.put("reward_eligible", false);

        String rawJson = execute(() -> restClient.post()
                .uri("/quiz_session?select=id")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != 1) {
            throw new QuizUpstreamException("Unexpected quiz session response from Supabase.");
        }
        return parseUuid(rows.getFirst(), "id");
    }

    private List<Map<String, Object>> parseRows(String rawJson) {
        try {
            return objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
        } catch (Exception exception) {
            throw new QuizUpstreamException("Invalid quiz response from Supabase.", exception);
        }
    }

    private UUID parseUuid(Map<String, Object> row, String column) {
        try {
            return UUID.fromString(requiredString(row, column));
        } catch (QuizUpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new QuizUpstreamException("Invalid quiz data from Supabase.", exception);
        }
    }

    private String requiredString(Map<String, Object> row, String column) {
        Object rawValue = row.get(column);
        if (rawValue == null || rawValue.toString().isBlank()) {
            throw new QuizUpstreamException("Missing required quiz data from Supabase.");
        }
        return rawValue.toString();
    }

    private String requiredContent(Map<String, Object> row, String column) {
        Object rawValue = row.get(column);
        if (rawValue == null || rawValue.toString().isBlank()) {
            throw new QuizUnavailableException();
        }
        return rawValue.toString().trim();
    }

    private String execute(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientException exception) {
            throw new QuizUpstreamException("Supabase quiz request failed.", exception);
        }
    }

    private record QuestionRow(UUID id, String prompt) {
    }
}
