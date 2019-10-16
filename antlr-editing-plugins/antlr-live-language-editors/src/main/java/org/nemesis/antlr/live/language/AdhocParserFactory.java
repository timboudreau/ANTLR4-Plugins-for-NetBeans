/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.live.language;

import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.debug.api.Trackables;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocParserFactory extends ParserFactory implements BiConsumer<Extraction, GrammarRunResult<?>> {

    private final Set<AdhocParser> liveParsers = new WeakSet<>();
    private static final Logger LOG = Logger.getLogger(AdhocParserFactory.class.getName());
    private final String mimeType;
//    private final EmbeddedAntlrParser embeddedParser;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocParserFactory(String mimeType) {
        this.mimeType = mimeType;
//        Path grammarFilePath = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
//        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(grammarFilePath.toFile()));
//        embeddedParser = EmbeddedAntlrParsers.forGrammar("parser-factory:" + AdhocMimeTypes.loggableMimeType(mimeType), fo);
//        embeddedParser.listen(this);
        AdhocLanguageHierarchy.parserFor(mimeType).listen(this);
    }

    private boolean updating;
    private int idAtLastUpdate = -1;

    void updated() {
        if (updating) {
            return;
        }
        try {
            updating = true;
            LOG.log(Level.FINE, "Received update notification from embedded parser "
                    + "{0} with {1} live parsers for underlying grammar change",
                    new Object[]{AdhocLanguageHierarchy.parserFor(mimeType), liveParsers.size()});
            for (AdhocParser p : liveParsers) {
                p.updated();
            }
            DynamicLanguages.mimeData().updateMimeType(mimeType);
        } finally {
            updating = false;
        }
    }

    @Override
    public Parser createParser(Collection<Snapshot> clctn) {
        if (!clctn.isEmpty()) {
            Snapshot snap = clctn.iterator().next();
            String m = snap.getMimeType();
            if (!mimeType.equals(m)) {
                LOG.log(Level.SEVERE, "AdhocParserFactory for " + mimeType + " asked to create a parser for " + m,
                        new Exception("AdhocParserFactory for " + mimeType + " asked to create a parser for " + m));
            }
        }
        AdhocParser parser = new AdhocParser(mimeType);
        Trackables.track(AdhocParser.class, parser, () -> {
            return "Parser-" + AdhocMimeTypes.loggableMimeType(mimeType);
        });
        liveParsers.add(parser);
        LOG.log(Level.FINER, "Created a parser {0} over {1} grammar {2}",
                new Object[]{parser, clctn, mimeType});
        return parser;
    }

    @Override
    public void accept(Extraction ext, GrammarRunResult<?> res) {
        updated();
    }
}
