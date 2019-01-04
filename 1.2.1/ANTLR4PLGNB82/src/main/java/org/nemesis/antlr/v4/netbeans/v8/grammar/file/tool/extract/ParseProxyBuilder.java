package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.io.IOException;
import java.util.function.Consumer;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public interface ParseProxyBuilder {

    GenerateBuildAndRunGrammarResult buildNoParse() throws IOException;

    GenerateBuildAndRunGrammarResult parse(String text) throws IOException;

    @NbBundle.Messages(value = {"# {0} - grammar file ", "GENERATING_ANTLR_SOURCES=Running antlr on {0}", "# {0} - grammar file ", "COMPILING_ANTLR_SOURCES=Compiling generated sources for {0}", "# {0} - grammar file ", "EXTRACTING_PARSE=Extracting parse for {0}", "CANCELLED=Cancelled."})
    GenerateBuildAndRunGrammarResult parse(String text, Consumer<String> status) throws IOException;

    ParseProxyBuilder regenerateAntlrCodeOnNextCall();

}
