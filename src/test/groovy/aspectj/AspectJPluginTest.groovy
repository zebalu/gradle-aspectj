package aspectj;

import static org.junit.Assert.*

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Test

class AspectJPluginTest extends GroovyTestCase {

	@Test
	public void testExampleHasGoodDay() {
		File testProjectFolder = new File(this.class.getResource('/project1/README').toURI()).parentFile
		BuildResult result = GradleRunner.create().withProjectDir(testProjectFolder).withPluginClasspath().forwardOutput().withArguments('clean', 'build', 'run').build();
		assert result.output.contains('Good day!')
	}
}
