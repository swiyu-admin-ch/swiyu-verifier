package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.repositories.AuthorizationResponseDataRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class AuthorizationResponseDataService {

    private final AuthorizationResponseDataRepository authorizationResponseDataRepository;

    public AuthorizationResponseData getAuthorizationResponseData(UUID id) {

        // TODO check if exception or null
        return authorizationResponseDataRepository.findById(id).orElse(null);
    }
}
