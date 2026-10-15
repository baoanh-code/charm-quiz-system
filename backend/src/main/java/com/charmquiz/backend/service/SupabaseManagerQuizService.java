package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.ManagerQuizImportRequest;
import com.charmquiz.backend.dto.ManagerQuizQuestionImportRequest;
import com.charmquiz.backend.dto.ManagerQuizQuestionResponse;
import com.charmquiz.backend.dto.ManagerQuizResponse;
import com.charmquiz.backend.exception.ManagerQuizNotFoundException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import com.charmquiz.backend.model.QuizStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SupabaseManagerQuizService {

    static final int QUESTION_COUNT = 10;
    static final int OPTION_COUNT = 4;
    static final String QUIZ_COLUMNS = "id,title,description,status,created_at";
    static final String QUESTION_COLUMNS =
            "id,quiz_id,question_text,explanation,created_at";
    static final String OPTION_COLUMNS =
            "id,question_id,option_text,is_correct,option_order";
    static final String IMPORT_RESULT_COLUMNS = "outcome,quiz_id";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseManagerQuizService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseManagerQuizService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<ManagerQuizResponse> getAllQuizzes() {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz?select=" + QUIZ_COLUMNS + "&order=created_at.desc,id.asc")
                .retrieve()
                .body(String.class));
        List<QuizRow> quizzes = parseRows(rawJson).stream().map(this::toQuizRow).toList();
        return assemble(quizzes);
    }

    public ManagerQuizResponse getQuiz(UUID id) {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz?id=eq." + id + "&select=" + QUIZ_COLUMNS + "&limit=1")
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.isEmpty()) {
            throw new ManagerQuizNotFoundException();
        }
        if (rows.size() != 1) {
            throw upstream("Unexpected quiz response from Supabase.");
        }
        return assemble(List.of(toQuizRow(rows.getFirst()))).getFirst();
    }

    public void validateImport(ManagerQuizImportRequest request) {
        normalize(request);
    }

    public ManagerQuizResponse importQuiz(ManagerQuizImportRequest request) {
        NormalizedImport normalized = normalize(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("p_title", normalized.title());
        body.put("p_description", normalized.description());
        body.put("p_questions", normalized.questions());

        String rawJson = execute(() -> restClient.post()
                .uri("/rpc/import_manager_quiz?select=" + IMPORT_RESULT_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class));
        Map<String, Object> result = exactlyOneResult(rawJson, "quiz import");
        String outcome = requiredString(result, "outcome");
        if ("INVALID_REQUEST".equals(outcome)) {
            throw new ManagerValidationException("Quiz import validation failed.");
        }
        if (!"OK".equals(outcome)) {
            throw upstream("Unknown quiz import outcome from Supabase.");
        }
        return getQuiz(parseUuid(result, "quiz_id"));
    }

    public ManagerQuizResponse updateStatus(UUID id, QuizStatus status) {
        Map<String, Object> body = Map.of(
                "p_quiz_id", id.toString(),
                "p_status", status.name()
        );
        String rawJson = execute(() -> restClient.post()
                .uri("/rpc/set_manager_quiz_status?select=" + IMPORT_RESULT_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class));
        Map<String, Object> result = exactlyOneResult(rawJson, "quiz status update");
        String outcome = requiredString(result, "outcome");
        if ("QUIZ_NOT_FOUND".equals(outcome)) {
            throw new ManagerQuizNotFoundException();
        }
        if (!"OK".equals(outcome)) {
            throw upstream("Unknown quiz status outcome from Supabase.");
        }
        UUID returnedId = parseUuid(result, "quiz_id");
        if (!id.equals(returnedId)) {
            throw upstream("Unexpected quiz returned by status update.");
        }
        return getQuiz(id);
    }

    private List<ManagerQuizResponse> assemble(List<QuizRow> quizzes) {
        if (quizzes.isEmpty()) {
            return List.of();
        }
        Set<UUID> quizIds = quizzes.stream().map(QuizRow::id)
                .collect(Collectors.toCollection(HashSet::new));
        List<QuestionRow> questions = getQuestions(quizIds);
        Set<UUID> questionIds = questions.stream().map(QuestionRow::id)
                .collect(Collectors.toCollection(HashSet::new));
        List<OptionRow> options = getOptions(questionIds);

        Map<UUID, List<OptionRow>> optionsByQuestion = options.stream()
                .collect(Collectors.groupingBy(OptionRow::questionId));
        Map<UUID, List<ManagerQuizQuestionResponse>> questionsByQuiz = new HashMap<>();
        for (QuestionRow question : questions) {
            List<OptionRow> questionOptions = optionsByQuestion.getOrDefault(
                    question.id(), List.of());
            ManagerQuizQuestionResponse response = mapQuestion(question, questionOptions);
            questionsByQuiz.computeIfAbsent(question.quizId(), ignored -> new ArrayList<>())
                    .add(response);
        }

        return quizzes.stream().map(quiz -> new ManagerQuizResponse(
                quiz.id().toString(),
                quiz.title(),
                quiz.description(),
                quiz.status(),
                List.copyOf(questionsByQuiz.getOrDefault(quiz.id(), List.of()))
        )).toList();
    }

    private List<QuestionRow> getQuestions(Set<UUID> quizIds) {
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_question?quiz_id=in.(" + uuidFilter(quizIds) + ")"
                        + "&select=" + QUESTION_COLUMNS
                        + "&order=created_at.asc,id.asc")
                .retrieve()
                .body(String.class));
        List<QuestionRow> rows = parseRows(rawJson).stream().map(this::toQuestionRow).toList();
        Set<UUID> ids = new HashSet<>();
        for (QuestionRow row : rows) {
            if (!quizIds.contains(row.quizId()) || !ids.add(row.id())) {
                throw upstream("Unexpected quiz question returned by Supabase.");
            }
        }
        return rows;
    }

    private List<OptionRow> getOptions(Set<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        String rawJson = execute(() -> restClient.get()
                .uri("/quiz_option?question_id=in.(" + uuidFilter(questionIds) + ")"
                        + "&select=" + OPTION_COLUMNS
                        + "&order=question_id.asc,option_order.asc,id.asc")
                .retrieve()
                .body(String.class));
        List<OptionRow> rows = parseRows(rawJson).stream().map(this::toOptionRow).toList();
        Set<UUID> ids = new HashSet<>();
        for (OptionRow row : rows) {
            if (!questionIds.contains(row.questionId()) || !ids.add(row.id())) {
                throw upstream("Unexpected quiz option returned by Supabase.");
            }
        }
        return rows;
    }

    private ManagerQuizQuestionResponse mapQuestion(
            QuestionRow question,
            List<OptionRow> unsortedOptions) {
        if (unsortedOptions.size() != OPTION_COUNT) {
            throw upstream("Manager quiz question must contain exactly four options.");
        }
        List<OptionRow> options = unsortedOptions.stream()
                .sorted(Comparator.comparingInt(OptionRow::order).thenComparing(OptionRow::id))
                .toList();
        List<String> labels = new ArrayList<>(OPTION_COUNT);
        int correctIndex = -1;
        for (int index = 0; index < options.size(); index++) {
            OptionRow option = options.get(index);
            if (option.order() != index) {
                throw upstream("Manager quiz option order is invalid.");
            }
            labels.add(option.text());
            if (option.correct()) {
                if (correctIndex != -1) {
                    throw upstream("Manager quiz question has multiple correct options.");
                }
                correctIndex = index;
            }
        }
        if (correctIndex == -1) {
            throw upstream("Manager quiz question has no correct option.");
        }
        return new ManagerQuizQuestionResponse(
                question.id().toString(),
                question.prompt(),
                List.copyOf(labels),
                correctIndex,
                question.explanation()
        );
    }

    private NormalizedImport normalize(ManagerQuizImportRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()
                || request.questions() == null
                || request.questions().size() != QUESTION_COUNT) {
            throw new ManagerValidationException("Quiz must have a title and exactly 10 questions.");
        }
        List<Map<String, Object>> questions = new ArrayList<>(QUESTION_COUNT);
        for (ManagerQuizQuestionImportRequest question : request.questions()) {
            if (question == null || question.prompt() == null || question.prompt().isBlank()
                    || question.options() == null
                    || question.options().size() != OPTION_COUNT
                    || question.correctIndex() == null
                    || question.correctIndex() < 0
                    || question.correctIndex() >= OPTION_COUNT) {
                throw new ManagerValidationException("Every question must contain four options and one correct index.");
            }
            List<String> options = question.options().stream().map(option -> {
                if (option == null || option.isBlank()) {
                    throw new ManagerValidationException("Quiz options must not be blank.");
                }
                return option.trim();
            }).toList();
            Map<String, Object> normalizedQuestion = new LinkedHashMap<>();
            normalizedQuestion.put("prompt", question.prompt().trim());
            normalizedQuestion.put("options", options);
            normalizedQuestion.put("correctIndex", question.correctIndex());
            normalizedQuestion.put("explanation", nullableTrimmed(question.explanation()));
            questions.add(normalizedQuestion);
        }
        return new NormalizedImport(
                request.title().trim(),
                nullableTrimmed(request.description()),
                List.copyOf(questions)
        );
    }

    private Map<String, Object> exactlyOneResult(String rawJson, String operation) {
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.size() != 1) {
            throw upstream("Unexpected " + operation + " response from Supabase.");
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> parseRows(String rawJson) {
        try {
            return objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
        } catch (Exception exception) {
            throw new ManagerUpstreamException("Invalid manager quiz response from Supabase.", exception);
        }
    }

    private QuizRow toQuizRow(Map<String, Object> row) {
        try {
            return new QuizRow(
                    parseUuid(row, "id"),
                    requiredContent(row, "title"),
                    nullableTrimmed(nullableString(row.get("description"))),
                    QuizStatus.valueOf(requiredString(row, "status").trim().toUpperCase(Locale.ROOT))
            );
        } catch (ManagerUpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ManagerUpstreamException("Invalid manager quiz data from Supabase.", exception);
        }
    }

    private QuestionRow toQuestionRow(Map<String, Object> row) {
        return new QuestionRow(
                parseUuid(row, "id"),
                parseUuid(row, "quiz_id"),
                requiredContent(row, "question_text"),
                nullableTrimmed(nullableString(row.get("explanation")))
        );
    }

    private OptionRow toOptionRow(Map<String, Object> row) {
        Object correct = row.get("is_correct");
        Object order = row.get("option_order");
        if (!(correct instanceof Boolean booleanValue) || !(order instanceof Number number)) {
            throw upstream("Invalid manager quiz option data from Supabase.");
        }
        int optionOrder = number.intValue();
        if (optionOrder < 0 || optionOrder >= OPTION_COUNT) {
            throw upstream("Invalid manager quiz option order from Supabase.");
        }
        return new OptionRow(
                parseUuid(row, "id"),
                parseUuid(row, "question_id"),
                requiredContent(row, "option_text"),
                booleanValue,
                optionOrder
        );
    }

    private UUID parseUuid(Map<String, Object> row, String column) {
        try {
            return UUID.fromString(requiredString(row, column));
        } catch (ManagerUpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ManagerUpstreamException("Invalid manager quiz UUID from Supabase.", exception);
        }
    }

    private String requiredContent(Map<String, Object> row, String column) {
        String value = requiredString(row, column).trim();
        if (value.isBlank()) {
            throw upstream("Blank required manager quiz data from Supabase.");
        }
        return value;
    }

    private String requiredString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null || value.toString().isBlank()) {
            throw upstream("Missing required manager quiz data from Supabase.");
        }
        return value.toString();
    }

    private String nullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullableTrimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String uuidFilter(Set<UUID> values) {
        return values.stream().sorted().map(UUID::toString).collect(Collectors.joining(","));
    }

    private String execute(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientException exception) {
            throw new ManagerUpstreamException("Supabase manager quiz request failed.", exception);
        }
    }

    private ManagerUpstreamException upstream(String message) {
        return new ManagerUpstreamException(message);
    }

    private record QuizRow(UUID id, String title, String description, QuizStatus status) {
    }

    private record QuestionRow(UUID id, UUID quizId, String prompt, String explanation) {
    }

    private record OptionRow(
            UUID id,
            UUID questionId,
            String text,
            boolean correct,
            int order) {
    }

    private record NormalizedImport(
            String title,
            String description,
            List<Map<String, Object>> questions) {
    }
}
