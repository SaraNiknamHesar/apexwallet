package com.apexwallet.app.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanValidatorTest {

    private final IbanValidator validator = new IbanValidator();

    @Test
    void acceptsAKnownIbanAndRejectsATamperedOne() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("GB82 WEST 1234 5698 7654 32", null));
        assertFalse(validator.isValid("GB82WEST12345698765433", null));
        assertFalse(validator.isValid("<!DOCTYPE", null));
    }
}
