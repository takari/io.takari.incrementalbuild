package io.takari.builder.enforcer.modularity;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathNormalizer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

public class MavenProjectContextTest {

    @Test
    public void testStore() throws Exception {
        PathNormalizer normalizer = PathNormalizer.createNormalizer("/locations");
        PathMatcher readMatcher = PathMatcher.builder() //
                .includePrefix("/read/includes") //
                .includePath("/readIncludes") //
                .includePath("/.hidden") //
                .excludePrefix("/read/excludes") //
                .excludePath("/read/excludes") //
                .build();
        PathMatcher writeMatcher = PathMatcher.builder() //
                .includePrefix("/write/includes") //
                .excludePrefix("/write/excludes") //
                .build();
        Set<String> execIncludes = ImmutableSet.of("p4");

        ProjectContext ctx = new ProjectContext(normalizer, "id", readMatcher, writeMatcher, execIncludes);

        assertProjextContext(
                ctx, //
                "+E p4" //
                ,
                "+R /.hidden" //
                ,
                "+R /read/includes/" //
                ,
                "+R /readIncludes" //
                ,
                "+W /write/includes/" //
                ,
                "-R /read/excludes" //
                ,
                "-R /read/excludes/" //
                ,
                "-W /write/excludes/" //
                ,
                "/locations" // was the first line...
                );
    }

    private void assertProjextContext(ProjectContext ctx, String... expected) throws IOException {
        // dump to a buffer
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ctx.store(buf);

        // render as sorted string list
        List<String> actual = Splitter.on(Pattern.compile("\r?\n"))
                .omitEmptyStrings()
                .splitToList(new String(buf.toByteArray(), Charsets.UTF_8));
        actual = new ArrayList<>(actual);
        Collections.sort(actual);

        // assert matches expected (use multi-line string for better diff view in eclipse)
        Assert.assertEquals(Joiner.on("\n").join(expected), Joiner.on("\n").join(actual));
    }

    @Test
    public void testLoad() throws Exception {
        StringBuilder input = new StringBuilder();
        // basedir
        input.append("/locations").append("\n");
        // read/write directory
        input.append("+R /locations/dirAllow/").append("\n");
        input.append("-R /locations/dirForbid/").append("\n");
        // read/write file
        input.append("+W /locations/fileAllow").append("\n");
        input.append("-W /locations/fileForbid").append("\n");
        // execute
        input.append("+E p4").append("\n");

        ProjectContext ctx =
                ProjectContext.load(new ByteArrayInputStream(input.toString().getBytes(StandardCharsets.UTF_8)));

        assertProjextContext(
                ctx, //
                "+E p4" //
                ,
                "+R /locations/dirAllow/" //
                ,
                "+W /locations/fileAllow" //
                ,
                "-R /locations/dirForbid/" //
                ,
                "-W /locations/fileForbid" //
                ,
                "/locations" //
                );
    }

    @Test
    public void testExecPath() throws Exception {
        PathNormalizer normalizer = PathNormalizer.createNormalizer(Paths.get("/locations/"));
        PathMatcher readMatcher = PathMatcher.builder(normalizer).build();
        PathMatcher writeMatcher = PathMatcher.builder(normalizer).build();
        Set<String> execIncludes = ImmutableSet.of("p4");

        ProjectContext ctx = new ProjectContext(normalizer, "id", readMatcher, writeMatcher, execIncludes);

        Assert.assertTrue(ctx.checkExecute("p4"));
        Assert.assertTrue(ctx.checkExecute("/usr/local/bin/p4"));
    }
}
