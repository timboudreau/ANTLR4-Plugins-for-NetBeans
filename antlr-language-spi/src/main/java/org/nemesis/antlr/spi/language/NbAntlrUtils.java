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

import java.util.Collections;
import java.util.function.BiConsumer;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.filesystems.FileObject;

/**
 * Some utility adapter and convenience methods.
 *
 * @author Tim Boudreau
 */
public final class NbAntlrUtils {

    public static CharStream newCharStream(LexerInput input, String name) {
        return new AntlrStreamAdapter(input, name);
    }

    public static <T extends TokenId> Lexer<T> createLexer(LexerRestartInfo<T> info, NbLexerAdapter<T, ?> adapter) {
        return new GenericAntlrLexer<>(info, adapter);
    }

    public static AbstractEditorAction createGotoDeclarationAction(NameReferenceSetKey<?>... keys) {
        return new AntlrGotoDeclarationAction(keys);
    }

    public static TaskFactory createErrorHighlightingTaskFactory(String mimeType) {
        return AntlrInvocationErrorHighlighter.factory(mimeType);
    }

    public static void parseImmediately(Document doc, BiConsumer<Extraction, Exception> consumer) {
        parseImmediately(Source.create(doc), consumer);
    }

    public static void parseImmediately(FileObject file, BiConsumer<Extraction, Exception> consumer) {
        parseImmediately(Source.create(file), consumer);
    }

    public static Extraction parseImmediately(Document doc) throws Exception {
        Extraction[] ext = new Extraction[1];
        Exception[] ex = new Exception[1];
        parseImmediately(doc, (res, thrown) -> {
            ex[0] = thrown;
            ext[0] = res;
        });
        if (ex[0] != null) {
            throw ex[0];
        }
        return ext[0];
    }

    public static Extraction parseImmediately(FileObject file) throws Exception {
        Extraction[] ext = new Extraction[1];
        Exception[] ex = new Exception[1];
        parseImmediately(file, (res, thrown) -> {
            ex[0] = thrown;
            ext[0] = res;
        });
        if (ex[0] != null) {
            throw ex[0];
        }
        return ext[0];
    }

    private static void parseImmediately(Source src, BiConsumer<Extraction, Exception> consumer) {
        String mime = src.getMimeType();
        if (mime == null || ParserManager.canBeParsed(mime)) {
            try {
                ParserManager.parse(Collections.singleton(src), new UserTask() {
                    @Override
                    public void run(ResultIterator resultIterator) throws Exception {
                        Parser.Result res = resultIterator.getParserResult();
                        if (res instanceof ExtractionParserResult) {
                            consumer.accept(((ExtractionParserResult) res).extraction(), null);
                        } else {
                            consumer.accept(null, new IllegalStateException(
                                    "Not an ExtractionParserResult: "
                                    + res + " (" + res.getClass().getName()
                                    + "). Some other parser intercepted it?"));
                        }
                    }
                });
            } catch (ParseException ex) {
                consumer.accept(new Extraction(), ex);
            }
        } else {
            consumer.accept(null, new IllegalArgumentException(
                    "No parser registered for mime type " + mime));
        }
    }

    private NbAntlrUtils() {
        throw new AssertionError();
    }

}
