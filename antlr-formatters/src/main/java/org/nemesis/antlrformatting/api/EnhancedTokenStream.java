package org.nemesis.antlrformatting.api;

import org.antlr.v4.runtime.TokenStream;

/**
 * Extension to TokenStream which implements Iterable and returns ModalToken -
 * used by the tooling for writing tests of formatters.
 *
 * @author Tim Boudreau
 */
public interface EnhancedTokenStream extends Iterable<ModalToken>, TokenStream {

    ModalToken LT(int k);

    ModalToken get(int index);
}
