package com.apexwallet.ai;

/** Reserved module. Phases 0-8 do not call a model. */
public final class AiModule {

    private AiModule() {
    }

    public static String name() {
        return "apexwallet-ai";
    }
}
