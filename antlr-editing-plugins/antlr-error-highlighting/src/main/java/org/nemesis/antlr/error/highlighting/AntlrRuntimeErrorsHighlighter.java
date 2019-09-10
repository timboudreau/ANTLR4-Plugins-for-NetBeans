package org.nemesis.antlr.error.highlighting;

import java.awt.Color;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRuntimeErrorsHighlighter implements Subscriber {

    protected final OffsetsBag bag;
    private final AttributeSet underlining;
    private static final Logger LOG = Logger.getLogger(
            AntlrRuntimeErrorsHighlighter.class.getName());

    AntlrRuntimeErrorsHighlighter(Context ctx) {
        bag = new OffsetsBag(ctx.getDocument(), true);
        // XXX listen for changes, etc
        MimePath mimePath = MimePath.parse("text/x-g4");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet set = fcs.getFontColors("error");
        if (set == null) {
            set = fcs.getFontColors("errors");
            if (set == null) {
                set = AttributesUtilities.createImmutable(EditorStyleConstants.WaveUnderlineColor, Color.RED);
            }
        }
        underlining = set;
        FileObject fo = NbEditorUtilities.getFileObject(ctx.getDocument());
        RebuildSubscriptions.subscribe(fo, this);
    }

    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        bag.clear();
        List<ParsedAntlrError> errors = res.errors();
        Optional<Path> path = extraction.source().lookup(Path.class);
        for (ParsedAntlrError err : errors) {
            boolean shouldAdd = true;
            if (path.isPresent()) {
                Path p = path.get();
                shouldAdd = p.endsWith(err.path());
            }
            if (shouldAdd) {
                try {
                    System.out.println("RH Errs add " + err.fileOffset() + ":" + (err.fileOffset() + err.length())
                            + " line " + err.lineNumber() + " pos " + err.lineOffset());
                    bag.addHighlight(err.fileOffset(), err.fileOffset() + err.length(), underlining);

                    if (!handleFix(err, fixes, extraction)) {
                        String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                        fixes.addError(errId, err.fileOffset(), err.fileOffset() + err.length(),
                                err.message());
                    }
                } catch (IllegalStateException ex) {
                    System.err.println("No line offsets in " + err);
                } catch (BadLocationException ex) {
                    LOG.log(Level.WARNING, "Bad error location " + err.fileOffset() + ":"
                            + (err.fileOffset() + err.length()), ex);
                }
            }
        }
        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        if (!unknowns.isEmpty()) {
            Iterator<SemanticRegion<UnknownNameReference<RuleTypes>>> it = unknowns.iterator();
            while (it.hasNext()) {
                SemanticRegion<UnknownNameReference<RuleTypes>> unk = it.next();
                String text = unk.key().name();
                if ("EOF".equals(text)) {
                    continue;
                }
                try {
                    String hint = NbBundle.getMessage(AntlrRuntimeErrorsHighlighter.class, "unknown_rule_referenced", text);
                    fixes.addHint(hint, unk, text, fixConsumer -> {
                        NamedSemanticRegions<RuleTypes> nameRegions = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                        for (String sim : nameRegions.topSimilarNames(text, 5)) {
                            String msg = NbBundle.getMessage(AntlrRuntimeErrorsHighlighter.class,
                                    "replace", text, sim);
                            fixConsumer.addReplacement(msg, unk.start(), unk.end(), sim);
                        }
                    });
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

    }

    boolean handleFix(ParsedAntlrError err, Fixes fixes, Extraction ext) throws BadLocationException {
        switch (err.code()) {
            case 51: // rule redefinition
            case 52: // lexer rule in parser grammar
            case 53: // parser rule in lexer grammar
                String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                NamedSemanticRegion<RuleTypes> region = ext.namedRegions(AntlrKeys.RULE_BOUNDS).at(err.fileOffset());
                if (region == null) {
                    return false;
                }
                fixes.addError(errId, region.start(), region.end(), err.message(), fixConsumer -> {
                    fixConsumer.addDeletion(NbBundle.getMessage(
                            AntlrRuntimeErrorsHighlighter.class, "delete_rule"),
                            region.start(), region.end());
                });
                return true;
            default:
                return false;
        }
    }

    HighlightsContainer bag() {
        return bag;
    }
}
