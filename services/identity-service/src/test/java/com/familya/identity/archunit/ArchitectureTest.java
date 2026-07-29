package com.familya.identity.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture rules for the Identity service. Rejects cross-service
 * imports, framework coupling in the domain, and synchronous
 * cycles. Mirrored by the repository-wide scanner in
 * {@code tooling/architecture-scan}.
 */
@AnalyzeClasses(
        packages = "com.familya.identity",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_jpa =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.identity..")
                    .should().dependOnClassesThat().resideInAPackage("com.familya.member..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.relationship..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.treeaccess..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.event..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.media..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.sharing..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.search..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.transfer..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.auditops..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.migration..");

    @ArchTest
    static final ArchRule no_cycles = slices().matching("com.familya.identity.(*)..")
            .should().beFreeOfCycles();
}
