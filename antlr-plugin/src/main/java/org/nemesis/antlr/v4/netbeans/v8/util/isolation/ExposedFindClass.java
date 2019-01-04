package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

/**
 *
 * @author Tim Boudreau
 */
public interface ExposedFindClass {
    public Class<?> lookupClass(String name) throws ClassNotFoundException;
}
