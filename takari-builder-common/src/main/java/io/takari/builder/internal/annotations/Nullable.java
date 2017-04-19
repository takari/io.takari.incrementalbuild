package io.takari.builder.internal.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maven does not export {@code org.eclipse.sisu.Nullable} annotation from the core classrealm, but
 * Sisu "compares the name of the annotation to 'Nullable', regardless of the package", so we can
 * use this custom annotation instead.
 * 
 * https://dev.eclipse.org/mhonarc/lists/sisu-users/msg00118.html
 */
@Target(value = {ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public
@interface Nullable {}
