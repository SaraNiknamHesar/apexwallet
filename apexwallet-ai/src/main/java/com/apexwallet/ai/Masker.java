package com.apexwallet.ai;

import java.util.regex.Pattern;

public final class Masker {

    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{6,}");

    private Masker() {
    }

    public static String mask(String value) {
        if (value == null) {
            return "";
        }
        return LONG_NUMBER.matcher(value).replaceAll("****");
    }
}
