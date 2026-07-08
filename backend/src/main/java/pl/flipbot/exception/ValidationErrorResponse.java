package pl.flipbot.exception;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ValidationErrorResponse {

    private String message;

    private Map<String, String> errors;
}
