package org.jetbrains.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 1.18.2 imports a handful of IntelliJ annotations. They carry no runtime behaviour,
 * so the port declares them here rather than putting the annotations jar (and its
 * transitive baggage) on the TeaVM classpath.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE,
		ElementType.TYPE_USE, ElementType.RECORD_COMPONENT })
public @interface Unmodifiable {
}
