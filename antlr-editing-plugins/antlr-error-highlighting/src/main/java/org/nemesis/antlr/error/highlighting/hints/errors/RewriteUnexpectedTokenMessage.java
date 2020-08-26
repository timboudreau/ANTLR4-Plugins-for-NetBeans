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
package org.nemesis.antlr.error.highlighting.hints.errors;

import com.mastfrog.function.state.Bool;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.NbBundle;

/**
 * Rewrites the slightly goofy error message "'foo' came as a complete surprise
 * to me".
 *
 * @author Tim Boudreau
 */
public class RewriteUnexpectedTokenMessage extends ErrorHintGenerator {

    public RewriteUnexpectedTokenMessage() {
        super(
                50 // 'foo' came as a complete surprise to me
        );
    }

    @Override
    protected boolean handle(ANTLRv4Parser.GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            OffsetsBag brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        Bool result = Bool.create();
        offsetsOf(doc, err, (start, end) -> {
            fixes.addError(err.id(), positions.range(start, end), stringifyUnexpectedTokenMessage(err.message()),
                    () -> htmlifyUnexpectedTokenMessage(err.message()));
            brandNewBag.addHighlight(start, end, colorings.get().errors());
            anyHighlights.set();
            result.set();
        });
        return result.get();
    }

    private static final Pattern SURPRISE_PATTERN = Pattern.compile("^.*?'(.*?)'.*");

    @NbBundle.Messages({
        "# {0} - token",
        "unexpectedMsg=Bad syntax - unexpected token ''{0}''",
        "# {0} - token",
        "unexpectedMsgHtml=Bad syntax - unexpected token <i>{0}</i>",})
    private static String stringifyUnexpectedTokenMessage(String msg) {
        Matcher m = SURPRISE_PATTERN.matcher(msg);
        if (m.find()) {
            return Bundle.unexpectedMsg(m.group(1));
        }
        return msg;
    }

    private static String htmlifyUnexpectedTokenMessage(String msg) {
        Matcher m = SURPRISE_PATTERN.matcher(msg);
        if (m.find()) {
            return Bundle.unexpectedMsgHtml(m.group(1));
        }
        return msg;
    }
}
