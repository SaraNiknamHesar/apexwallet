package com.apexwallet.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModuleTest {

    @Test
    void moduleBoundaryExists() {
        assertEquals("apexwallet-ai", AiModule.name());
    }
}
