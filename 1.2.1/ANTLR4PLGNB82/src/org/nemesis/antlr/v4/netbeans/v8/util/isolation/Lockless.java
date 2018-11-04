package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Marker annotations for class loaders which can be wrapped by
 * IsolationClassLoader, and pre-create all classes so they do not
 * need a classloading lock.
 *
 * @author Tim Boudreau
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Lockless {

}
