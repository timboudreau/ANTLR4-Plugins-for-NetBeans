package org.nemesis.antlr.spi.language.highlighting.semantic;

import java.util.function.Function;
import javax.swing.text.AttributeSet;

/**
 * This class is simply to provide a default value for an annotation.
 *
 * @author Tim Boudreau
 */
final class DummyAttributeSetFinder implements Function<Object, AttributeSet> {

    private DummyAttributeSetFinder() {
        throw new AssertionError();
    }

    @Override
    public AttributeSet apply(Object t) {
        throw new AssertionError(DummyAttributeSetFinder.class.getName());
    }
}
