package org.example.mypaintings.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String UPPERCASE_REGEX = ".*[A-Z].*";
    private static final String LOWERCASE_REGEX = ".*[a-z].*";
    private static final String DIGIT_REGEX     = ".*\\d.*";
    private static final String SPECIAL_REGEX   =
            ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`].*";

    private StrongPassword annotation;

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
        this.annotation = constraintAnnotation;
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (password.length() < annotation.minLength()) {
            return fail(context,
                    "Password must be at least " + annotation.minLength() + " characters long.");
        }

        if (password.length() > annotation.maxLength()) {
            return fail(context,
                    "Password must be at most " + annotation.maxLength() + " characters long.");
        }

        if (annotation.requireUppercase() && !password.matches(UPPERCASE_REGEX)) {
            return fail(context, "Password must contain at least one uppercase letter.");
        }

        if (annotation.requireLowercase() && !password.matches(LOWERCASE_REGEX)) {
            return fail(context, "Password must contain at least one lowercase letter.");
        }

        if (annotation.requireDigit() && !password.matches(DIGIT_REGEX)) {
            return fail(context, "Password must contain at least one digit.");
        }

        if (annotation.requireSpecial() && !password.matches(SPECIAL_REGEX)) {
            return fail(context, "Password must contain at least one special character.");
        }

        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
        return false;
    }
}