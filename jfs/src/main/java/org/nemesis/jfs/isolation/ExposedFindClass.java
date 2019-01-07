package org.nemesis.jfs.isolation;

/**
 * Interface implemented by classloaders which expose the ability to call
 * findClass() indirectly, to external callers.
 *
 * @author Tim Boudreau
 */
public interface ExposedFindClass {

    /**
     * Delegate to protected findClass().
     *
     * @param name The class name
     * @return A class
     * @throws ClassNotFoundException If no class is found
     */
    public Class<?> lookupClass(String name) throws ClassNotFoundException;
}
