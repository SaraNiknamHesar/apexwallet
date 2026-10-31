package com.apexwallet.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DomainBoundaryTest {

    @Test
    void domainDoesNotDependOnSpringOrJpa() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.apexwallet.domain");
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..");
        rule.check(classes);
    }
}
