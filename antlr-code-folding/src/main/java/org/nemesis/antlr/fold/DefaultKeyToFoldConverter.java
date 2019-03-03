package org.nemesis.antlr.fold;

import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.netbeans.spi.editor.fold.FoldInfo;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultKeyToFoldConverter implements KeyToFoldConverter<IndexAddressableItem> {

    private DefaultKeyToFoldConverter() {
        throw new AssertionError("Should never be instantiated");
    }

    @Override
    public FoldInfo apply(IndexAddressableItem t) {
        throw new AssertionError();
    }

}
