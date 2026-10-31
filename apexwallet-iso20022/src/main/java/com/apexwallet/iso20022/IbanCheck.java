package com.apexwallet.iso20022;

import java.math.BigInteger;

/** ISO 13616 check. This is the study implementation, not a claim about every national IBAN length. */
public final class IbanCheck {

    private IbanCheck() {
    }

    public static boolean valid(String iban) {
        if (iban == null) {
            return false;
        }
        String compact = iban.replace(" ", "").toUpperCase();
        if (compact.length() < 15 || compact.length() > 34 || !compact.chars().allMatch(Character::isLetterOrDigit)) {
            return false;
        }
        return mod97(compact.substring(4) + compact.substring(0, 4)) == 1;
    }

    public static String generate(String country, String bban) {
        String check = "00";
        int remainder = mod97(bban + country + check);
        int digits = 98 - remainder;
        return country + (digits < 10 ? "0" + digits : Integer.toString(digits)) + bban;
    }

    private static int mod97(String rearranged) {
        StringBuilder digits = new StringBuilder();
        for (char ch : rearranged.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (Character.isLetter(ch)) {
                digits.append(ch - 'A' + 10);
            } else {
                return -1;
            }
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue();
    }
}
