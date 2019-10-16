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
package org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import java.nio.file.Path;

import java.util.List;
import java.util.Optional;

import java.util.logging.Logger;

import javax.swing.event.ChangeListener;

import javax.swing.text.Document;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;

import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;

import org.nemesis.antlr.v4.netbeans.v8.project.ProjectType;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;

import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.syntax.TokensSyntacticErrorListener;

import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl.TokensLexer;
import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl.TokensParser;

import org.nemesis.antlr.v4.netbeans.v8.tokens.code.summary.Collector;

import org.netbeans.api.project.Project;

import org.netbeans.modules.csl.spi.ParserResult;

import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;

import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class TokensParserAdapter extends Parser {
    private static final Logger LOG = Logger.getLogger
                        ("ANTLR plugin:" + TokensParserAdapter.class.getName());
    
    private       TokensParserResult  result ;
    
    public TokensParserAdapter() {
//        System.out.println("TokensParserAdapter:TokensParserAdapter() : begin");
        this.result = null;
//        System.out.println("TokensParserAdapter:TokensParserAdapter() : end");
    }
    
    @Override
    public void parse(Snapshot                snapshot,
                      Task                    task    ,
                      SourceModificationEvent event   ) throws ParseException {
//        System.out.println("TokensParserAdapter:parse(Snapshot, Task, SourceModificationEvent) : begin");
        FileObject tokensFO = snapshot.getSource().getFileObject();
        Document doc = ProjectHelper.getDocument(tokensFO);
        Optional<Project> project = ProjectHelper.getProject(doc);
        ProjectType projectType = project.isPresent() ? ProjectHelper.getProjectType(project.get())
                : ProjectType.UNDEFINED;

        Path tokensFilePath = FileUtil.toFile(tokensFO).toPath();
        String contentToBeParsed = snapshot.getText().toString();
        try (Reader sr = new StringReader(contentToBeParsed) ) {
            TokensLexer lexer = new TokensLexer(CharStreams.fromReader(sr));
            CommonTokenStream tokens = new CommonTokenStream(lexer);  
            TokensParser tokensParser = new TokensParser(tokens);

            TokensSyntacticErrorListener syntacticErrorListener =
                                     new TokensSyntacticErrorListener(tokensFO);
            tokensParser.removeErrorListeners(); // remove ConsoleErrorListener
            tokensParser.addErrorListener(syntacticErrorListener); // add ours
         // If we are in an undefined project type, we do not collect info
            if (projectType != ProjectType.UNDEFINED) {
                Collector collector = new Collector(doc, Optional.ofNullable(tokensFilePath));
                tokensParser.addParseListener(collector);
            }
            
            tokensParser.token_declarations();
            List<ParsingError> errors = syntacticErrorListener.getParsingError();
            
            result = new TokensParserResult(snapshot, errors);
        } catch (IOException ex) {
            LOG.severe("Strange! Unable to read the String Buffer");
        } catch (RecognitionException ex) {
            LOG.severe(ex.toString());
        }
//        System.out.println("TokensParserAdapter:parse(Snapshot, Task, SourceModificationEvent) : end");
    }

    
    @Override
    public Result getResult (Task task) {
        return result;
    }

    
    @Override
    public void addChangeListener (ChangeListener changeListener) {
    }

    @Override
    public void removeChangeListener (ChangeListener changeListener) {
    }

    
    public static class TokensParserResult extends ParserResult {
        private       boolean            valid;
        private final List<ParsingError> errors;

        TokensParserResult
             (Snapshot           snapshot,
              List<ParsingError> errors  ) {
            super (snapshot);
            this.errors = errors;
            this.valid = true;
        }

        @Override
        protected void invalidate () {
            valid = false;
        }
        
        
        @Override
        public List<ParsingError> getDiagnostics() {
            return errors;
        }
    }
}