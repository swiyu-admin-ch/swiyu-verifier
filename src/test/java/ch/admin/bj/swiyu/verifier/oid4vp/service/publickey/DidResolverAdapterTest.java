package ch.admin.bj.swiyu.verifier.oid4vp.service.publickey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
public class DidResolverAdapterTest {
    @Autowired
    DidResolverAdapter didResolverAdapter;

    @Test
    public void testResolveNullDid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            didResolverAdapter.resolveDid(null);
        });
    }

    @Test
    public void testEmptyDid() {
        Assertions.assertThrows(DidResolverException.class, () -> {
            didResolverAdapter.resolveDid("");
        });
    }
}
