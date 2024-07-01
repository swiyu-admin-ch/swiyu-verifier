package ch.admin.bit.eid.verifier_management.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.Setter;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;

/**
 * A logstash/logback JSON field provider that writes a configured, fixed field value to the output
 */
@Setter
@Getter
public class FieldValueJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private String value;

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent iLoggingEvent) throws IOException {
        JsonWritingUtils.writeStringField(generator, getFieldName(), value);
    }

}