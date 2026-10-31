package com.apexwallet.app.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigInteger;

public class IbanValidator implements ConstraintValidator<Iban, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String compact = value.replace(" ", "").toUpperCase();
        if (compact.length() < 15 || compact.length() > 34 || !compact.chars().allMatch(ch -> Character.isLetterOrDigit(ch))) {
            return false;
        }
        String rearranged = compact.substring(4) + compact.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char ch : rearranged.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (Character.isLetter(ch)) {
                digits.append(ch - 'A' + 10);
            } else {
                return false;
            }
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
