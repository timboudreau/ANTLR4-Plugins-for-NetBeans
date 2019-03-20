package org.nemesis.registration.utils;

/**
 *
 * @author Tim Boudreau
 */
public interface Named {

    String name();

    static String findName(Object o) {
        if (o instanceof Named) {
            return ((Named) o).name();
        }
        if (o instanceof Wrapper<?>) {
            Named n = ((Wrapper<?>) ((Wrapper<?>) o)).find(Named.class);
            if (n != null) {
                return n.name();
            }
        }
        return o == null ? "null" : o.toString();
    }
}
