package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@Service
@AllArgsConstructor
@Data
public class StatusListResolverAdapter {

    private static final int MAX_STATUS_LIST_SIZE = 10485760; // 10 MB

    public String resolveStatusList(String uri, ManagementEntity managementEntity) {
        try {
            validateStatusListSize(URI.create(uri).toURL());
            return RestClient
                    .create()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

        } catch (MalformedURLException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Invalid URI: " + uri, managementEntity);
        } catch (IllegalArgumentException e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage(), managementEntity);
        }
    }

    void validateStatusListSize(URL url) {
        try {
            var connection = url.openConnection();
            var contentLength = connection.getContentLengthLong();

            if (contentLength > MAX_STATUS_LIST_SIZE) {
                throw new IllegalArgumentException("Status list size from " + url + " exceeds maximum allowed size");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate status list size from " + url, e);
        }
    }
}
