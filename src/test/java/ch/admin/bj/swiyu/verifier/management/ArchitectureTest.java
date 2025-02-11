/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packagesOf = {VerifierManagementApplication.class},
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class}
)
public class ArchitectureTest {

    @ArchTest
    public static final ArchTests codingRules = ArchTests.in(CodingRules.class);

    @ArchTest
    public static final ArchTests architectureRules = ArchTests.in(ArchitectureRules.class);

    @ArchTest
    public static final ArchTests namingRules = ArchTests.in(NamingRules.class);

    private static Architectures.LayeredArchitecture getArchitectureLayers() {
        return layeredArchitecture()
                .consideringAllDependencies()
                .layer(Layer.API.layerName)
                .definedBy(Layer.API.packageIdentifiers)
                .layer(Layer.DOMAIN.layerName)
                .definedBy(Layer.DOMAIN.packageIdentifiers)
                .layer(Layer.SERVICE.layerName)
                .definedBy(Layer.SERVICE.packageIdentifiers)
                .optionalLayer(Layer.INFRASTRUCTURE.layerName)
                .definedBy(Layer.INFRASTRUCTURE.packageIdentifiers)
                .optionalLayer(Layer.WEB.layerName)
                .definedBy(Layer.WEB.packageIdentifiers)
                .optionalLayer(Layer.COMMON.layerName)
                .definedBy(Layer.COMMON.packageIdentifiers)
                .whereLayer(Layer.WEB.layerName)
                .mayNotBeAccessedByAnyLayer();
    }

