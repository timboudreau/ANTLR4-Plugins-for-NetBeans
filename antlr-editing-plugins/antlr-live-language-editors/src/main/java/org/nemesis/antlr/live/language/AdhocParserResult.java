package org.nemesis.antlr.live.language;

import java.util.function.Consumer;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocParserResult extends Parser.Result {

    private AntlrProxies.ParseTreeProxy proxy;
    private final Consumer<AdhocParserResult> invalidator;

    public AdhocParserResult(Snapshot sn, AntlrProxies.ParseTreeProxy proxy, Consumer<AdhocParserResult> invalidator) {
        super(sn);
        this.proxy = proxy;
        this.invalidator = invalidator;
    }

    public AntlrProxies.ParseTreeProxy parseTree() {
        return proxy;
    }

    @Override
    protected void invalidate() {
//        new Exception("Invalidate " + proxy.grammarName()).printStackTrace();
        invalidator.accept(this);
    }

}
