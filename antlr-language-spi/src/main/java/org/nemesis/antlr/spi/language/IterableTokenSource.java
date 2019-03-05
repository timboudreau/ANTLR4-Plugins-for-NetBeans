package org.nemesis.antlr.spi.language;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.TokenSource;

/**
 *
 * @author Tim Boudreau
 */
public interface IterableTokenSource extends TokenSource, Iterable<CommonToken> {

    void dispose();
}
