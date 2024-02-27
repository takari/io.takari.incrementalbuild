package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderRunner.getBuilderMethodForGoal;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import io.takari.builder.Builder;
import io.takari.builder.LifecyclePhase;
import io.takari.builder.testing.BuilderExecutionException;
import java.lang.reflect.Method;
import org.junit.Test;

public class BuilderMethodTest {

    private static final String EXT_BASE_MOJO = "extbasemojo";
    private static final String METHOD_NAME = "myMethod";

    private static class Base {}

    private static class ExtendedBase extends Base {

        @Builder(name = EXT_BASE_MOJO, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
        public void myMethod() {}
    }

    @Test
    public void testFindBuilderMethod() throws Exception {
        Method actualMethod =
                getBuilderMethodForGoal(ExtendedBase.class, EXT_BASE_MOJO, BuilderExecutionException::new);
        Method expectedMethod = ExtendedBase.class.getMethod(METHOD_NAME);

        assertEquals(String.format("Expected to find %s builder method", METHOD_NAME), expectedMethod, actualMethod);
    }

    @Test(expected = BuilderExecutionException.class)
    public void testNoBuilderMethodDeclared() throws Exception {
        getBuilderMethodForGoal(Base.class, EXT_BASE_MOJO, BuilderExecutionException::new);
    }

    //
    //
    //

    private static class InaccessibleBuilder {
        public int count;

        @Builder(name = "inaccessible")
        private void execution() {
            count++;
        }
    }

    @Test
    public void testInaccessibleType() throws Exception {
        Class<?> builderType = InaccessibleBuilder.class;
        BuilderInputs inputs = builder().build(builderType);
        Method method = getBuilderMethodForGoal(builderType, "inaccessible", BuilderExecutionException::new);
        InaccessibleBuilder instance = (InaccessibleBuilder) inputs.newBuilder();
        method.invoke(instance);
        assertEquals(1, instance.count);
    }
}
