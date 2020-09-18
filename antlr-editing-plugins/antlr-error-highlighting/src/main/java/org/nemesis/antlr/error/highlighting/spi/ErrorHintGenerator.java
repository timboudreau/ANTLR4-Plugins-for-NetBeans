/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.antlr.v4.tool.ErrorType;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.openide.text.NbDocument;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

/**
 * Specialized highlighter for handling Antlr errors.
 *
 * @author Tim Boudreau
 */
public abstract class ErrorHintGenerator {

    private static final IntMap<ErrorHintGenerator> contents = IntMap.create(16);
    private static final IntSet fastNegativeTest = IntSet.bitSetBased(16);
    private static boolean initialized;
    private final int[] errorTypes;
    @SuppressWarnings("NonConstantLogger")
    protected final Logger LOG = Logger.getLogger(getClass().getName());

    protected ErrorHintGenerator(int... errorTypes) {
        this.errorTypes = sanityCheck(getClass(), errorTypes);
    }

    /**
     * Handle the passed error, if possible, adding hints, highlighting, and so
     * forth - if this method returns true, the default error highlighting for
     * this error will be skipped.
     *
     * @param tree A parse tree for analysis
     * @param err The error generated by MemoryTool running Antlr over this
     * grammar
     * @param fixes The fixes to add to
     * @param ext The extraction generated from our own Antlr parse
     * @param doc The document hints are being applied to
     * @param positions A factory for ranges that can survive document edits
     * @param brandNewBag An OffsetsBag for adding colorings to
     * @param anyHighlights Call <code>set()</code> on this if you add any
     * highlights, otherwise they may be ignored if no other hint generators
     * added highlights either
     * @param colorings A supplier for editor colorings which updates if the
     * user changes colorings in the Options dialog
     * @return true if error highlighting or hints have been handled and no
     * further processing of this error is needed
     * @throws BadLocationException If something goes wrong fetching text from
     * the document
     */
    protected abstract boolean handle(GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            HighlightConsumer brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException;

    private static void init() {
        if (!initialized) {
            Lookup.getDefault().lookupAll(ErrorHintGenerator.class).forEach(eg -> {
                eg.register(contents);
            });
            fastNegativeTest.addAll(contents.keySet());
            initialized = true;
        }
    }

    public static boolean handleError(GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            HighlightConsumer brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        ErrorHintGenerator gen = forError(err);
        if (gen != null) {
            return gen.handle(tree, err, fixes, ext, doc, positions, brandNewBag, anyHighlights, colorings);
        }
        return false;
    }

    private static ErrorHintGenerator forError(ParsedAntlrError err) {
        init();
        if (!fastNegativeTest.contains(err.code())) {
            return null;
        }
        return contents.get(err.code());
    }

    private static int[] sanityCheck(Class<?> type, int[] errs) {
        if (errs.length == 0) {
            throw new IllegalArgumentException("Empty error type array for " + type.getName());
        }
        Arrays.sort(errs);
        int prev = -1;
        for (int i = 0; i < errs.length; i++) {
            if (errs[i] == prev) {
                throw new IllegalArgumentException(type.getName() + " lists the error code " + errs[i]
                        + " more than once in " + Arrays.toString(errs));
            }
            if (errs[i] < 0) {
                throw new IllegalArgumentException("Error code < 0 at " + i
                        + " in " + Arrays.toString(errs) + " for " + type.getName());
            }
        }
        return errs;
    }

    void register(IntMap<ErrorHintGenerator> into) {
        for (int err : errorTypes) {
            ErrorHintGenerator gen = into.put(err, this);
            if (gen != null) {
                LOG.warning(() -> {
                    return "Both " + gen + " and " + this + " handle the Antlr error code " + err + ". "
                            + getClass().getSimpleName() + " will supersede " + gen.getClass().getSimpleName();
                });
            }
        }
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        for (int i = 0; i < errorTypes.length; i++) {
            int et = errorTypes[i];
            ErrorType type = typeFor(et);
            if (type == null) {
                sb.append(et);
            } else {
                sb.append(type.name());
            }
            if (i != errorTypes.length - 1) {
                sb.append(", ");
            }
        }
        return sb.append(')').toString();
    }

    static IntMap<ErrorType> typesByCode;

    static ErrorType typeFor(int code) {
        if (typesByCode == null) {
            ErrorType[] types = ErrorType.values();
            typesByCode = IntMap.create(types.length);
            for (ErrorType et : types) {
                typesByCode.put(et.code, et);
            }
        }
        return typesByCode.get(code);
    }

    private static final boolean WIN = Utilities.isWindows();

    public static final int offsetsOf(Document doc, ParsedAntlrError error, BadLocationIntBiConsumer startEnd) throws BadLocationException {
        if (!WIN && error.hasFileOffset()) {
            // The updated and optimized line offset finding in MemoryTool seems
            // to be working well enough to use it rather than not trusting it.
            // Leaving it off on Windows for now, since \r's will probably
            // screw up the line lengths

            // Pending - left recursion items wind up spanning to the end of the file
//            startEnd.accept(error.fileOffset(), error.fileOffset() + error.length());
        }
        LineDocument lines = LineDocumentUtils.as(doc, LineDocument.class);
        if (lines != null) {
            int docLength = lines.getLength();
//            error.hasFileOffset();
            int lc = LineDocumentUtils.getLineCount(lines);
            int lineNumber = Math.max(0, error.lineNumber() - 1 >= lc
                    ? lc - 1 : error.lineNumber() - 1);
            int lineOffsetInDocument = NbDocument.findLineOffset((StyledDocument) doc, lineNumber);
            int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
            int errorEndOffset = Math.min(lines.getLength() - 1, errorStartOffset + error.length());
            if (errorStartOffset < errorEndOffset) {
                startEnd.accept(Math.min(docLength - 1, errorStartOffset), Math.min(docLength - 1, errorEndOffset));
            } else {
                if (errorStartOffset == 0 && errorEndOffset == -1) {
                    // Antlr does this for a few errors such as 99: Grammar contains no rules
                    startEnd.accept(0, 0);
                } else {
                    Logger.getLogger(ErrorHintGenerator.class.getName())
                            .log(Level.INFO, "Computed nonsensical error start offsets "
                                    + "{0}:{1} for line {2} of {3} for error {4}",
                                    new Object[]{
                                        errorStartOffset, errorEndOffset,
                                        lineNumber, lc, error
                                    });
                }
            }
            return docLength;
        }
        return 0;
    }

    public static final boolean withOffsetsOf(Document doc, ParsedAntlrError error, BadLocationIntBiPredicate startEnd) throws BadLocationException {
//        if (!WIN && error.hasFileOffset()) {
        // The updated and optimized line offset finding in MemoryTool seems
        // to be working well enough to use it rather than not trusting it.
        // Leaving it off on Windows for now, since \r's will probably
        // screw up the line lengths

        // Pending - left recursion items wind up spanning to the end of the file
//            startEnd.accept(error.fileOffset(), error.fileOffset() + error.length());
//        }
        LineDocument lines = LineDocumentUtils.as(doc, LineDocument.class);
        if (lines != null) {
            int docLength = lines.getLength();
            int lc = LineDocumentUtils.getLineCount(lines);
            int lineNumber = Math.max(0, error.lineNumber() - 1 >= lc
                    ? lc - 1 : error.lineNumber() - 1);
            int lineOffsetInDocument = NbDocument.findLineOffset((StyledDocument) doc, lineNumber);
            int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
            int errorEndOffset = Math.min(lines.getLength() - 1, errorStartOffset + error.length());
            if (errorStartOffset < errorEndOffset) {
                return startEnd.test(Math.min(docLength - 1, errorStartOffset), Math.min(docLength - 1, errorEndOffset));
            } else {
                if (errorStartOffset == 0 && errorEndOffset == -1) {
                    // Antlr does this for a few errors such as 99: Grammar contains no rules
                    return startEnd.test(0, 0);
                } else {
                    Logger.getLogger(ErrorHintGenerator.class.getName())
                            .log(Level.INFO, "Computed nonsensical error start offsets "
                                    + "{0}:{1} for line {2} of {3} for error {4}",
                                    new Object[]{
                                        errorStartOffset, errorEndOffset,
                                        lineNumber, lc, error
                                    });
                }
            }
        }
        return !error.hasFileOffset()
                ? false
                : startEnd.test(error.fileOffset(), error.fileOffset() + error.length());
    }
}
