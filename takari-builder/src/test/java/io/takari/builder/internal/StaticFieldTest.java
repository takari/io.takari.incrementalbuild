package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.CompositeValue;
import org.junit.Test;

public class StaticFieldTest {

    static class _Complex {
        static String staticField;
        String value;
    }

    static class _Data {
        @Parameter
        _Complex parameter;
    }

    @Test
    public void testStaticField() throws Exception {
        CompositeValue input = builder() //
                .withConfigurationXml("<parameter><value>value</value></parameter>") //
                .build(_Data.class, "parameter");
        assertEquals(1, input.configuration.size());
        assertEquals("value", input.configuration.keySet().iterator().next().getName());
    }
}
