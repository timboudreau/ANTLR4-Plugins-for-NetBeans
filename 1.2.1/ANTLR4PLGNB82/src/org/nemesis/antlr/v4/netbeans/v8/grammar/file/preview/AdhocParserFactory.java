package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Collection;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
class AdhocParserFactory extends ParserFactory {

    private final AntlrProxies.ParseTreeProxy proxy;
    private final Set<AdhocParser> liveParsers = new WeakSet<>();
    private final GenerateBuildAndRunGrammarResult buildResult;

    AdhocParserFactory(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy proxy) {
        this.proxy = proxy;
        this.buildResult = buildResult;
    }

    void updated(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy proxy) {
        for (AdhocParser p : liveParsers) {
            p.updated(buildResult, proxy);
        }
    }

    @Override
    public Parser createParser(Collection<Snapshot> clctn) {
        AdhocParser result = new AdhocParser(buildResult, proxy);
        liveParsers.add(result);
        return result;
    }

    public AdhocParser newParser() {
        return new AdhocParser(buildResult, proxy);
    }

}
