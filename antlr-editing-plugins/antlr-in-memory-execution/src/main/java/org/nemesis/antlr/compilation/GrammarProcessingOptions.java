package org.nemesis.antlr.compilation;

import java.util.EnumSet;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public enum GrammarProcessingOptions {
    REGENERATE_GRAMMAR_SOURCES,
    REBUILD_JAVA_SOURCES,
    RETURN_LAST_GOOD_RESULT_ON_FAILURE;

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public static Set<GrammarProcessingOptions> setOf(GrammarProcessingOptions... all) {
        EnumSet<GrammarProcessingOptions> result = EnumSet.noneOf(GrammarProcessingOptions.class);
        for (GrammarProcessingOptions o : all) {
            result.add(o);
        }
        return result;
    }
}
