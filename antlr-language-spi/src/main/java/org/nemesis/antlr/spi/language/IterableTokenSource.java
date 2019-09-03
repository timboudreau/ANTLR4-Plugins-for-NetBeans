package org.nemesis.antlr.spi.language;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.TokenSource;

/**
 * Implementation of TokenSource which also implements Iterable to allow
 * replaying the token sequence.
 *
 * @author Tim Boudreau
 */
public interface IterableTokenSource extends TokenSource, Iterable<CommonToken> {

    /**
     * Dispose the cached token sequence if it is not going to be used again.
     */
    void dispose();
}
