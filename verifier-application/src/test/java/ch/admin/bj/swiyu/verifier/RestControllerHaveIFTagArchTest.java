package ch.admin.bj.swiyu.verifier;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class RestControllerHaveIFTagArchTest {

    private static final Map<String, String> classByInterfaceCode = new HashMap<>();

    @Test
    void restControllerClassShouldHaveIFTag() {

        var imported = new ClassFileImporter()
                .importPackages("ch.admin.bj.swiyu");

        var rule = classes().that().areAnnotatedWith(RestController.class)
                .should(haveTagWhereIfIsDefined());

        rule.check(imported);
    }

    private ArchCondition<JavaClass> haveTagWhereIfIsDefined() {
        return new ArchCondition<>("have @Tag with description containing '(IF-xxx)' to identify the interface") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                var tags = allTagAnnotations(clazz);

                var tagWithIF = 0;
                for (var tag : tags) {
                    var interfaceCode = extractInterfaceCode(tag);
                    if (interfaceCode != null) {
                        if (classByInterfaceCode.containsKey(interfaceCode)) {
                            events.add(SimpleConditionEvent.violated(clazz, "%s has a  @Tag annotation %s but class %s already has the same code."
                                    .formatted(clazz.getFullName(), interfaceCode, classByInterfaceCode.get(interfaceCode))));
                            return;
                        }
                        classByInterfaceCode.put(interfaceCode, clazz.getFullName());
                        tagWithIF++;
                    }
                }

                if (tagWithIF > 0) {
                    events.add(SimpleConditionEvent.satisfied(clazz, clazz.getFullName() + " has (IF-xxx) information in a @Tag annotation description"));
                } else {
                    events.add(SimpleConditionEvent.violated(clazz, clazz.getFullName() + " is missing a @Tag annotation with (IF-xxx)"));
                }
            }
        };
    }

    private String extractInterfaceCode(JavaAnnotation<JavaClass> tag) {
        var pattern = Pattern.compile("(\\(IF-[0-9]+\\))");
        var descriptionOpt = tag.get("description");
        if (descriptionOpt.isPresent()) {
            String description = descriptionOpt.get().toString();
            var matcher = pattern.matcher(description);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    @NotNull
    private static List<JavaAnnotation<JavaClass>> allTagAnnotations(JavaClass clazz) {
        return clazz.getAnnotations().stream()
                .filter(RestControllerHaveIFTagArchTest::containsTagAnnotation)
                .toList();
    }

    private static boolean containsTagAnnotation(JavaAnnotation<JavaClass> annotation) {
        return annotation.getRawType().getFullName().equals("io.swagger.v3.oas.annotations.tags.Tag");
    }
}
