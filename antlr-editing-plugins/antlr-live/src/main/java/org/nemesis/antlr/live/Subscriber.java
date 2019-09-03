package org.nemesis.antlr.live;

import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;

/**
 * A subscriber which can be notified after Antlr code for a project
 * is <i>regenerated</i>.
 *
 * @author Tim Boudreau
 */
public interface Subscriber {

    void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes);
}
