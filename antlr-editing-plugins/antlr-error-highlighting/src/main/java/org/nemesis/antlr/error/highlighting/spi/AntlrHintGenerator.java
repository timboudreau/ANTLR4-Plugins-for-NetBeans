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
package org.nemesis.antlr.error.highlighting.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AntlrHintGenerator {

    @SuppressWarnings("NonConstantLogger")
    protected final Logger LOG = Logger.getLogger(getClass().getName());

    static List<AntlrHintGenerator> all;

    public static List<AntlrHintGenerator> all() {
        if (all == null) {
            List<AntlrHintGenerator> result = new ArrayList<>();
            result.addAll(Lookup.getDefault().lookupAll(AntlrHintGenerator.class));
            all = result;
        }
        return all;
    }

    protected AntlrHintGenerator() {
    }

    protected final boolean isEnabled() {
        return NbPreferences.forModule(getClass()).getBoolean("enable-" + getClass().getSimpleName(), true);
    }

    public boolean generateHints(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, OffsetsBag highlights) throws BadLocationException {
        if (isEnabled()) {
            return generate(tree, extraction, res, populate, fixes, doc, positions, highlights);
        }
        return false;
    }

    protected abstract boolean generate(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, OffsetsBag highlights) throws BadLocationException;
}
