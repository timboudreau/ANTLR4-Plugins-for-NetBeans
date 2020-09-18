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

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;

/**
 * Convenience subclass for the common case where hint generation does not add
 * any highlights.
 *
 * @author Tim Boudreau
 */
public abstract class NonHighlightingHintGenerator extends AntlrHintGenerator {

    @Override
    protected final boolean generate(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, HighlightConsumer highlights) throws BadLocationException {
        generate(tree, extraction, res, populate, fixes, doc, positions);
        return false;
    }

    protected abstract void generate(ANTLRv4Parser.GrammarFileContext tree, 
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions) throws BadLocationException;

}
