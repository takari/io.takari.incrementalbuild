package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.StringValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BasicInputTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    //
    // private field parameter
    //

    static class _PrivateParameterData {
        @Parameter(defaultValue = "default-value")
        private String parameter;
    }

    @Test
    public void testPrivateParameter() throws Exception {
        StringValue input = builder().build(_PrivateParameterData.class, "parameter");

        assertEquals("default-value", input.configuration);
    }

    //
    // read-only parameter
    //

    static class _ReadonlyParameterData {
        @Parameter("explicit-value")
        public String parameter;
    }

    @Test
    public void testReadonlyParameter() throws Exception {
        thrown.expect(InvalidConfigurationException.class);

        builder() //
                .withConfigurationXml("<parameter>readonly-value</parameter>") //
                .build(_ReadonlyParameterData.class, "parameter");
    }

    //
    // optional parameter
    //

    static class _OptionalParameterData {
        @Parameter(required = false)
        public String parameter = "";
    }

    @Test
    public void testOptionalParameter() throws Exception {
        assertNull(builder().build(_OptionalParameterData.class, "parameter"));
    }
}
