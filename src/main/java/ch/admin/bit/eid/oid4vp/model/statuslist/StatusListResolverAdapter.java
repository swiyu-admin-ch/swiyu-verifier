package ch.admin.bit.eid.oid4vp.model.statuslist;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@AllArgsConstructor
@Data
public class StatusListResolverAdapter {

    public String resolveStatusList(String uri) {
        return RestClient.create().get().uri(uri).retrieve().body(String.class);
    }
}
