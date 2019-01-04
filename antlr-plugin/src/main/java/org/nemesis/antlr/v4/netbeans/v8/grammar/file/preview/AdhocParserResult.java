package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocParserResult extends Parser.Result {

    private final AntlrProxies.ParseTreeProxy proxy;
    private final GenerateBuildAndRunGrammarResult buildResult;

    public AdhocParserResult(Snapshot sn, AntlrProxies.ParseTreeProxy proxy, GenerateBuildAndRunGrammarResult buildResult) {
        super(sn);
        this.proxy = proxy;
        this.buildResult = buildResult;
    }

    public GenerateBuildAndRunGrammarResult buildResult() {
        return buildResult;
    }

    public AntlrProxies.ParseTreeProxy parseTree() {
        return proxy;
    }

    @Override
    protected void invalidate() {
    }

}
