package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.CompositeValue;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputs.MapValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

public class MapInputTest {

    static MapValue newMapInput(String... values) {
        Map<String, String> members = new LinkedHashMap<>();
        for (String value : values) {
            members.put(value, value);
        }
        return new MapValue(LinkedHashMap::new, members, v -> v);
    }

    @Test
    public void testDigest() throws Exception {
        Digest digest = digest(newMapInput("a", "b"));

        assertEquals(digest, digest(newMapInput("a", "b")));
        assertNotEquals(digest, digest(newMapInput("c", "d")));
    }

    //
    //
    //

    static class _MapData {
        @Parameter(required = false)
        Map<String, String> map;
    }

    static class _IntegerMapData {
        @Parameter(required = false)
        Map<String, Integer> map;
    }

    @Test
    public void testMap() throws Exception {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom listConfiguration = new Xpp3Dom("map");
        configuration.addChild(listConfiguration);
        listConfiguration.addChild(newParameter("key1", "value1"));
        listConfiguration.addChild(newParameter("key2", "value2"));

        MapValue input = builder().withConfiguration(configuration).build(_MapData.class, "map");

        assertEquals("value1", input.configuration.get("key1"));
        assertEquals("value2", input.configuration.get("key2"));
    }

    @Test
    public void testMapWithIntegerValue() throws Exception {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom listConfiguration = new Xpp3Dom("map");
        configuration.addChild(listConfiguration);
        listConfiguration.addChild(newParameter("key1", "1"));
        listConfiguration.addChild(newParameter("key2", "2"));

        MapValue input = builder().withConfiguration(configuration).build(_IntegerMapData.class, "map");

        assertEquals(1, input.value().get("key1"));
        assertEquals(2, input.value().get("key2"));
    }

    @Test
    public void testMapWithOmittedOptionalValue() throws Exception {
        assertNull(builder().build(_MapData.class, "map"));
    }

    //
    //
    //

    static class _MapWrapper {
        Map<String, String> map;
    }

    static class _MapWrapperData {
        @Parameter
        _MapWrapper custom;
    }

    @Test
    public void testMapWrapper() throws Exception {
        CompositeValue composite = builder() //
                .withConfigurationXml("<custom><map><key>value</key></map></custom>") //
                .build(_MapWrapperData.class, "custom");
        _MapWrapper value = (_MapWrapper) composite.value();
        assertEquals("value", value.map.get("key"));
    }
}
