package aspectj

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

/**
 * Very simple Aspectj compiler for gradle.
 * @author Luke Taylor
 * @author Mike Noordermeer
 * @author Balázs Zaicsek
 */
class AspectJPlugin implements Plugin<Project> {

	/**
	 * Adds 'ajtools' configuration and 'org.aspectj:[aspectjtools/aspectjrt]' dependency to project and checks for AspectJ version
	 */
	void apply(Project project) {
		project.plugins.apply(JavaPlugin)

		def aspectj = project.extensions.create('aspectj', AspectJExtension, project)

		if (project.configurations.findByName('ajtools') == null) {
			project.configurations.create('ajtools')
			project.afterEvaluate { p ->
				if (aspectj.version == null) {
					throw new GradleException("No aspectj version supplied")
				} else {
					p.logger.info "AspectJ version: ${aspectj.version} will be used"
				}

				p.dependencies {
					ajtools "org.aspectj:aspectjtools:${aspectj.version}"
					compile "org.aspectj:aspectjrt:${aspectj.version}"
				}
			}
		}

		for (projectSourceSet in project.sourceSets) {
			def namingConventions = projectSourceSet.name.equals('main') ? new MainNamingConventions() : new DefaultNamingConventions();
			for (configuration in [
				namingConventions.getAspectPathConfigurationName(projectSourceSet),
				namingConventions.getAspectInpathConfigurationName(projectSourceSet)
			]) {
				if (project.configurations.findByName(configuration) == null) {
					project.configurations.create(configuration)
				}
			}

			if (!projectSourceSet.allJava.isEmpty()) {
				def aspectTaskName = namingConventions.getAspectCompileTaskName(projectSourceSet)
				def javaTaskName = namingConventions.getJavaCompileTaskName(projectSourceSet)

				project.tasks.create(name: aspectTaskName, overwrite: true, description: "Compiles AspectJ Source for ${projectSourceSet.name} source set", type: Ajc) {
					sourceSet = projectSourceSet.name
					inputs.files(projectSourceSet.allJava)
					outputs.dir(projectSourceSet.java.outputDir)
					aspectpath = project.configurations.findByName(namingConventions.getAspectPathConfigurationName(projectSourceSet))
					ajInpath = project.configurations.findByName(namingConventions.getAspectInpathConfigurationName(projectSourceSet))
				}

				project.tasks[aspectTaskName].setDependsOn(project.tasks[javaTaskName].dependsOn)
				project.tasks[aspectTaskName].dependsOn(project.tasks[aspectTaskName].aspectpath)
				project.tasks[aspectTaskName].dependsOn(project.tasks[aspectTaskName].ajInpath)
				project.tasks[aspectTaskName].dependsOn(project.tasks[javaTaskName].classpath)
				project.tasks[javaTaskName].actions=[]
				project.tasks[javaTaskName].dependsOn(project.tasks[aspectTaskName])
			}
		}
	}

	private static class MainNamingConventions implements NamingConventions {

		@Override
		String getJavaCompileTaskName(final SourceSet sourceSet) {
			return "compileJava"
		}

		@Override
		String getAspectCompileTaskName(final SourceSet sourceSet) {
			return "compileAspect"
		}

		@Override
		String getAspectPathConfigurationName(final SourceSet sourceSet) {
			return "aspectpath"
		}

		@Override
		String getAspectInpathConfigurationName(final SourceSet sourceSet) {
			return "ajInpath"
		}
	}

	private static class DefaultNamingConventions implements NamingConventions {

		@Override
		String getJavaCompileTaskName(final SourceSet sourceSet) {
			return "compile${sourceSet.name.capitalize()}Java"
		}

		@Override
		String getAspectCompileTaskName(final SourceSet sourceSet) {
			return "compile${sourceSet.name.capitalize()}Aspect"
		}

		@Override
		String getAspectPathConfigurationName(final SourceSet sourceSet) {
			return "${sourceSet.name}Aspectpath"
		}

		@Override
		String getAspectInpathConfigurationName(final SourceSet sourceSet) {
			return "${sourceSet.name}AjInpath"
		}
	}
}

/**
 * The definition of the AspectJTask
 */
class Ajc extends DefaultTask {
	
	@Input
	String sourceSet
	
	private SourceSet sourceSetResolved

	@InputFiles
	FileCollection aspectpath
	@InputFiles
	FileCollection ajInpath

	// ignore or warning
	@Input
	String xlint = 'ignore'

	@Input
	String maxmem = ""
	@Input
	Map<String, String> additionalAjcArgs = [:]
	@Input
	List<String> additionalCompilerArgs = []

	Ajc() {
		logging.captureStandardOutput(LogLevel.INFO)
	}

	@TaskAction
	def compile() {
		logger.info("=" * 30)
		logger.info("=" * 30)
		logger.info("Running ajc ...")
		logger.info("classpath: ${sourceSetResolved.compileClasspath.asPath}")
		logger.info("srcDirs $sourceSetResolved.java.srcDirs")

		def iajcArgs = [classpath           : sourceSetResolved.compileClasspath.asPath,
			destDir             : sourceSetResolved.java.outputDir.absolutePath,
			s                   : sourceSetResolved.java.outputDir.absolutePath,
			source              : project.convention.plugins.java.sourceCompatibility,
			target              : project.convention.plugins.java.targetCompatibility,
			inpath              : ajInpath.asPath,
			xlint               : xlint,
			fork                : 'true',
			aspectPath          : aspectpath.asPath,
			sourceRootCopyFilter: '**/*.java,**/*.aj',
			showWeaveInfo       : 'true']

		if (maxmem) {
			iajcArgs['maxmem'] = maxmem
		}

		if (additionalAjcArgs) {
			for (pair in additionalAjcArgs) {
				iajcArgs[pair.key] = pair.value
			}
		}

		ant.taskdef(resource: "org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties", classpath: project.configurations.ajtools.asPath)
		ant.iajc(iajcArgs) {
			sourceRoots {
				SourceSet ss = project.sourceSets.find {it.name == sourceSet}
				if(!ss) throw new GradleException("Can not compile source set: ${sourceSet} since it is not found.")
				ss.java.srcDirs.each {
					logger.info("   sourceRoot $it")
					pathelement(location: it.absolutePath)
				}
			}
			if (null != additionalCompilerArgs) {
				for (arg in additionalCompilerArgs) {
					logger.info("   compilerArg $arg")
					compilerArg(value: arg)
				}
			}
		}
	}
	
	void setSourceSet(String name) {
		this.sourceSet=name;
		this.sourceSetResolved=project.sourceSets.find { it.name == name }
		if(!sourceSetResolved) {
			throw new GradleException("Can not find source set with name: ${name}")
		}
	}
	
	void setSourceSet(SourceSet sourceSet) {
		setSourceSet(sourceSet.name)
	}
}

/**
 * The plugin expect an 'aspectjVersion' property to be set. It defaults to 1.9.3.
 */
class AspectJExtension {

	@Input
	String version

	AspectJExtension(Project project) {
		this.version = project.findProperty('aspectjVersion') ?: '1.9.3'
	}
}
