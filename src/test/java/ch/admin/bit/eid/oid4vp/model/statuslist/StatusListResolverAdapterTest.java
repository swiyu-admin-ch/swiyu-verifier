package ch.admin.bit.eid.oid4vp.model.statuslist;

import ch.admin.bit.eid.oid4vp.config.UrlRewriteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatusListResolverAdapterTest {

    private StatusListResolverAdapter statusListResolverAdapter;

    @BeforeEach
    void setUp() {
        statusListResolverAdapter = new StatusListResolverAdapter(new UrlRewriteConfig());
    }

    @Test
    void testValidateStatusListSize_ValidSize() throws Exception {
        var url = mock(URL.class);
        var connection = mock(HttpURLConnection.class);

        when(url.openConnection()).thenReturn(connection);
        when(connection.getContentLengthLong()).thenReturn(1024L); // 1 KB

        statusListResolverAdapter.validateStatusListSize(url);
    }

    @Test
    void testValidateStatusListSize_ExceedsMaxSize() throws Exception {
        var url = mock(URL.class);
        var connection = mock(HttpURLConnection.class);

        when(url.openConnection()).thenReturn(connection);
        when(connection.getContentLengthLong()).thenReturn(10485761L); // 10 MB + 1 byte

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.validateStatusListSize(url));
        assertEquals("Status list size from " + url + " exceeds maximum allowed size", exception.getMessage());
    }

    @Test
    void testValidateStatusListSize_FailedConnection() throws Exception {
        var url = mock(URL.class);

        when(url.openConnection()).thenThrow(new IOException("Connection failed"));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.validateStatusListSize(url));
        assertEquals("Failed to validate status list size from " + url, exception.getMessage());
    }
}