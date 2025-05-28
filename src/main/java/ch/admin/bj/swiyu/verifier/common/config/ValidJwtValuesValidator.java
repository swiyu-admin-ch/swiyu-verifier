package ch.admin.bj.swiyu.verifier.common.config;

import com.nimbusds.jose.JWSAlgorithm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService.USED_JWS_ALGORITHM;

public class ValidJwtValuesValidator implements ConstraintValidator<ValidJwtValues, List<String>> {
    @Override
    public boolean isValid(List<String> jwtAlgorithms, ConstraintValidatorContext constraintValidatorContext) {

        // Could be more in the future, but for now we only support ES256
        var validAlgsList = List.of(USED_JWS_ALGORITHM);

        var validAlgorithms = new HashSet<>(validAlgsList.stream().map(JWSAlgorithm::getName).toList());

        return validAlgorithms.containsAll(jwtAlgorithms) && validAlgorithms.size() == new HashSet<>(jwtAlgorithms).size();
    }
}