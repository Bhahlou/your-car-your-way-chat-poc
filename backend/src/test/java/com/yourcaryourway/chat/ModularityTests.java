package com.yourcaryourway.chat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture test (Spring Modulith): fails if a module accesses another's internal classes,
 * or if a dependency cycle appears. This is what keeps the modular monolith's boundaries intact over time.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ChatPocApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    /** Generates the modules' PlantUML diagrams in target/spring-modulith-docs (useful for documentation). */
    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
