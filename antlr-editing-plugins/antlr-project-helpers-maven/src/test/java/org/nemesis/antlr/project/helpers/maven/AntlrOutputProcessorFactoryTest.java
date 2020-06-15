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
package org.nemesis.antlr.project.helpers.maven;

import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nemesis.antlr.project.helpers.maven.AntlrOutputProcessorFactory.AntlrLineProcessor;
import org.nemesis.antlr.project.helpers.maven.AntlrOutputProcessorFactory.AntlrLineProcessor.ErrInfo;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.output.OutputProcessor;
import org.netbeans.modules.maven.api.output.OutputVisitor;
import org.netbeans.modules.maven.api.output.OutputVisitor.Context;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
@Execution(ExecutionMode.SAME_THREAD)
public class AntlrOutputProcessorFactoryTest {

    private static final String ERR_1 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:0: error: syntax error: '}' came as a complete surprise to me [error 50]";
    private static final String ERR_2 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:11]: syntax error: '}' came as a complete surprise to me";
    private static final String ERR_3 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:22: error: parser rule gwerb not allowed in lexer [error 53]";
    private static final String ERR_4 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:33]: parser rule gwerb not allowed in lexer";
    private static final String ERR_5 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:44: error: reference to parser rule gwerb in lexer rule gwerb [error 160]";
    private static final String ERR_6 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:5555]: reference to parser rule gwerb in lexer rule gwerb";
    private static final String ERR_7 = "/home/folder\\ with\\ spaces/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:66]: reference to parser rule gwerb in lexer rule gwerb";
    private static final String ERR_8 = "/home/tim/work/personal/personal/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4::: error: syntax error: mismatched character '<EOF>' expecting '\"' [error 50]";

    private static final String W_ERR_1 = "C:\\foo\\bar\\MarkdownLexer.md:130:77: error: syntax error: '}' came as a complete surprise to me [error 50]";
    private static final String W_ERR_2 = "\\\\Server\\foo\\bar\\MarkdownLexer.md:130:8888: error: syntax error: '}' came as a complete surprise to me [error 50]";

    @Test
    public void testParseErrors() {
        for (String err : new String[]{ERR_1, ERR_2, ERR_3, ERR_4, ERR_5, ERR_6, ERR_7, ERR_8, W_ERR_1, W_ERR_2}) {
            testOne(err);
        }
    }

    private void testOne(String err) {
        switch (err) {
            case W_ERR_1:
            case W_ERR_2:
                ErrInfo.setParsingMode(ErrInfo.ParsingMode.WINDOWS);
                break;
            default:
                ErrInfo.setParsingMode(ErrInfo.ParsingMode.UNIX);
                break;
        }
        ErrInfo info = ErrInfo.parse(err);
        assertNotNull(info, err);
        assertFalse(info.file.endsWith("130"));
        int expPos;
        boolean expClosingBracket = false;
        String expFile = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4";
        switch (err) {
            case ERR_1:
                expClosingBracket = true;
                expPos = 0;
                break;
            case ERR_2:
                expPos = 11;
                break;
            case ERR_3:
                expClosingBracket = true;
                expPos = 22;
                break;
            case ERR_4:
                expPos = 33;
                break;
            case ERR_5:
                expClosingBracket = true;
                expPos = 44;
                break;
            case ERR_6:
                expPos = 5555;
                break;
            case ERR_7:
                expPos = 66;
                expFile = "/home/folder\\ with\\ spaces/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4";
                break;
            case ERR_8 :
                expPos = 0;
                expFile = "/home/tim/work/personal/personal/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4";
                expClosingBracket = true;
                assertEquals(0, info.line, "-1 offset should have been converted to 0");
                assertEquals(0, info.lineOffset, "-1 offset should have been converted to 0");
                break;
            case W_ERR_1:
                expFile = "C:\\foo\\bar\\MarkdownLexer.md";
                expPos = 77;
                expClosingBracket = true;
                break;
            case W_ERR_2:
                expPos = 8888;
                expFile = "\\\\Server\\foo\\bar\\MarkdownLexer.md";
                expClosingBracket = true;
                break;
            default:
                throw new AssertionError("Unknown text: " + err);
        }
        assertEquals(expPos, info.lineOffset);
        assertEquals(expFile, info.file);
        assertEquals(expClosingBracket, info.message.charAt(info.message.length() - 1) == ']');
        assertEquals(expClosingBracket, info.isExceptionMessage);
    }

    @Test
    public void testMavenSessionPlayback() throws IOException {
        // We use a microformat that lets us embed assertions at the head of lines about what
        //
        ErrInfo.setParsingMode(ErrInfo.ParsingMode.UNIX);
        String session;
        try (InputStream in = AntlrOutputProcessorFactoryTest.class.getResourceAsStream("errors-session.txt")) {
            assertNotNull(in, "errors-session.txt is not adjacent to AntlrOutputProcessorFactoryTest");
            session = Streams.readString(in);
        }
        AntlrOutputProcessorFactory fact = new AntlrOutputProcessorFactory();
        Set<? extends OutputProcessor> set = fact.createProcessorsSet(new FakeProject());
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertEquals(1, set.size());
        OutputProcessor op = set.iterator().next();
        assertTrue(op instanceof AntlrOutputProcessorFactory.AntlrLineProcessor);
        String[] lines = session.split("\n");
        AntlrLineProcessor lp = (AntlrLineProcessor) op;
        OutputVisitor ov = new OutputVisitor(new Context() {
            @Override
            public Project getCurrentProject() {
                return new FakeProject();
            }
        });
        lp.sequenceStart("mojo-execute#antlr4:antlr4", ov);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            testLine(line, lp, i + 1);
        }
        lp.sequenceFail("mojo-execute#antlr4:antlr4", ov);
        lp.sequenceEnd("project-execute", ov);
        lp.sequenceEnd("session-execute", ov);
    }

    private void testLine(String line, AntlrLineProcessor proc, int lineNumber) {
        if (line.length() > 0 && line.charAt(0) == ':') {
            String[] instructionsAndText = line.substring(1).split("\\|\\!\\|");
            assertTrue(instructionsAndText.length == 2, "Corrupted input line: '"
                    + line + "' could not split on '|!|' "
                    + "delimiter but does start with a ':'");
            handleOneLine(instructionsAndText[0], instructionsAndText[1], proc, new OutputVisitor(), lineNumber);
        }
    }

    private void handleOneLine(String instructions, String line, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
        int oldState = proc.state();
        proc.processLine(line, vis);
        int newState = proc.state();
        String[] assertions = instructions.split(";");
        for (int i = 0; i < assertions.length; i++) {
            handleOneAssertion(assertions[i], line, oldState, newState, proc, vis, lineNumber);
        }
    }

    private void handleOneAssertion(String assertion, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
        boolean handled = false;
        for (int i = 0; i < allAssertionTypes.length; i++) {
            handled = allAssertionTypes[i].maybeProcess(assertion, line, oldState, newState, proc, vis, lineNumber);
            if (handled) {
                break;
            }
        }
        assertTrue(handled, "Malformed asssertion '" + assertion + "' not recognized at line " + lineNumber);
    }

    static abstract class AssertionType {

        private final String name;

        public AssertionType(String name) {
            this.name = name;
        }

        final boolean maybeProcess(String assertion, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            if (assertion.startsWith(name)) {
                String input = assertion.substring(name.length()).trim();
                run(input, line, oldState, newState, proc, vis, lineNumber);
                return true;
            }
            return false;
        }

        abstract void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber);
    }

    private static class StateChange extends AssertionType {

        StateChange() {
            super("StateChange");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            String[] args = tail.split("\\s+");
            assertEquals(2, args.length, "line " + line + " '" + tail + "' is badly formatted - need two numeric arguments");
            int from = Integer.parseInt(args[0]);
            int to = Integer.parseInt(args[1]);
            assertEquals(oldState, from, "Previous state should be " + from + " but was " + oldState + "at " + lineNumber + ": '" + line + "' states " + oldState + "->" + newState + " expected " + from + "->" + to);
            assertEquals(newState, to, "Next state should be " + to + " but was " + newState + " at line " + lineNumber+ ": '" + line + "' states " + oldState + "->" + newState + " expected " + from + "->" + to);
        }
    }

    private static class LineHidden extends AssertionType {

        LineHidden() {
            super("LineHidden");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            assertTrue(vis.isLineSkipped(), "Line " + lineNumber + " should have told the OutputVisitor to skip line '" + line + "'");
        }
    }

    private static class ErrorListenerSet extends AssertionType {

        ErrorListenerSet() {
            super("ErrorListenerSet");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            assertNotNull(vis.getOutputListener(), "Listener should have been set for line " + lineNumber + ": '" + line + "'");
            assertTrue(vis.getOutputListener() instanceof AntlrOutputProcessorFactory.AntlrLineProcessor.Listener);
            assertNotNull(((AntlrOutputProcessorFactory.AntlrLineProcessor.Listener) vis.getOutputListener()).info);
        }
    }

    private static class IsExceptionMessage extends AssertionType {

        IsExceptionMessage() {
            super("IsExceptionMessage");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            ErrInfo info = ((AntlrOutputProcessorFactory.AntlrLineProcessor.Listener) vis.getOutputListener()).info;
            assertNotNull(info, "No info set for line " + lineNumber + ": '" + line + "'");
            assertTrue(info.isExceptionMessage, "Line " + lineNumber + " should have been detected as an exception message, not error output: '" + line + "'");
        }
    }

    private static class NotExceptionMessage extends AssertionType {

        NotExceptionMessage() {
            super("NotExceptionMessage");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            ErrInfo info = ((AntlrOutputProcessorFactory.AntlrLineProcessor.Listener) vis.getOutputListener()).info;
            assertNotNull(info, "No info set for line " + lineNumber + ": '" + line + "'");
            assertFalse(info.isExceptionMessage, "Line " + lineNumber + " should have been detected as a error output, not an exception message: '" + line + "'");
        }
    }

    private static class StackTraceLine extends AssertionType {

        StackTraceLine() {
            super("StackTraceLine");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            assertEquals(AntlrOutputProcessorFactory.AntlrLineProcessor.STATE_IN_STACK_TRACE, newState, "Expected state STATE_IN_STACK_TRACE (" + AntlrOutputProcessorFactory.AntlrLineProcessor.STATE_IN_STACK_TRACE + ") for line " + lineNumber + ": '" + line + "'");
        }
    }

    private static class Ready extends AssertionType {

        Ready() {
            super("Ready");
        }

        @Override
        void run(String tail, String line, int oldState, int newState, AntlrLineProcessor proc, OutputVisitor vis, int lineNumber) {
            assertEquals(AntlrOutputProcessorFactory.AntlrLineProcessor.STATE_READY, newState, "Expected state READY at line " + lineNumber + ": '" + line + "'");
        }
    }

    private AssertionType[] allAssertionTypes = new AssertionType[]{new StateChange(),
        new LineHidden(), new ErrorListenerSet(), new IsExceptionMessage(),
        new NotExceptionMessage(), new StackTraceLine(), new Ready()};

    static final class FakeProject implements Project {

        private final HideAntlrSourceDirsFromMavenOtherSources marker = new HideAntlrSourceDirsFromMavenOtherSources(Lookup.EMPTY);

        @Override
        public FileObject getProjectDirectory() {
            return null;
        }

        @Override
        public Lookup getLookup() {
            return Lookups.singleton(marker);
        }
    }
}
