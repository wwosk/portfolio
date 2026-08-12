package org.example.mypaintings.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;
    private StrongPassword annotation;
    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();

        annotation = mock(StrongPassword.class);
        when(annotation.minLength()).thenReturn(8);
        when(annotation.maxLength()).thenReturn(72);
        when(annotation.requireUppercase()).thenReturn(true);
        when(annotation.requireLowercase()).thenReturn(true);
        when(annotation.requireDigit()).thenReturn(true);
        when(annotation.requireSpecial()).thenReturn(true);

        validator.initialize(annotation);

        context = mock(ConstraintValidatorContext.class);
        builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    }

    @Nested
    @DisplayName("Null Value Validation")
    class NullValidationTests {

        @Test
        @DisplayName("Should accept null passwords")
        void shouldAcceptNull() {
            // given & when
            boolean result = validator.isValid(null, context);

            // then
            assertThat(result).isTrue();
            verify(context, never()).buildConstraintViolationWithTemplate(anyString());
            verify(context, never()).disableDefaultConstraintViolation();
        }
    }

    @Nested
    @DisplayName("Password Length Validation")
    class LengthValidationTests {

        @Test
        @DisplayName("Should reject password when too short")
        void shouldRejectTooShortPassword() {
            // given & when
            boolean result = validator.isValid("Ab1!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must be at least 8 characters long.");
        }

        @Test
        @DisplayName("Should reject password when too long")
        void shouldRejectTooLongPassword() {
            // given
            when(annotation.maxLength()).thenReturn(10);
            validator.initialize(annotation);

            // when
            boolean result = validator.isValid("Abcdef1234!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must be at most 10 characters long.");
        }

        @Test
        @DisplayName("Should reject password failing minimum length boundary")
        void shouldRejectWhenMinLengthExactBoundaryFails() {
            // given & when
            boolean result = validator.isValid("Abc123!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must be at least 8 characters long.");
        }

        @Test
        @DisplayName("Should accept password matching exact minimum length")
        void shouldAcceptExactlyMinLength() {
            // given & when
            boolean result = validator.isValid("Abcdef1!", context);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Character Requirements Validation")
    class CharacterRequirementsTests {

        @Test
        @DisplayName("Should reject password when uppercase letter is missing")
        void shouldRejectWhenUppercaseMissing() {
            // given & when
            boolean result = validator.isValid("abcdef1!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must contain at least one uppercase letter.");
        }

        @Test
        @DisplayName("Should reject password when lowercase letter is missing")
        void shouldRejectWhenLowercaseMissing() {
            // given & when
            boolean result = validator.isValid("ABCDEF1!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must contain at least one lowercase letter.");
        }

        @Test
        @DisplayName("Should reject password when digit is missing")
        void shouldRejectWhenDigitMissing() {
            // given & when
            boolean result = validator.isValid("Abcdefg!", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must contain at least one digit.");
        }

        @Test
        @DisplayName("Should reject password when special character is missing")
        void shouldRejectWhenSpecialMissing() {
            // given & when
            boolean result = validator.isValid("Abcdef12", context);

            // then
            assertThat(result).isFalse();
            assertViolationMessage("Password must contain at least one special character.");
        }
    }

    @Nested
    @DisplayName("Disabled Requirements Flags")
    class FlagDisabledTests {

        @Test
        @DisplayName("Should accept password without uppercase when not required")
        void shouldAcceptWithoutUppercaseWhenNotRequired() {
            // given
            when(annotation.requireUppercase()).thenReturn(false);
            validator.initialize(annotation);

            // when & then
            assertThat(validator.isValid("abcdef1!", context)).isTrue();
        }

        @Test
        @DisplayName("Should accept password without lowercase when not required")
        void shouldAcceptWithoutLowercaseWhenNotRequired() {
            // given
            when(annotation.requireLowercase()).thenReturn(false);
            validator.initialize(annotation);

            // when & then
            assertThat(validator.isValid("ABCDEF1!", context)).isTrue();
        }

        @Test
        @DisplayName("Should accept password without digit when not required")
        void shouldAcceptWithoutDigitWhenNotRequired() {
            // given
            when(annotation.requireDigit()).thenReturn(false);
            validator.initialize(annotation);

            // when & then
            assertThat(validator.isValid("Abcdefg!", context)).isTrue();
        }

        @Test
        @DisplayName("Should accept password without special character when not required")
        void shouldAcceptWithoutSpecialWhenNotRequired() {
            // given
            when(annotation.requireSpecial()).thenReturn(false);
            validator.initialize(annotation);

            // when & then
            assertThat(validator.isValid("Abcdef12", context)).isTrue();
        }
    }

    @Nested
    @DisplayName("Happy Path Validation")
    class HappyPathTests {

        @Test
        @DisplayName("Should accept valid strong password")
        void shouldAcceptStrongPassword() {
            // given & when
            boolean result = validator.isValid("Abcdef1!xyz", context);

            // then
            assertThat(result).isTrue();
            verify(context, never()).buildConstraintViolationWithTemplate(anyString());
        }

        @Test
        @DisplayName("Should accept various special characters")
        void shouldAcceptVariousSpecialCharacters() {
            // given & when & then
            assertThat(validator.isValid("Abcdef1@", context)).isTrue();
            assertThat(validator.isValid("Abcdef1#", context)).isTrue();
            assertThat(validator.isValid("Abcdef1$", context)).isTrue();
            assertThat(validator.isValid("Abcdef1!", context)).isTrue();
            assertThat(validator.isValid("Abcdef1~", context)).isTrue();
        }
    }

    private void assertViolationMessage(String expected) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(captor.capture());
        verify(builder).addConstraintViolation();

        assertThat(captor.getValue()).isEqualTo(expected);
    }
}
