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
package org.nemesis.antlr.spi.language;

import org.nemesis.antlr.spi.language.fix.Fixes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Parameters;

/**
 * A hook method which allows modules to registor contributors to parser result
 * contents - implement and register using MimeRegistration with type
 * ParseResultHook. Programmatic registration is also available to support
 * refreshing dynamically generated editor kits when a grammar is reparsed.
 *
 * @author Tim Boudreau
 */
public class ParseResultHook<T extends ParserRuleContext> {

    private final Class<T> type;
    private static final Logger LOG = Logger.getLogger(ParseResultHook.class.getName());

    protected ParseResultHook(Class<T> type) {
        Parameters.notNull("type", type);
        this.type = type;
    }

    /**
     * Programmatically register a hook to get called when a <i>specific
     * file</i> is reparsed. If you are registering your hook with
     * <code>&#064;MimeRegistration</code> then <b>it is a mistake to also call
     * this method</b> - programmatic registration is available for handling the
     * case of implementing file types over live antlr grammars, were, when the
     * grammar is altered, all open editors over files of that type should
     * update their syntax highlighting.
     * <p>
     * The passed hook must remain <i>strongly referenced</i> or it wil be
     * garbage collected and never called.
     * </p>
     *
     * @param <T> The entry point rule context type
     * @param fo A file object
     * @param hook A hook
     */
    public static <T extends ParserRuleContext> void register(FileObject fo, ParseResultHook<T> hook) {
        ProgrammaticParseResultHookRegistry.register(fo, hook);
    }

    /**
     * Programmatically register a hook to get called when any file of a
     * <i>particular mime type</i> is reparsed. If you are registering your hook
     * with <code>&#064;MimeRegistration</code> then <b>it is a mistake to also
     * call this method</b> - programmatic registration is available for
     * handling the case of implementing file types over live antlr grammars,
     * were, when the grammar is altered, all open editors over files of that
     * type should update their syntax highlighting.
     * <p>
     * The passed hook must remain <i>strongly referenced</i> or it wil be
     * garbage collected and never called.
     * </p>
     *
     * @param <T> The entry point rule context type
     * @param String mimeType
     * @param hook A hook
     */
    public static <T extends ParserRuleContext> void register(String mimeType, ParseResultHook<T> hook) {
        ProgrammaticParseResultHookRegistry.register(mimeType, hook);
    }

    /**
     * Unregister a hook that was previously registered with a programmatic call
     * to <code>register()</code> (has no effect if the hook is registered via
     * <code>&#064;MimeRegistration</code>
     *
     * @param <T> The tree type
     * @param mimeType The mime type
     * @param hook The hook
     * @return true if the hook had indeeed been registered and was removed
     */
    public static <T extends ParserRuleContext> boolean deregister(String mimeType, ParseResultHook<T> hook) {
        return ProgrammaticParseResultHookRegistry.deregister(mimeType, hook);
    }

    /**
     * Unregister a hook that was previously registered with a programmatic call
     * to <code>register()</code> (has no effect if the hook is registered via
     * <code>&#064;MimeRegistration</code>
     *
     * @param <T> The tree type
     * @param fo The file
     * @param hook The hook
     * @return true if the hook had indeeed been registered and was removed
     */
    public static <T extends ParserRuleContext> boolean deregister(FileObject fo, ParseResultHook<T> hook) {
        return ProgrammaticParseResultHookRegistry.deregister(fo, hook);
    }

    private <R> ParseResultHook<? super R> castIfCompatible(R obj) {
        if (type.isInstance(obj)) {
            return (ParseResultHook<? super R>) this;
        }
        return null;
    }

    Class<T> type() {
        return type;
    }

    /**
     * Called when a file is reparsed (hint: to get the origin file, use, e.g.,
     * <code>extraction.source().lookup(FileObject.class)</code>.
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
        LOG.log(Level.FINE, "Got reparse of {0} for {1}", new Object[] { extraction.source(), mimeType});
        try {
            onReparse(tree, mimeType, extraction, populate, fixes);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    static <R extends ParserRuleContext> void runForMimeType(String mimeType, R ctx, 
            Extraction extraction, ParseResultContents populate, Fixes fixes) {
        boolean postprocess = NbAntlrUtils.isPostprocessingEnabled();
        if (!postprocess) {
            System.out.println( "SKIP POST PROCESSING FOR " + mimeType );
            return;
        }
        Collection<? extends ParseResultHook> all = MimeLookup.getLookup(mimeType).lookupAll(ParseResultHook.class);
        List<ParseResultHook<? super R>> found = new ArrayList<>(3);
        for (ParseResultHook<?> p : all) {
            ParseResultHook<? super R> matched = p.castIfCompatible(ctx);
            if (matched != null) {
                found.add(matched);
            }
        }
        LOG.log(Level.FINEST, "Run {0} hooks registered to {1} for reparse of {2}",
                new Object[] { all.size(), mimeType, extraction.source()});
        try {
            for (ParseResultHook<? super R> p : found) {
                try {
                    p.reparsed(ctx, mimeType, extraction, populate, fixes);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Exception running hook " + p + " for "
                            + mimeType + " against " + extraction.source(), ex);
                }
            }
        } finally {
            if (ProgrammaticParseResultHookRegistry.active()) {
                LOG.log(Level.FINEST, "Have programmatically registered hooks to run");
                ProgrammaticParseResultHookRegistry.onReparse(ctx, mimeType, extraction, populate, fixes);
            }
        }
    }
}
