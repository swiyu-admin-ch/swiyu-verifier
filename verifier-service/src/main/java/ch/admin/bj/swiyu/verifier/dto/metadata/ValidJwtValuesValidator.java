package ch.admin.bj.swiyu.verifier.dto.metadata;

import com.nimbusds.jose.JWSAlgorithm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;

public class ValidJwtValuesValidator implements ConstraintValidator<ValidJwtValues, List<String>> {
    @Override
    public boolean isValid(List<String> jwtAlgorithms, ConstraintValidatorContext constraintValidatorContext) {

        if (jwtAlgorithms == null || jwtAlgorithms.isEmpty()) {
            return false;
        }

        // Could be more in the future, but for now we only support ES256
        var validAlgsList = List.of(JWSAlgorithm.ES256);

        var validAlgorithms = new HashSet<>(validAlgsList.stream().map(JWSAlgorithm::getName).toList());

        return validAlgorithms.containsAll(jwtAlgorithms) && validAlgorithms.size() == new HashSet<>(jwtAlgorithms).size();
    }
}