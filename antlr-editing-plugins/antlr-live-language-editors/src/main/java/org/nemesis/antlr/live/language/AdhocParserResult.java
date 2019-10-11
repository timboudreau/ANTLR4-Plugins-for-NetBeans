package org.nemesis.antlr.live.language;

import java.util.function.Consumer;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocParserResult extends Parser.Result {

    private EmbeddedAntlrParserResult embeddedResult;
    private final Consumer<AdhocParserResult> invalidator;

    public AdhocParserResult(Snapshot sn, EmbeddedAntlrParserResult embeddedResult, Consumer<AdhocParserResult> invalidator) {
        super(sn);
        this.embeddedResult = embeddedResult;
        this.invalidator = invalidator;
    }

    public EmbeddedAntlrParserResult result() {
        return embeddedResult;
    }

    public AntlrProxies.ParseTreeProxy parseTree() {
        return embeddedResult.proxy();
    }

    public String grammarHash() {
        return embeddedResult.grammarTokensHash();
    }

    @Override
    protected void invalidate() {
        invalidator.accept(this);
    }
}
