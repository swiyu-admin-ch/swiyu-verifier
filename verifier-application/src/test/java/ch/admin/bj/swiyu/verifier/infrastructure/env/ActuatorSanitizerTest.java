package ch.admin.bj.swiyu.verifier.infrastructure.env;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ActuatorSanitizerTest {

    @Test
    void testActuatorSanitizer() {
        var whitelist = List.of("foo", "password");
        var sanitizer = new ActuatorSanitizer(whitelist);

        var value = "placeholder";

        var data = new SanitizableData(null, "bar", value);
        assertEquals(value, data.getValue());

        // sanitize values not on whitelist
        var sanitizedData = sanitizer.apply(data);
        assertNotEquals(value, sanitizedData.getValue());

        // ignore values on whitelist
        data = new SanitizableData(null, "foo", value);
        sanitizedData = sanitizer.apply(data);
        assertEquals(value, sanitizedData.getValue());

        // ignore passwords despite whitelist
        data = new SanitizableData(null, "password", value);
        sanitizedData = sanitizer.apply(data);
        assertNotEquals(value, sanitizedData.getValue());
    }

    @Test
    void testActuatorSanitizer_Regex() {
        // allow everything
        var whitelist = List.of(".*");
        var sanitizer = new ActuatorSanitizer(whitelist);

        var value = "placeholder";

        var data = new SanitizableData(null, "bar", value);
        assertEquals(value, data.getValue());

        // sanitize values not on whitelist
        var sanitizedData = sanitizer.apply(data);
        assertEquals(value, sanitizedData.getValue());

        // ignore passwords despite whitelist
        data = new SanitizableData(null, "password", value);
        sanitizedData = sanitizer.apply(data);
        assertNotEquals(value, sanitizedData.getValue());

        // ignore tokens despite whitelist
        data = new SanitizableData(null, "foo.token", value);
        sanitizedData = sanitizer.apply(data);
        assertNotEquals(value, sanitizedData.getValue());

        // ignore secrets despite whitelist
        data = new SanitizableData(null, "bar.foo.secret", value);
        sanitizedData = sanitizer.apply(data);
        assertNotEquals(value, sanitizedData.getValue());
    }
}
