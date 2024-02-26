package io.takari.builder.internal;

import io.takari.builder.Messages;
import java.io.File;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tempporary class to allow messages to pass through to logger
 *
 * @author jaime.morales
 * @since 138
 */
class PassThroughMessages implements Messages {
    static final Logger log = LoggerFactory.getLogger(PassThroughMessages.class);

    @Override
    public void info(File resource, int line, int column, String message, Throwable cause) {
        log.info("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }

    @Override
    public void warn(File resource, int line, int column, String message, Throwable cause) {
        log.warn("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }

    @Override
    public void error(File resource, int line, int column, String message, Throwable cause) {
        log.error("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }

    @Override
    public void info(Path resource, int line, int column, String message, Throwable cause) {
        log.info("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }

    @Override
    public void warn(Path resource, int line, int column, String message, Throwable cause) {
        log.warn("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }

    @Override
    public void error(Path resource, int line, int column, String message, Throwable cause) {
        log.error("{}:[{},{}] {}", resource.toString(), line, column, message, cause);
    }
}
