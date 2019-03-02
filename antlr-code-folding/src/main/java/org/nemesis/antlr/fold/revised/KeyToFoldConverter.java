package org.nemesis.antlr.fold.revised;

import java.util.function.Function;
import org.nemesis.data.IndexAddressable;
import org.netbeans.spi.editor.fold.FoldInfo;

/**
 *
 * @author Tim Boudreau
 */
public interface KeyToFoldConverter<I extends IndexAddressable.IndexAddressableItem> extends Function<I,FoldInfo> {

}
