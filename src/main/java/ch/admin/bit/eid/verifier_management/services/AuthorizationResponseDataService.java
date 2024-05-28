package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.models.AuthorizationResponseData;
import ch.admin.bit.eid.verifier_management.repositories.AuthorizationResponseDataRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthorizationResponseDataService {

    private final AuthorizationResponseDataRepository authorizationResponseDataRepository;


    public AuthorizationResponseData getAuthorizationResponseData(String id) {
        // TODO check if authorization request id is used
        return authorizationResponseDataRepository.findById(id).orElse(null);
    }
}
