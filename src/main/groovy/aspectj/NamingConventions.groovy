package aspectj

import org.gradle.api.tasks.SourceSet

/**
 * This simple interface is used to provide task name generation logic-
 */
public interface NamingConventions {

    String getJavaCompileTaskName(SourceSet sourceSet);

    String getAspectCompileTaskName(SourceSet sourceSet);

    String getAspectPathConfigurationName(SourceSet sourceSet);

    String getAspectInpathConfigurationName(SourceSet sourceSet);
}