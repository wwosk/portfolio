package org.example.mypaintings.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.example.mypaintings.exception.access.MissingRequiredRoleException;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.exception.files.FileStorageException;
import org.example.mypaintings.exception.messages.SelfMessageException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final List<String> FORBIDDEN_STRUCTURAL = List.of(
            "Exception", "Throwable", "Error:",
            "java.", "javax.", "jakarta.", "org.springframework", "org.hibernate", "org.example",
            "\tat ", "Caused by", "Suppressed:",
            "SQL", "PSQL", "constraint", "duplicate key", "table", "column", "Detail:", "hint:",
            "/home/", "/tmp/", "/var/", "C:\\", "file:/"
    );

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("\\(\\w+\\.java:\\d+\\)"),
            Pattern.compile("\\bat\\s+[\\w$]+\\.[\\w$]+\\("),
            Pattern.compile("\\bat\\s+[\\w$]+\\.[\\w$]+\\(")
    );

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static Stream<Arguments> simpleMappings() {
        String msg = "test-message";
        return Stream.of(
                Arguments.of(new ResourceNotFoundException(msg), HttpStatus.NOT_FOUND, msg),
                Arguments.of(new ConflictException(msg), HttpStatus.CONFLICT, msg),
                Arguments.of(new MissingRequiredRoleException(msg), HttpStatus.FORBIDDEN, msg),
                Arguments.of(new UnauthorizedAccessException(msg), HttpStatus.UNAUTHORIZED, msg),
                Arguments.of(new BusinessValidationException(msg), HttpStatus.BAD_REQUEST, msg),
                Arguments.of(new SelfMessageException(msg), HttpStatus.BAD_REQUEST, msg)
        );
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("simpleMappings")
    @DisplayName("Should map domain exception to proper status and message")
    void shouldMapExceptionToProperStatusAndMessage(Exception ex,
                                                    HttpStatus expectedStatus,
                                                    String expectedMessage) {
        // given & when
        ResponseEntity<ErrorResponse> response = dispatch(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should return 401 with generic message for BadCredentialsException without echoing input")
    void badCredentialsShouldReturnGenericMessageWithoutEchoingInput() {
        // given
        var ex = new BadCredentialsException("Bad credentials for user=alice password=hunter2");

        // when
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Invalid credentials")
                .doesNotContain("alice", "hunter2", "password");
        assertNoSecretLeak(response.getBody(), "alice", "hunter2");
    }

    @ParameterizedTest(name = "{0} -> {1} (no leak)")
    @MethodSource("simpleMappings")
    @DisplayName("Should not leak implementation details for 4xx responses")
    void shouldNotLeakDetailsFor4xx(Exception ex, HttpStatus expectedStatus, String ignored) {
        // given & when
        ResponseEntity<ErrorResponse> response = dispatch(ex);

        // then
        assertNoStructuralLeak(response.getBody());
    }

    @Nested
    @DisplayName("No Information Leak Tests")
    class NoLeakTests {

        private static final String MALICIOUS_MESSAGE =
                "SQL: SELECT * FROM users WHERE password='hunter2' " +
                        "at org.example.mypaintings.service.UserService.load(UserService.java:42) " +
                        "Caused by: org.postgresql.util.PSQLException: duplicate key 'uk_users_email' " +
                        "Detail: Key (email)=(a@b.c) already exists. File: /home/app/secrets.yml";

        @Test
        @DisplayName("Should not leak message, class name, or stack trace in global exception")
        void globalExceptionMustNotLeakInternals() {
            // given
            var ex = new IllegalStateException(MALICIOUS_MESSAGE);

            // when
            var body = handler.handleGlobalException(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(500);
            assertThat(body.getMessage())
                    .startsWith("Unexpected error occurred. Reference ID: ");
            assertNoSecretLeak(body, "hunter2", MALICIOUS_MESSAGE);
        }

        @Test
        @DisplayName("Should not leak file paths or internal messages in FileStorageException")
        void fileStorageMustNotLeakInternals() {
            // given
            var ex = new FileStorageException("Cannot write /home/app/uploads/secret.png");

            // when
            var body = handler.handleFileStorage(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getMessage())
                    .startsWith("Could not store the file. Reference ID: ");
            assertNoSecretLeak(body, "/home/app/uploads/secret.png", "secret.png");
        }

        @Test
        @DisplayName("Should not leak parser details in HttpMessageNotReadableException")
        void notReadableMustNotLeakParserDetails() {
            // given
            var ex = new HttpMessageNotReadableException(
                    "JSON parse error at line 3 column 7 in /tmp/payload.json: unexpected token",
                    null);

            // when
            var body = handler.handleNotReadable(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getMessage()).isEqualTo("Malformed JSON request");
            assertNoSecretLeak(body, "/tmp/payload.json", "unexpected token");
        }
    }

    @Nested
    @DisplayName("Data Integrity Violation Tests")
    @ExtendWith(MockitoExtension.class)
    class DataIntegrityViolationTests {

        @Mock
        private DataIntegrityViolationException ex;

        @Test
        @DisplayName("Should return nickname conflict message with 409 and no SQL leak")
        void shouldReturnNicknameConflictWithoutLeak() {
            // given
            stubCause("Duplicate entry 'john' for key 'uk_users_nickname'");

            // when
            var body = handler.handleDataIntegrityViolation(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(409);
            assertThat(body.getMessage()).isEqualTo("A user with this nickname already exists.");
            assertNoSecretLeak(body, "john", "uk_users_nickname");
        }

        @Test
        @DisplayName("Should return email conflict message with 409 and no SQL leak")
        void shouldReturnEmailConflictWithoutLeak() {
            // given
            stubCause("Duplicate entry 'a@b.c' for key 'uk_users_email'");

            // when
            var body = handler.handleDataIntegrityViolation(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(409);
            assertThat(body.getMessage()).isEqualTo("A user with this email already exists.");
            assertNoSecretLeak(body, "a@b.c", "uk_users_email");
        }

        @Test
        @DisplayName("Should return generic message with 409 for other constraints and no SQL leak")
        void shouldReturnGenericMessageWithoutLeak() {
            // given
            stubCause("ERROR: insert or update on table violates foreign key constraint \"fk_paintings_user\"");

            // when
            var body = handler.handleDataIntegrityViolation(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(409);
            assertThat(body.getMessage()).isEqualTo("Database conflict error.");
            assertNoSecretLeak(body, "fk_paintings_user", "insert or update");
        }

        @Test
        @DisplayName("Should not leak raw database messages")
        void rawDbMessageMustNotLeak() {
            // given
            stubCause("duplicate key value violates unique constraint \"uk_users_email\" " +
                    "Detail: Key (email)=(secret@example.com) already exists.");

            // when
            var body = handler.handleDataIntegrityViolation(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertNoSecretLeak(body, "secret@example.com", "uk_users_email", "duplicate key");
        }

        private void stubCause(String message) {
            lenient().when(ex.getMostSpecificCause())
                    .thenReturn(new RuntimeException(message));
        }
    }

    @Nested
    @DisplayName("Reference ID Tests")
    class ReferenceIdTests {

        @Test
        @DisplayName("Should generate unique reference IDs for global exceptions")
        void globalExceptionShouldGenerateUniqueReferenceId() {
            // given
            var ex = new RuntimeException("boom");

            // when
            String first = handler.handleGlobalException(ex).getBody().getMessage();
            String second = handler.handleGlobalException(ex).getBody().getMessage();

            // then
            assertThat(first).isNotEqualTo(second);
            assertThat(extractReferenceId(first)).isNotBlank();
            assertThat(extractReferenceId(second)).isNotBlank();
            assertThat(extractReferenceId(first)).hasSize(36);
        }

        @Test
        @DisplayName("Should generate unique reference IDs for file storage exceptions")
        void fileStorageShouldGenerateUniqueReferenceId() {
            // given
            var ex = new FileStorageException("Cannot write file");

            // when
            String first = handler.handleFileStorage(ex).getBody().getMessage();
            String second = handler.handleFileStorage(ex).getBody().getMessage();

            // then
            assertThat(first).isNotEqualTo(second);
        }

        private String extractReferenceId(String message) {
            int idx = message.indexOf("Reference ID: ");
            return idx < 0 ? "" : message.substring(idx + "Reference ID: ".length()).trim();
        }
    }

    @Nested
    @DisplayName("Method Argument Not Valid Tests")
    class MethodArgumentNotValidTests {

        @Test
        @DisplayName("Should return 400 with formatted field errors")
        void shouldFormatFieldErrors() throws NoSuchMethodException {
            // given
            var bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError("target", "email", "must not be blank"));
            bindingResult.addError(new FieldError("target", "password", "must be at least 8 characters"));
            var ex = new MethodArgumentNotValidException(methodParameter(), bindingResult);

            // when
            var body = handler.handleMethodArgumentNotValid(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(400);
            assertThat(body.getMessage())
                    .contains("email: must not be blank")
                    .contains("password: must be at least 8 characters")
                    .contains("; ");
            assertNoStructuralLeak(body);
        }

        @Test
        @DisplayName("Should not leak binding internals or class names")
        void shouldNotLeakBindingInternals() throws NoSuchMethodException {
            // given
            var bindingResult = new BeanPropertyBindingResult(new Object(), "secretInternalName");
            bindingResult.addError(new FieldError("target", "email", "must not be blank"));
            var ex = new MethodArgumentNotValidException(methodParameter(), bindingResult);

            // when
            var body = handler.handleMethodArgumentNotValid(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getMessage())
                    .doesNotContain("secretInternalName", "BeanPropertyBindingResult", "MethodArgumentNotValidException");
            assertNoStructuralLeak(body);
        }

        @Test
        @DisplayName("Should not echo submitted field values in validation errors")
        void shouldNotEchoSubmittedFieldValue() throws NoSuchMethodException {
            // given
            var bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError(
                    "target",
                    "password",
                    "hunter2",
                    false,
                    new String[]{"Size"},
                    null,
                    "must be at least 8 characters"
            ));
            var ex = new MethodArgumentNotValidException(methodParameter(), bindingResult);

            // when
            var body = handler.handleMethodArgumentNotValid(ex).getBody();

            // then
            assertThat(body).isNotNull();
            assertThat(body.getMessage()).isEqualTo("password: must be at least 8 characters");
            assertNoSecretLeak(body, "hunter2");
        }

        private MethodParameter methodParameter() throws NoSuchMethodException {
            return new MethodParameter(
                    MethodArgumentNotValidTests.class.getDeclaredMethod("dummyMethod", Object.class), 0);
        }

        @SuppressWarnings("unused")
        private void dummyMethod(Object arg) { }
    }

    @Nested
    @DisplayName("Error Response Shape Tests")
    class ErrorResponseShapeTests {

        @Test
        @DisplayName("Should expose only status, message, and timestamp in ErrorResponse")
        void errorResponseMustExposeOnlyStatusMessageAndTimestamp() {
            // given & when
            List<String> fieldNames = Arrays.stream(ErrorResponse.class.getDeclaredFields())
                    .filter(f -> !f.isSynthetic())
                    .map(Field::getName)
                    .toList();

            // then
            assertThat(fieldNames).containsExactlyInAnyOrder("status", "message", "timestamp");
        }

        @Test
        @DisplayName("Should not expose extra getters in ErrorResponse")
        void errorResponseMustNotExposeExtraGetters() {
            // given
            List<String> allowedGetters = List.of("status", "message", "timestamp", "class");

            // when
            List<String> extraGetters = Arrays.stream(ErrorResponse.class.getMethods())
                    .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                    .map(m -> m.getName().substring(3).toLowerCase())
                    .filter(name -> !allowedGetters.contains(name))
                    .toList();

            // then
            assertThat(extraGetters).isEmpty();
        }
    }

    @Nested
    @DisplayName("Logging Leak Tests")
    class LoggingLeakTests {

        private Logger logger;
        private ListAppender<ILoggingEvent> appender;

        @BeforeEach
        void attachAppender() {
            logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void detachAppender() {
            logger.detachAppender(appender);
        }

        @Test
        @DisplayName("Should not contain passwords in bad credentials logs")
        void badCredentialsLogMustNotContainPassword() {
            // given & when
            handler.handleBadCredentials(
                    new BadCredentialsException("Bad credentials for user=alice password=hunter2"));

            // then
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(msg -> assertThat(msg).doesNotContain("hunter2"));
        }

        @Test
        @DisplayName("Should log details for global exceptions at error level")
        void globalExceptionShouldLogDetailsForOperators() {
            // given
            var ex = new IllegalStateException("internal detail");

            // when
            handler.handleGlobalException(ex);

            // then
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getThrowableProxy()).isNotNull();
                    });
        }

        @Test
        @DisplayName("Should log resource not found exceptions at warn level")
        void resourceNotFoundLogShouldBeWarn() {
            // given & when
            handler.handleResourceNotFound(new ResourceNotFoundException("Painting 42 not found"));

            // then
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }

    private ResponseEntity<ErrorResponse> dispatch(Exception ex) {
        Function<Exception, ResponseEntity<ErrorResponse>> fn = switch (ex) {
            case ResourceNotFoundException _ -> e2 -> handler.handleResourceNotFound((ResourceNotFoundException) e2);
            case ConflictException _ -> e2 -> handler.handleConflictException((ConflictException) e2);
            case MissingRequiredRoleException _ -> e2 -> handler.handleMissingRequiredRole((MissingRequiredRoleException) e2);
            case UnauthorizedAccessException _ -> e2 -> handler.handleUnauthorizedAccess((UnauthorizedAccessException) e2);
            case BusinessValidationException _ -> e2 -> handler.handleValidationException((BusinessValidationException) e2);
            case BadCredentialsException _ -> e2 -> handler.handleBadCredentials((BadCredentialsException) e2);
            case SelfMessageException _ -> e2 -> handler.handleSelfMessage((SelfMessageException) e2);
            default -> throw new IllegalArgumentException("Unhandled: " + ex.getClass());
        };
        return fn.apply(ex);
    }

    private static void assertNoStructuralLeak(ErrorResponse body) {
        assertThat(body).isNotNull();
        String message = body.getMessage();
        assertThat(message).isNotBlank();

        for (String forbidden : FORBIDDEN_STRUCTURAL) {
            assertThat(message).doesNotContainIgnoringCase(forbidden);
        }

        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            assertThat(pattern.matcher(message).find()).isFalse();
        }
    }

    private static void assertNoSecretLeak(ErrorResponse body, String... secrets) {
        assertNoStructuralLeak(body);
        for (String secret : secrets) {
            assertThat(body.getMessage()).doesNotContain(secret);
        }
    }
}
