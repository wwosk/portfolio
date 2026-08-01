package org.example.mypaintings.exception;

import org.example.mypaintings.exception.access.MissingRequiredRoleException;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.exception.files.FileStorageException;
import org.example.mypaintings.exception.messages.SelfMessageException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;



@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("[ERR_RES_NOT_FOUND] Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex) {
        log.warn("[ERR_DATA_CONFLICT] Data conflict: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MissingRequiredRoleException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequiredRole(MissingRequiredRoleException ex) {
        log.warn("[ERR_MISSING_ROLE] Missing role: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        log.warn("[ERR_UNAUTHORIZED] Unauthorized access attempt: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(BusinessValidationException ex) {
        log.warn("[ERR_BUSINESS_VALIDATION] Validation error: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException __) {
        log.warn("[ERR_BAD_CREDENTIALS] Failed login attempt");
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid credentials"
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(SelfMessageException.class)
    public ResponseEntity<ErrorResponse> handleSelfMessage(SelfMessageException ex) {
        log.warn("[ERR_SELF_MESSAGE] Invalid message attempt (user messaging self): {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("[ERR_INTERNAL_SERVER_ERROR - ID: {}] An unexpected internal server error occurred: ", errorId, ex);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Unexpected error occurred. Reference ID: " + errorId
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String rootMessage = ex.getMostSpecificCause().getMessage().toLowerCase();
        log.warn("[ERR_DATA_INTEGRITY] Database constraint violation: {}", rootMessage);

        String message;
        if (rootMessage.contains("nickname")) {
            message = "A user with this nickname already exists.";
        } else if (rootMessage.contains("email")) {
            message = "A user with this email already exists.";
        } else {
            message = "Database conflict error.";
        }

        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), message);
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[ERR_VALIDATION] {}", message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("[ERR_MALFORMED_JSON] {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON request"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(FileStorageException ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("[ERR_FILE_STORAGE - ID: {}] {}", errorId, ex.getMessage(), ex);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Could not store the file. Reference ID: " + errorId
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }


}