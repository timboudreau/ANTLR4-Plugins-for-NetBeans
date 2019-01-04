package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

/**
 * Yet another ProxyLookup subclass which exposes setLookups().
 *
 * @author Tim Boudreau
 */
final class MutableProxyLookup extends ProxyLookup {

    private final Lookup base;

    MutableProxyLookup(Lookup base) {
        super(base);
        this.base = base;
    }

    public void setAdditional(Lookup lkp) {
        setLookups(base, lkp);
    }
}
