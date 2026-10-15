package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.QuizAnswerRequest;
import com.charmquiz.backend.dto.QuizAnswerResponse;
import com.charmquiz.backend.dto.QuizResultDetailResponse;
import com.charmquiz.backend.dto.QuizResultResponse;
import com.charmquiz.backend.exception.QuizAnswerAlreadySubmittedException;
import com.charmquiz.backend.exception.QuizIncompleteException;
import com.charmquiz.backend.exception.QuizOptionNotFoundException;
import com.charmquiz.backend.exception.QuizQuestionNotFoundException;
import com.charmquiz.backend.exception.QuizSessionCompletedException;
import com.charmquiz.backend.exception.QuizSessionNotFoundException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SupabaseQuizAnswerService {

    static final int QUIZ_LENGTH = 10;
    static final int OPTIONS_PER_QUESTION = 4;
    static final String RPC_COLUMNS =
            "outcome,is_correct,correct_option_id,explanation,answered_count,score,reward_eligible,completed_at";
    static final String SESSION_COLUMNS =
            "id,quiz_id,score,total_questions,reward_eligible,completed_at";
    static final String ANSWER_COLUMNS =
            "question_id,selected_option_id,is_correct";
    static final String QUESTION_COLUMNS =
            "id,quiz_id,question_text,explanation,created_at";
    static final String OPTION_COLUMNS =
            "id,question_id,option_text,is_correct";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseQuizAnswerService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseQuizAnswerService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public QuizAnswerResponse submitAnswer(QuizAnswerRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("p_session_id", request.sessionId().toString());
        body.put("p_question_id", request.questionId().toString());
        body.put("p_selected_option_id", request.optionId().toString());

        String rawJson = execute(() -> restClient.post()
                .uri("/rpc/submit_quiz_answer?select=" + RPC_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != 1) {
            throw upstream("Unexpected answer submission response from Supabase.");
        }

        Map<String, Object> row = rows.getFirst();
        String outcome = requiredString(row, "outcome");
        switch (outcome) {
            case "SESSION_NOT_FOUND" -> throw new QuizSessionNotFoundException();
            case "QUESTION_NOT_FOUND" -> throw new QuizQuestionNotFoundException();
            case "OPTION_NOT_FOUND" -> throw new QuizOptionNotFoundException();
            case "SESSION_COMPLETED" -> throw new QuizSessionCompletedException();
            case "ANSWER_ALREADY_SUBMITTED" -> throw new QuizAnswerAlreadySubmittedException();
            case "OK" -> {
                return validAnswerResponse(row);
            }
            default -> throw upstream("Unknown answer submission outcome from Supabase.");
        }
    }

    public QuizResultResponse getResult(UUID sessionId) {
        SessionRow session = getCompletedSession(sessionId);
        List<AnswerRow> answers = getAnswers(sessionId);
        if (answers.size() != QUIZ_LENGTH) {
            throw upstream("Completed quiz session does not contain exactly ten answers.");
        }

        Set<UUID> questionIds = answers.stream()
                .map(AnswerRow::questionId)
                .collect(Collectors.toCollection(HashSet::new));
        if (questionIds.size() != QUIZ_LENGTH) {
            throw upstream("Completed quiz session contains duplicate question answers.");
        }

        List<QuestionRow> questions = getQuestions(session.quizId(), questionIds);
        List<OptionRow> options = getOptions(questionIds);
        Map<UUID, AnswerRow> answersByQuestion = uniqueAnswers(answers);
        Map<UUID, List<OptionRow>> optionsByQuestion = options.stream()
                .collect(Collectors.groupingBy(OptionRow::questionId));

        List<QuizResultDetailResponse> details = new ArrayList<>(QUIZ_LENGTH);
        int correctCount = 0;
        for (QuestionRow question : questions) {
            AnswerRow answer = answersByQuestion.get(question.id());
            if (answer == null) {
                throw upstream("Missing persisted answer for quiz question.");
            }
            List<OptionRow> questionOptions = optionsByQuestion.getOrDefault(
                    question.id(), List.of());
            if (questionOptions.size() != OPTIONS_PER_QUESTION) {
                throw upstream("Quiz question does not contain exactly four options.");
            }

            OptionRow selected = null;
            OptionRow correct = null;
            Set<UUID> optionIds = new HashSet<>();
            for (OptionRow option : questionOptions) {
                if (!optionIds.add(option.id())) {
                    throw upstream("Duplicate option returned for quiz question.");
                }
                if (option.id().equals(answer.selectedOptionId())) {
                    selected = option;
                }
                if (option.correct()) {
                    if (correct != null) {
                        throw upstream("Quiz question has more than one correct option.");
                    }
                    correct = option;
                }
            }
            if (selected == null || correct == null || selected.correct() != answer.correct()) {
                throw upstream("Persisted quiz answer is inconsistent with quiz options.");
            }
            if (answer.correct()) {
                correctCount++;
            }
            details.add(new QuizResultDetailResponse(
                    question.id().toString(),
                    question.text(),
                    selected.text(),
                    correct.text(),
                    answer.correct(),
                    question.explanation()
            ));
        }

        int incorrectCount = QUIZ_LENGTH - correctCount;
        boolean rewardEligible = correctCount >= 6;
        if (session.totalQuestions() != QUIZ_LENGTH
                || session.score() != correctCount
                || session.rewardEligible() != rewardEligible) {
            throw upstream("Persisted quiz result is inconsistent with quiz answers.");
        }
        return new QuizResultResponse(
                correctCount,
                correctCount,
                incorrectCount,
                rewardEligible,
                List.copyOf(details)
        );
    }

    private QuizAnswerResponse validAnswerResponse(Map<String, Object> row) {
        boolean correct = requiredBoolean(row, "is_correct");
        UUID correctOptionId = parseUuid(row, "correct_option_id");
        int answeredCount = requiredInteger(row, "answered_count");
        int score = requiredInteger(row, "score");
        boolean rewardEligible = requiredBoolean(row, "reward_eligible");
        Object completedAt = row.get("completed_at");

        if (answeredCount < 1 || answeredCount > QUIZ_LENGTH
                || score < 0 || score > answeredCount
                || rewardEligible != (answeredCount == QUIZ_LENGTH && score >= 6)
                || (answeredCount < QUIZ_LENGTH && completedAt != null)
                || (answeredCount == QUIZ_LENGTH
                    && (completedAt == null || completedAt.toString().isBlank()))) {
            throw upstream("Invalid answer submission state returned by Supabase.");
        }
        return new QuizAnswerResponse(
                correct,
                correctOptionId.toString(),
                nullableString(row, "explanation")
        );
    }

    private SessionRow getCompletedSession(UUID sessionId) {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_session?id=eq." + sessionId
                        + "&select=" + SESSION_COLUMNS
                        + "&limit=1")
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.isEmpty()) {
            throw new QuizSessionNotFoundException();
        }
        if (rows.size() != 1) {
            throw upstream("Unexpected quiz session response from Supabase.");
        }
        Map<String, Object> row = rows.getFirst();
        if (!sessionId.equals(parseUuid(row, "id"))) {
            throw upstream("Unexpected quiz session returned by Supabase.");
        }
        Object completedAt = row.get("completed_at");
        if (completedAt == null) {
            throw new QuizIncompleteException();
        }
        if (completedAt.toString().isBlank()) {
            throw upstream("Invalid quiz completion timestamp from Supabase.");
        }
        return new SessionRow(
                parseUuid(row, "quiz_id"),
                requiredInteger(row, "score"),
                requiredInteger(row, "total_questions"),
                requiredBoolean(row, "reward_eligible")
        );
    }

    private List<AnswerRow> getAnswers(UUID sessionId) {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_answer?session_id=eq." + sessionId
                        + "&select=" + ANSWER_COLUMNS
                        + "&order=question_id.asc"
                        + "&limit=" + (QUIZ_LENGTH + 1))
                .retrieve()
                .body(String.class));
        return parseRows(rawJson).stream()
                .map(row -> new AnswerRow(
                        parseUuid(row, "question_id"),
                        parseUuid(row, "selected_option_id"),
                        requiredBoolean(row, "is_correct")
                ))
                .toList();
    }

    private List<QuestionRow> getQuestions(UUID quizId, Set<UUID> questionIds) {
        String filter = uuidFilter(questionIds);
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_question?quiz_id=eq." + quizId
                        + "&id=in.(" + filter + ")"
                        + "&select=" + QUESTION_COLUMNS
                        + "&order=created_at.asc,id.asc"
                        + "&limit=" + (QUIZ_LENGTH + 1))
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != QUIZ_LENGTH) {
            throw upstream("Quiz result questions are incomplete.");
        }
        Set<UUID> returnedIds = new HashSet<>();
        List<QuestionRow> questions = new ArrayList<>(QUIZ_LENGTH);
        for (Map<String, Object> row : rows) {
            UUID id = parseUuid(row, "id");
            UUID returnedQuizId = parseUuid(row, "quiz_id");
            if (!quizId.equals(returnedQuizId)
                    || !questionIds.contains(id)
                    || !returnedIds.add(id)) {
                throw upstream("Unexpected quiz question returned by Supabase.");
            }
            questions.add(new QuestionRow(
                    id,
                    requiredContent(row, "question_text"),
                    nullableString(row, "explanation")
            ));
        }
        return List.copyOf(questions);
    }

    private List<OptionRow> getOptions(Set<UUID> questionIds) {
        String filter = uuidFilter(questionIds);
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_option?question_id=in.(" + filter + ")"
                        + "&select=" + OPTION_COLUMNS
                        + "&order=question_id.asc,id.asc"
                        + "&limit=" + (QUIZ_LENGTH * OPTIONS_PER_QUESTION + 1))
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != QUIZ_LENGTH * OPTIONS_PER_QUESTION) {
            throw upstream("Quiz result options are incomplete.");
        }
        Set<UUID> optionIds = new HashSet<>();
        List<OptionRow> options = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            UUID id = parseUuid(row, "id");
            UUID questionId = parseUuid(row, "question_id");
            if (!optionIds.add(id) || !questionIds.contains(questionId)) {
                throw upstream("Unexpected quiz option returned by Supabase.");
            }
            options.add(new OptionRow(
                    id,
                    questionId,
                    requiredContent(row, "option_text"),
                    requiredBoolean(row, "is_correct")
            ));
        }
        return List.copyOf(options);
    }

    private Map<UUID, AnswerRow> uniqueAnswers(List<AnswerRow> answers) {
        Map<UUID, AnswerRow> result = new HashMap<>();
        for (AnswerRow answer : answers) {
            if (result.putIfAbsent(answer.questionId(), answer) != null) {
                throw upstream("Duplicate persisted answer returned by Supabase.");
            }
        }
        return result;
    }

    private String uuidFilter(Set<UUID> values) {
        return values.stream().sorted().map(UUID::toString).collect(Collectors.joining(","));
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
        Object value = row.get(column);
        if (value == null || value.toString().isBlank()) {
            throw upstream("Missing required quiz data from Supabase.");
        }
        return value.toString();
    }

    private String requiredContent(Map<String, Object> row, String column) {
        return requiredString(row, column).trim();
    }

    private String nullableString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }

    private boolean requiredBoolean(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw upstream("Invalid boolean quiz data from Supabase.");
    }

    private int requiredInteger(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw upstream("Invalid numeric quiz data from Supabase.");
    }

    private String execute(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientException exception) {
            throw new QuizUpstreamException("Supabase quiz request failed.", exception);
        }
    }

    private QuizUpstreamException upstream(String message) {
        return new QuizUpstreamException(message);
    }

    private record SessionRow(
            UUID quizId,
            int score,
            int totalQuestions,
            boolean rewardEligible
    ) {
    }

    private record AnswerRow(UUID questionId, UUID selectedOptionId, boolean correct) {
    }

    private record QuestionRow(UUID id, String text, String explanation) {
    }

    private record OptionRow(UUID id, UUID questionId, String text, boolean correct) {
    }
}
