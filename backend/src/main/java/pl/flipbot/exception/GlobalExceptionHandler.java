package pl.flipbot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidationException(
            MethodArgumentNotValidException exception
    ) {

        Map<String, String> errors =
                new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(
                        error ->
                                errors.put(
                                        error.getField(),
                                        error.getDefaultMessage()
                                )
                );

        return ValidationErrorResponse.builder()
                .message("Validation failed")
                .errors(errors)
                .build();
    }


    @ExceptionHandler(BotAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleBotAlreadyExistsException(
            BotAlreadyExistsException exception
    ) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }


    @ExceptionHandler(BotNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBotNotFoundException(
            BotNotFoundException exception
    ) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }


    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoSuchElementException(
            NoSuchElementException exception
    ) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }


    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalStateException(
            IllegalStateException exception
    ) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception
    ) {

        log.error(
                "Database integrity violation",
                exception
        );

        return Map.of(
                "message",
                "Nie można wykonać tej operacji z powodu konfliktu danych."
        );
    }


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpectedException(
            Exception exception
    ) {

        log.error(
                "Unexpected server error",
                exception
        );

        return Map.of(
                "message",
                "Wystąpił nieoczekiwany błąd serwera."
        );
    }
}