    private static String[] concat(String[]... arrays) {
        return Arrays.stream(arrays)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

    @Getter
    enum Layer {
        DOMAIN("Domain", "..domain.."),
        SERVICE("Service", "..service.."),
        API("Api", "..api.."),
        INFRASTRUCTURE("Infrastructure", "..infrastructure.."),
        WEB("Web", "..infrastructure.web.."),
        COMMON("Common", "..common..");

        final String layerName;
        final String[] packageIdentifiers;

        Layer(String layerName, String... packageIdentifiers) {
            this.layerName = layerName;
            this.packageIdentifiers = packageIdentifiers;
        }
    }

    @Getter
    enum Naming {
        DOMAIN("Domain", "..domain.."),
        REPOSITORY("Repository", "..domain.."),
        SERVICE("Service", "..service.."),
        MAPPER("Mapper", "..mapper.."),
        FACTORY("Factory", "..service..", "..infrastructure.."),
        CONTROLLER("Controller", "..web..");

        final String classNameEnding;
        final String[] packageIdentifiers;

        Naming(String nameEnding, String... packageIdentifiers) {
            this.classNameEnding = nameEnding;
            this.packageIdentifiers = packageIdentifiers;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CodingRules {

        @ArchTest
        public static final ArchRule classes_should_not_use_field_injection = noFields()
                .should(beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired"))
                .because("field injection is evil, see http://olivergierke.de/2013/11/why-field-injection-is-evil/");
        @ArchTest
        static final ArchRule classes_should_not_throw_generic_exceptions =
                GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

        @ArchTest
        static final ArchRule classes_should_not_use_java_util_logging =
                GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
        @ArchTest
        static final ArchRule service_classes_should_not_have_state = classes()
                .that(annotatedWith(Service.class))
                .or(annotatedWith(Component.class))
                .should()
                .haveOnlyFinalFields()
                .as("Spring services should only have final fields")
                .because(
                        """
                                final fields make sure, that a service has no state. \
                                This is required when running a microservice with multiple instances.\
                                """
                )
                .allowEmptyShould(true);

        /**
         * ArchRules which support freezing. @see <a
         * href="https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules">freezing_arch_rules</a>
         */
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Freezing {

            @ArchTest
            static final ArchRule freezing_classes_should_not_throw_generic_exceptions = FreezingArchRule.freeze(
                    classes_should_not_throw_generic_exceptions
            );

            @ArchTest
            static final ArchRule freezing_classes_should_not_use_java_util_logging = FreezingArchRule.freeze(
                    classes_should_not_use_java_util_logging
            );

            @ArchTest
            static final ArchRule freezing_classes_should_not_use_field_injection = FreezingArchRule.freeze(
                    classes_should_not_use_field_injection
            );

            @ArchTest
            static final ArchRule freezing_service_classes_should_not_have_state = FreezingArchRule.freeze(
                    service_classes_should_not_have_state
            );
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ArchitectureRules {

        @ArchTest
        static final ArchRule architecture_is_respected = getArchitectureLayers()
                .whereLayer(Layer.API.layerName)
                .mayOnlyBeAccessedByLayers(
                        Layer.SERVICE.layerName,
                        Layer.INFRASTRUCTURE.layerName
                )
                .whereLayer(Layer.DOMAIN.layerName)
                .mayOnlyBeAccessedByLayers(
                        Layer.SERVICE.layerName,
                        // INFRASTRUCTURE: due to simplicity we allow infrastructure in general, but only for
                        // exceptions. see rule infrastructure_may_access_only_exceptions_from_domain
                        Layer.INFRASTRUCTURE.layerName)
                .whereLayer(Layer.INFRASTRUCTURE.layerName)
                .mayNotBeAccessedByAnyLayer();
        /**
         * Infrastructure Layer is allowed to access domain exceptions, but no other domain classes. This
         * is an exception so we don't have to convert domain exceptions to infrastructure exceptions.
         */
        @ArchTest
        static final ArchRule infrastructure_may_access_only_exceptions_from_domain = classes()
                .that().resideInAnyPackage(Layer.DOMAIN.packageIdentifiers)
                .and().areNotAssignableTo(Exception.class)
                .should().onlyBeAccessed()
                .byAnyPackage(concat(Layer.DOMAIN.packageIdentifiers, Layer.SERVICE.packageIdentifiers));
        @ArchTest
        static final ArchRule no_cycles_between_slices = SlicesRuleDefinition.slices()
                .matching("..verifier.(**)..")
                .should()
                .beFreeOfCycles();

        /**
         * ArchRules which support freezing. @see <a
         * href="https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules">freezing_arch_rules</a>
         */
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Freezing {

            @ArchTest
            static final ArchRule freezing_architecture_is_respected = FreezingArchRule.freeze(
                    architecture_is_respected
            );

            @ArchTest
            static final ArchRule freezing_no_cycles_between_slices = FreezingArchRule.freeze(no_cycles_between_slices);
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private static class NamingRules {

        @ArchTest
        static final ArchRule controllers = classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith(Naming.CONTROLLER.classNameEnding)
                .andShould()
                .resideInAnyPackage(Naming.CONTROLLER.packageIdentifiers)
                .allowEmptyShould(true);

        @ArchTest
        static final ArchRule entities = classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .resideInAnyPackage(Naming.DOMAIN.packageIdentifiers)
                .allowEmptyShould(true);

        @ArchTest
        static final ArchRule repositories = classes()
                .that()
                .areAnnotatedWith(Repository.class)
                .should()
                .haveSimpleNameEndingWith(Naming.REPOSITORY.classNameEnding)
                .andShould()
                .resideInAnyPackage(Naming.REPOSITORY.packageIdentifiers)
                .allowEmptyShould(true);

        @ArchTest
        static final ArchRule services = classes()
                .that()
                .areAnnotatedWith(Service.class)
                .and()
                .haveSimpleNameEndingWith(Naming.SERVICE.classNameEnding)
                .should()
                .resideInAnyPackage(Naming.SERVICE.packageIdentifiers)
                .allowEmptyShould(true);

        @ArchTest
        static final ArchRule mappers = classes()
                .that()
                .areAnnotatedWith(Service.class)
                .and()
                .haveSimpleNameEndingWith(Naming.MAPPER.classNameEnding)
                .should()
                .resideInAnyPackage(Naming.MAPPER.packageIdentifiers)
                .allowEmptyShould(true);

        @ArchTest
        static final ArchRule interfaces_should_not_have_names_ending_with_the_word_interface = noClasses()
                .that()
                .areInterfaces()
                .should()
                .haveNameMatching(".*Interface");

        @ArchTest
        static final ArchRule interfaces_should_not_have_simple_class_names_containing_the_word_interface = noClasses()
                .that()
                .areInterfaces()
                .should()
                .haveSimpleNameContaining("Interface");

        @ArchTest
        static final ArchRule interfaces_must_not_be_placed_in_implementation_packages = noClasses()
                .that()
                .resideInAPackage("..service")
                .should()
                .beInterfaces();
    }
}
