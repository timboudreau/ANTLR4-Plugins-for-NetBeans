package org.nemesis.antlr.project;

import org.openide.util.Lookup;

/**
 * Marker to allow the Maven project support module to know if the Antlr source
 * factory is present - it will hide some source folders from Other Sources if
 * it is, but they should be visible in Other Sources if not.
 * <p>
 * Implemented by the antlr-project-helpers-maven module, which must cooperate
 * with the antlr-project-extensions module, since Maven projects do not
 * tolerate a source group that also shows up under the Other Sources node.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class MavenAntlrSourceFactoryPresent {

    protected abstract boolean isActive();

    /**
     * If true, then hide the antlr folders from Other Sources in Maven
     * projects.
     *
     * @return
     */
    public static boolean isPresent() {
        return Lookup.getDefault()
                .lookupAll(MavenAntlrSourceFactoryPresent.class)
                .stream()
                .anyMatch(p -> (p.isActive()));
    }
}
