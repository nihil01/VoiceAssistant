package com.nihil.voice.config;

import java.util.ArrayList;
import java.util.List;

public final class AsteriskStartupValidator {
    private AsteriskStartupValidator() {}

    public static void validate(AsteriskProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties.username() == null || properties.username().isBlank()) missing.add("ARI_USERNAME");
        if (properties.password() == null || properties.password().isBlank()) missing.add("ARI_PASSWORD");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Asterisk integration is enabled but required variables are missing: "
                + String.join(", ", missing));
        }
    }
}
