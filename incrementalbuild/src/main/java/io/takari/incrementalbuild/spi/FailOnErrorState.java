package io.takari.incrementalbuild.spi;

/***
 *  The purpose is to indicate if a MojoException must be throw after the compilation if errors are present
 *  NONE means no preference when errors are present, this is the default value
 *  TRUE means stop the build if errors are present
 *  FALSE means continue the build if errors are present
 */
public enum FailOnErrorState {

    NONE, TRUE, FALSE
}
