package org.nemesis.antlr.spi.language.highlighting.semantic;

import java.util.function.Function;

/**
 * This class is simply to provide a default value for an annotation.
 *
 * @author Tim Boudreau
 */
final class DummyKeyFinder implements Function<Object, String> {

    private DummyKeyFinder() {
        throw new AssertionError();
    }

    @Override
    public String apply(Object t) {
        throw new AssertionError(DummyKeyFinder.class.getName());
    }

}
