package org.nemesis.antlr.spi.language;

import org.nemesis.antlr.spi.language.fix.Fixes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.util.Exceptions;
import org.openide.util.Parameters;

/**
 * A hook method which allows modules to registor contributors to parser result
 * contents - implement and register using MimeRegistration with type
 * ParseResultHook.
 *
 * @author Tim Boudreau
 */
public class ParseResultHook<T extends ParserRuleContext> {

    private final Class<T> type;

    protected ParseResultHook(Class<T> type) {
        Parameters.notNull("type", type);
        this.type = type;
    }

    private <R> ParseResultHook<? super R> castIfCompatible(R obj) {
        if (type.isInstance(obj)) {
            return (ParseResultHook<? super R>) this;
        }
        return null;
    }

    /**
     * Called when a file is reparsed (hint: to get the origin file, use, e.g.,
     *  <code>extraction.source().lookup(FileObject.class)</code>.
     *
     * @param tree The parse tree - <i>do not hold a reference to it!!</i>
     * @param mimeType The mime type being parsed
     * @param extraction The extraction derived from walking the parse tree by
     * all registered ExtractionContributors.
     *
     * @param populate The parse result, for populating
     * @throws Exception if something goes wrong
     */
    protected void onReparse(T tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {

    }

    void reparsed(T tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) {
        try {
            onReparse(tree, mimeType, extraction, populate, fixes);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    static <R extends ParserRuleContext> void runForMimeType(String mimeType, R ctx, Extraction extraction, ParseResultContents populate, Fixes fixes) {
        Collection<? extends ParseResultHook> all = MimeLookup.getLookup(mimeType).lookupAll(ParseResultHook.class);
        List<ParseResultHook<? super R>> found = new ArrayList<>(3);
        for (ParseResultHook<?> p : all) {
            ParseResultHook<? super R> matched = p.castIfCompatible(ctx);
            if (matched != null) {
                found.add(matched);
            }
        }
        for (ParseResultHook<? super R> p : found) {
            p.reparsed(ctx, mimeType, extraction, populate, fixes);
        }
    }
}
