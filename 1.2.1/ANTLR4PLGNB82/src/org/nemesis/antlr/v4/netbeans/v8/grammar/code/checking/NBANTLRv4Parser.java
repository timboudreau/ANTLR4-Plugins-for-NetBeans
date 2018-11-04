/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking;

import java.awt.EventQueue;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import java.util.logging.Logger;

import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;

import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.syntax.ANTLRv4SyntacticErrorListener;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;

import org.netbeans.modules.csl.spi.ParserResult;

import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class NBANTLRv4Parser extends Parser {

    private static final Logger LOG = Logger.getLogger("ANTLR plugin:" + NBANTLRv4Parser.class.getName());
    // source document of snapshot
    private Document doc;
    private ANTLRv4Lexer lexer;
    private ANTLRv4GrammarChecker grammarChecker;
    private ANTLRv4SyntacticErrorListener errorListener;
    private ANTLRv4ParserResult result;

    public ANTLRv4Lexer getLexer() {
        return lexer;
    }

    @Override
    public void parse(Snapshot snapshot,
            Task task,
            SourceModificationEvent event) {
        assert snapshot != null;
        assert snapshot.getSource() != null;
//        assert snapshot.getSource().getFileObject() != null : "No file object";

        // With next two statements, if document is already open in an editor
        // then doc variable is non null else it is null:
        // this.doc = snapshot.getSource().getDocument(false);
        // this.doc = NbDocument.getDocument(snapshot.getSource().getFileObject());
        // We do not want to open an editor containing the document. We just
        // want to open / load it in memory. For that, we use EditorCookie
        // (that's more than surprising to use EditorCookie for not editing!)
        Document doc = snapshot.getSource().getDocument(false);
        FileObject grammarFO = snapshot.getSource().getFileObject();
        if (doc == null && grammarFO != null) {
            doc = ProjectHelper.getDocument(grammarFO);
        }
        this.doc = doc;

        String contentToBeParsed = snapshot.getText().toString();

        Path grammarFilePath = null;
        if (grammarFO != null) {
            grammarFilePath = FileUtil.toFile(grammarFO).toPath();
        }
        parse(doc, grammarFilePath, snapshot, contentToBeParsed);
    }

    public static final Map<Document, List<Reference<Consumer<ANTLRv4ParserResult>>>> notifyOnParse = new WeakHashMap<>();

    public static void notifyOnReparse(Document of, Consumer<ANTLRv4ParserResult> consumer) {
        List<Reference<Consumer<ANTLRv4ParserResult>>> l = notifyOnParse.get(of);
        if (l == null) {
            l = new ArrayList<>(4);
            notifyOnParse.put(of, l);
        }
        l.add(new WeakReference<>(consumer));
    }

    private static void onReparse(Document doc, ANTLRv4ParserResult res) {
        List<Reference<Consumer<ANTLRv4ParserResult>>> l = notifyOnParse.get(doc);
        if (l != null) {
            List<Consumer<ANTLRv4ParserResult>> all = new ArrayList<>(l.size());
            for (Reference<Consumer<ANTLRv4ParserResult>> r : l) {
                Consumer<ANTLRv4ParserResult> c = r.get();
                if (c != null) {
                    all.add(c);
                }
            }
            if (!all.isEmpty()) {
                EventQueue.invokeLater(() -> {
                    for (Consumer<ANTLRv4ParserResult> c : all) {
                        c.accept(res);
                    }
                });
            }
        }
    }

    public ANTLRv4ParserResult parse(Document doc, Path grammarFilePath, Snapshot snapshot, String contentToBeParsed) {
        grammarChecker = new ANTLRv4GrammarChecker(doc, grammarFilePath);
        grammarChecker.check(contentToBeParsed);
        errorListener = grammarChecker.getSyntacticErrorListener();
        ANTLRv4SemanticParser semanticParser = grammarChecker.getSemanticParser();
        result = new ANTLRv4ParserResult(snapshot, errorListener, semanticParser);
        onReparse(doc, result);
        return result;
    }

    public static ANTLRv4GrammarChecker parse(Path path) throws IOException, BadLocationException {
        Document doc = new PlainDocument();
        int pos = 0;
        StringBuilder sb = new StringBuilder();
        for (String s : Files.readAllLines(path)) {
            sb.append(s).append("\n");
            doc.insertString(pos, s, null);
            pos += s.length();
        }
        ANTLRv4GrammarChecker grammarChecker = new ANTLRv4GrammarChecker(doc, path);
        grammarChecker.check(sb.toString());
        return grammarChecker;
    }

    @Override
    public ANTLRv4ParserResult getResult(Task task) {
        return result;
    }

    @Override
    public void addChangeListener(ChangeListener changeListener) {
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
    }

    /**
     * Now ANTLRv4ParserResult cannot just inherit from Parser.Result. It must
     * inherit from ParserResult (CSL module) that inherits from Parser.Result.
     * As a result, we have to provide an implementation to a new method called
     * getDiagnostics().
     *
     */
    public static class ANTLRv4ParserResult extends ParserResult {

        private boolean valid;
        private final ANTLRv4SyntacticErrorListener errorListener;
        private final ANTLRv4SemanticParser semanticParser;

        ANTLRv4ParserResult(Snapshot snapshot,
                ANTLRv4SyntacticErrorListener errorListener,
                ANTLRv4SemanticParser semanticParser) {
            super(snapshot);
            this.errorListener = errorListener;
            this.semanticParser = semanticParser;
            this.valid = true;
        }

        public ANTLRv4SemanticParser semanticParser() {
            return semanticParser;
        }

        @Override
        protected void invalidate() {
            valid = false;
        }

        @Override
        public List<ParsingError> getDiagnostics() {
            List<ParsingError> answer = new ArrayList<>();
            answer.addAll(errorListener.getParsingError());
            if (semanticParser != null) {
                answer.addAll(semanticParser.getSemanticErrors());
                answer.addAll(semanticParser.getSemanticWarnings());
            }
            return answer;
        }
    }
}
