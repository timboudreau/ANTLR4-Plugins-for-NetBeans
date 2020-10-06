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
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

/**
 * SPI for hint generators for Antlr files.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrHintGenerator {

    @SuppressWarnings("NonConstantLogger")
    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private EditorAttributesFinder attrsFinder;

    static List<AntlrHintGenerator> all;

    public static synchronized List<AntlrHintGenerator> all() {
        if (all == null) {
            List<AntlrHintGenerator> result = new ArrayList<>();
            result.addAll(Lookup.getDefault().lookupAll(AntlrHintGenerator.class));
            all = result;
        }
        return all;
    }

    protected AntlrHintGenerator() {
    }

    /**
     * Get a utility object that can lookup colorings by name from
     * FontColorSettings for the Antlr grammar mime type and listens correctly
     * for changes.
     *
     * @return An EditorAttributesFinder which wil be cached for the lifetime of
     * this object
     */
    protected final EditorAttributesFinder colorings() {
        if (attrsFinder == null) {
            attrsFinder = EditorAttributesFinder.forMimeType(ANTLR_MIME_TYPE);
        }
        return attrsFinder;
    }

    /**
     * Set whether or not this highlighter / hint generator is enabled.
     *
     * @return If it is enabled
     */
    public final boolean isEnabled() {
        return NbPreferences.forModule(getClass()).getBoolean("enable-" + getClass().getSimpleName(), true);
    }

    /**
     * Returns whether or not this highlighter / hint generator is enabled.
     *
     * @return If the value was changed by this call
     */
    public final boolean setEnabled(boolean val) {
        boolean wasEnabled = isEnabled();
        if (wasEnabled != val) {
            NbPreferences.forModule(getClass()).putBoolean("enable-" + getClass().getSimpleName(), val);
            return true;
        }
        return false;
    }

    /**
     * Called to apply hints to the document.
     *
     * @param tree The parse tree
     * @param extraction The extraction
     * @param res The Antlr generation result
     * @param populate Parser result contents
     * @param fixes Fixes which can have errors and hints added to them
     * @param doc The document
     * @param positions Factory for edit-surviving position ranges to store in
     * hints, so even if the document is updated, the hint can still be applied
     * to the right offsets
     * @param highlights Highlights in the document to add to
     * @return true if highlights were added, false if not
     * @throws BadLocationException If something goes wrong
     */
    public final boolean generateHints(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, HighlightConsumer highlights) throws BadLocationException {
        if (isEnabled()) {
            return generate(tree, extraction, res, populate, fixes, doc, positions, highlights);
        }
        return false;
    }

    /**
     * Called after the enablement check, to determine if this hint should be
     * enabled.
     *
     * @param tree The parse tree
     * @param extraction The extraction
     * @param res The Antlr generation result
     * @param populate Parser result contents
     * @param fixes Fixes which can have errors and hints added to them
     * @param doc The document
     * @param positions Factory for edit-surviving position ranges to store in
     * hints, so even if the document is updated, the hint can still be applied
     * to the right offsets
     * @param highlights Highlights in the document to add to
     * @return true if highlights were added, false if not
     * @throws BadLocationException If something goes wrong
     */
    protected abstract boolean generate(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, HighlightConsumer highlights) throws BadLocationException;

    /**
     * Turns length text into "head...tail".
     *
     * @param content Some text
     * @return An elided version of the string
     */
    protected static String elidedContent(String content) {
        content = content.replaceAll("\\s+", " ");
        if (content.length() <= 21) {
            return content;
        }
        String head = content.substring(0, 10).replace("\\s+", " ");
        String tail = content.substring(content.length() - 10).replace("\\s+", " ");
        return head + "\u2026" + tail; // ellipsis
    }
}
