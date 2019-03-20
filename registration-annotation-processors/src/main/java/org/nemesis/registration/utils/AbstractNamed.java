package org.nemesis.registration.utils;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractNamed implements Named {

    @Override
    public final String toString() {
        return name();
    }
}
