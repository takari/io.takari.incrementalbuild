package io.takari.builder.internal.maven;

import static org.junit.Assert.assertEquals;

import io.takari.builder.internal.ExpressionEvaluationException;
import io.takari.builder.internal.ExpressionEvaluator;
import io.takari.maven.testing.TestMavenRuntime;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

// TODO separate MavenProjectExpressionResolver tests
public class ExpressionEvaluatorTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final TestMavenRuntime maven = new TestMavenRuntime();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private MavenProject project;

    @Before
    public void setup() throws Exception {
        File basedir = temp.newFolder();
        File file = new File(basedir, "1.txt");
        file.createNewFile();

        project = maven.readMavenProject(basedir);
        project.setArtifactId("artifact1");
        project.setGroupId("group1");
        project.setVersion("1.0");
    }

    private ExpressionEvaluator newEvaluator() {
        return newEvaluator(new Properties());
    }

    private ExpressionEvaluator newEvaluator(Properties properties) {
        List<Function<String, String>> resolvers = new ArrayList<>();
        resolvers.add(s -> properties.getProperty(s));
        resolvers.add(new MavenProjectPropertyResolver(project));
        return new ExpressionEvaluator(resolvers);
    }

    @Test
    public void testProjectExpressionAtStart() throws Exception {
        String expression = "${project.build.directory}";
        ExpressionEvaluator evaluator = newEvaluator();
        Object evaluatedExpression = evaluator.evaluate(expression);
        Object expectedResult = project.getBuild().getDirectory();

        assertEquals(expectedResult, evaluatedExpression);
    }

    @Test
    public void testProjectExpressionInMiddle() throws Exception {
        String expression = "/sometext/${project.build.directory}/somemore.txt";
        ExpressionEvaluator evaluator = newEvaluator();
        Object evaluatedExpression = evaluator.evaluate(expression);
        Object expectedResult = "/sometext/" + project.getBuild().getDirectory() + "/somemore.txt";

        assertEquals(expectedResult, evaluatedExpression);
    }

    @Test
    public void testProjectExpressionAtEnd() throws Exception {
        String expression = "/sometext/${project.build.directory}";
        ExpressionEvaluator evaluator = newEvaluator();
        Object evaluatedExpression = evaluator.evaluate(expression);
        Object expectedResult = "/sometext/" + project.getBuild().getDirectory();

        assertEquals(expectedResult, evaluatedExpression);
    }

    @Test()
    public void testInvalidExpression() throws Exception {
        String expression = "${settings.localRepository}";
        ExpressionEvaluator evaluator = newEvaluator();

        expectedEx.expect(ExpressionEvaluationException.class);
        expectedEx.expectMessage(expression);

        evaluator.evaluate(expression);
    }

    @Test
    public void testUserOrSystemPropertiesExpression() throws Exception {
        String propertyName = "my.user.property";
        String expression = String.format("${%s}", propertyName);
        Properties properties = new Properties();
        Object propertyValue = "Something";
        properties.put(propertyName, propertyValue);
        ExpressionEvaluator evaluator = newEvaluator(properties);
        Object evaluatedExpression = evaluator.evaluate(expression);
        Object expectedResult = propertyValue;

        assertEquals(expectedResult, evaluatedExpression);
    }

    @Test
    public void testProjectPropertiesExpression() throws Exception {
        String propertyName = "my.project.property";
        String expression = String.format("${%s}", propertyName);
        Object propertyValue = "Something";
        project.getProperties().put(propertyName, propertyValue);
        ExpressionEvaluator evaluator = newEvaluator();
        Object evaluatedExpression = evaluator.evaluate(expression);
        Object expectedResult = propertyValue;

        assertEquals(expectedResult, evaluatedExpression);
    }

    @Test
    public void testArtifactId() throws Exception {
        verifySimpleExpression("${project.artifactId}", project.getArtifactId());
    }

    @Test
    public void testBasedir() throws Exception {
        verifySimpleExpression("${project.basedir}", project.getBasedir().getAbsolutePath());
    }

    @Test
    public void testGroupId() throws Exception {
        verifySimpleExpression("${project.groupId}", project.getGroupId());
    }

    @Test
    public void testBuildDirectory() throws Exception {
        verifySimpleExpression("${project.build.directory}", project.getBuild().getDirectory());
    }

    @Test
    public void testOutputDirectory() throws Exception {
        verifySimpleExpression(
                "${project.build.outputDirectory}", project.getBuild().getOutputDirectory());
    }

    @Test
    public void testTestOutputDirectory() throws Exception {
        verifySimpleExpression(
                "${project.build.testOutputDirectory}", project.getBuild().getTestOutputDirectory());
    }

    @Test
    public void testVersion() throws Exception {
        verifySimpleExpression("${project.version}", project.getVersion());
    }

    @Test
    public void test$$escape() throws Exception {
        verifySimpleExpression("$${project.version}", "${project.version}");
        verifySimpleExpression("$$${project.version}", "$" + project.getVersion());
    }

    @Test
    public void testNoExpression() throws Exception {
        verifySimpleExpression("", "");
        verifySimpleExpression("t", "t");
        verifySimpleExpression("text", "text");
        verifySimpleExpression("$", "$");
        verifySimpleExpression("${", "${");
        verifySimpleExpression("${project.version", "${project.version");
    }

    @Test
    public void testEmptyExpression() throws Exception {
        expectedEx.expect(ExpressionEvaluationException.class);
        expectedEx.expectMessage("${}");
        newEvaluator().evaluate("${}");
    }

    private void verifySimpleExpression(String expression, Object expectedResult) throws Exception {
        ExpressionEvaluator evaluator = newEvaluator();
        Object evaluatedExpression = evaluator.evaluate(expression);

        assertEquals(expectedResult, evaluatedExpression);
    }
}
