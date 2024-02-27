package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import io.takari.builder.Parameter;
import org.junit.Test;

public class EnumParameterTest {

    static enum _Enum {
        VALUE,
        VALUE2
    }

    static class _Data {

        @Parameter(defaultValue = "VALUE")
        public _Enum parameter;
    }

    @Test
    public void testDefaultValue() throws Exception {
        assertEquals(_Enum.VALUE, builder().build(_Data.class, "parameter").value());
    }
}
