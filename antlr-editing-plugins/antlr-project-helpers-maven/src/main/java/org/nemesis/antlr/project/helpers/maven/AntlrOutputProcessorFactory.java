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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.output.OutputProcessor;
import org.netbeans.modules.maven.api.output.OutputProcessorFactory;
import org.netbeans.modules.maven.api.output.OutputVisitor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOColors;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;

/**
 * Makes antlr errors clickable in the output window.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = OutputProcessorFactory.class)
public class AntlrOutputProcessorFactory implements OutputProcessorFactory {

    private static final Logger LOG = Logger.getLogger(AntlrOutputProcessorFactory.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    @Override
    public Set<? extends OutputProcessor> createProcessorsSet(Project prjct) {
        if (prjct != null && prjct.getLookup().lookup(HideAntlrSourceDirsFromMavenOtherSources.class) == null) {
            return Collections.emptySet();
        }
        LOG.log(Level.FINE, "Create an AntlrLineProcessor for {0}", prjct);
        return Collections.singleton(new AntlrLineProcessor());
    }

    static class AntlrLineProcessor implements OutputProcessor {

        private static final String[] SEQS = new String[]{"mojo-execute#antlr4:antlr4"};
        static final int STATE_READY = 0;
        static final int STATE_RECEIVED_LINE = 1;
        static final int STATE_ANTICIPATING_EXCEPTION_TYPE = 2;
        static final int STATE_IN_STACK_TRACE = 3;
        int state = STATE_READY;
        private static final Pattern EXCEPTION_PATTERN = Pattern.compile("^org\\.antlr\\.\\S+Exception");
//        private static final Pattern STACK_TRACE_LINE = Pattern.compile("^\\s+at\\s\\S+\\s\\(\\w+\\.\\w+:\\d+\\)$");
        private static final Pattern STACK_TRACE_LINE = Pattern.compile("^\\s+at\\s\\S+\\s\\(\\w+\\.\\w+:\\d+\\)$");
        private static final Pattern STACK_TRACE_LINE_2 = Pattern.compile("^\\s+at\\s\\S+\\s\\(Native Method\\)$");

        int state() {
            return state;
        }

        @Override
        public void sequenceStart(String string, OutputVisitor ov) {
            LOG.log(Level.FINEST, "Sequence start {0}", string);
            state = STATE_READY;
        }

        @Override
        public void sequenceEnd(String string, OutputVisitor ov) {
            LOG.log(Level.FINEST, "Sequence end {0}", string);
            state = STATE_READY;
        }

        @Override
        public void sequenceFail(String string, OutputVisitor ov) {
            LOG.log(Level.FINEST, "Sequence fail {0}", string);
            state = STATE_READY;
        }

        @Override
        public String[] getRegisteredOutputSequences() {
            return SEQS;
        }

        private ErrInfo parseForAntlrError(String line, OutputVisitor ov) {
            ErrInfo info = ErrInfo.parse(line);
            if (info != null) {
                if (info.isExceptionMessage) {
                    state = STATE_ANTICIPATING_EXCEPTION_TYPE;
                    LOG.log(Level.FINE, "Processed err line to {0}, is exception message; new state 2", info);
                    ov.skipLine();
                } else {
                    LOG.log(Level.FINE, "Processed err line to {0}, attach listener; new state 1", info);
                    state = STATE_RECEIVED_LINE;
                    attachToLine(info, ov);
                }

            }
            return info;
        }

        @Override
        public void processLine(String line, OutputVisitor ov) {
            boolean gotExpectedTransition = doProcessLine(line, ov);
            if (!gotExpectedTransition) {
                ErrInfo info = parseForAntlrError(line, ov);
                if (info == null) {
                    state = STATE_READY;
                }
            }
        }

        private boolean exceptionType(String line, OutputVisitor ov) {
            Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
            if (exceptionMatcher.matches()) {
                LOG.log(Level.FINEST, "Antipated and matched exception type {0}; to state 3", line);
                state = STATE_IN_STACK_TRACE;
                ov.skipLine();
                return true;
            }
            return false;
        }

        public boolean doProcessLine(String line, OutputVisitor ov) {
            ErrInfo info = null;
            switch (state) {
                case STATE_READY:
                case STATE_RECEIVED_LINE:
                    info = parseForAntlrError(line, ov);
                    if (info == null) {
                        if (!exceptionType(line, ov)) {
                            state = STATE_READY;
                            LOG.log(Level.FINEST, "Not recognized: {0}", line);
                        }
                    }
                    return true;
                case STATE_ANTICIPATING_EXCEPTION_TYPE:
                    return exceptionType(line, ov);
                case STATE_IN_STACK_TRACE:
                    Matcher traceMatcher = STACK_TRACE_LINE.matcher(line);
                    if (!traceMatcher.matches()) {
                        traceMatcher = STACK_TRACE_LINE_2.matcher(line);
                    }
                    if (traceMatcher.matches()) {
                        if (LOG.isLoggable(Level.FINEST)) {
                            LOG.log(Level.FINEST, "Antipated and matched stack trace line {0}; stay in state 3", line.trim());
                        }
                        ov.skipLine();
                        return true;
                    } else {
                        return false;
                    }
                default:
                    LOG.log(Level.WARNING, "Unexpected state {0}, to state 0", state);
                    return false;
            }
        }

        private void attachToLine(ErrInfo info, OutputVisitor ov) {
            ov.setOutputType(IOColors.OutputType.HYPERLINK_IMPORTANT);
            ov.setOutputListener(new Listener(info));
        }

        static class Listener implements OutputListener {

            final ErrInfo info;

            public Listener(ErrInfo info) {
                this.info = info;
            }

            @Override
            public void outputLineAction(OutputEvent oe) {
                Path path = Paths.get(info.file);
                LOG.log(Level.FINE, "Attempt to open {0} for output click", path);
                if (Files.exists(path)) {
                    FileObject fo = FileUtil.toFileObject(path.toFile());
                    try {
                        DataObject dob = DataObject.find(fo);
                        EditorCookie cookie = dob.getLookup().lookup(EditorCookie.class);
                        if (cookie != null) {
                            try {
                                if (info.line >= 0 && info.lineOffset >= 0) {
                                    cookie.getLineSet().getCurrent(info.line).show(
                                            Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                                } else {
                                    cookie.getLineSet().getCurrent(0).show(
                                            Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                                }
                            } catch (IndexOutOfBoundsException ex) {
                                cookie.open();
                            }
                        } else {
                            Logger.getLogger(AntlrOutputProcessorFactory.class.getName()).log(
                                    Level.WARNING, "No cookie found for dataobject {0}", dob); // NOI18N
                        }
                    } catch (DataObjectNotFoundException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }

            @Override
            public void outputLineSelected(OutputEvent oe) {
                // do nothing
            }

            @Override
            public void outputLineCleared(OutputEvent oe) {
                // do nothing
            }
        }

        static class ErrInfo {

            private static boolean win = Utilities.isWindows();
            private static final String WINDOWS_FILE_PATTERN = "^(?:[a-zA-Z]\\:|\\\\\\\\[\\w\\.]+\\\\[\\w.$]+)\\\\(?:[\\w]+\\\\)*\\w([\\w.])+";
            private static final String UNIX_FILE_PATTERN = "^(\\/\\S.*?)+\\S.*?\\.g4";
            private static final String FILE_PATTERN = win ? WINDOWS_FILE_PATTERN : UNIX_FILE_PATTERN;
            private static final String ERR_MATCH_1 = "\\s\\[(\\-?\\d+):(\\-?\\d+)\\]:\\s(\\w.*)$";
            private static final String ERR_MATCH_2 = ":(\\-?\\d+):(\\-?\\d+):\\s(\\w.*)$";
            private static final String ERR_MATCH_3 = ":::\\s(\\w.*)$";

            private static final Pattern FM1 = Pattern.compile(FILE_PATTERN + ERR_MATCH_1);
            private static final Pattern FM2 = Pattern.compile(FILE_PATTERN + ERR_MATCH_2);
            private static final Pattern FM3 = Pattern.compile(FILE_PATTERN + ERR_MATCH_3);

            public final String file;
            public final int line;
            public final int lineOffset;
            public final String message;
            public final boolean isExceptionMessage;

            public ErrInfo(String file, int line, int lineOffset, boolean matchPattern1, String message) {
                this.file = file;
                this.line = line;
                this.lineOffset = lineOffset;
                this.message = message;
                this.isExceptionMessage = !matchPattern1;
            }

            public String toString() {
                return file + " " + "[" + line + ":" + lineOffset + "] " + message;
            }

            static ErrInfo parse(String string) {
                // Pending:  We are assuming error format == gnu, which is what
                // the add antlr support action will generate.  Might be nice either to
                // support the other error output formats antlr supports, or offer to
                // fiddle with the pom file
                Matcher m = pattern1().matcher(string);
                boolean isP1 = true;
                boolean isP3 = false;
                if (!m.find()) {
                    m = pattern2().matcher(string);
                    if (!m.find()) {
                        m = pattern3().matcher(string);
                        if (!m.find()) {
                            return null;
                        }
                        isP3 = true;
                        isP1 = false;
                    } else {
                        isP1 = false;
                    }
                }
                if (isP3) {
                    // error about unexpected EOF tag will have line/position
                    // -1:-1, and the exception form will omit line position info
                    // entirely
                    int msgStart = m.start(2);
                    String path = string.substring(0, msgStart - 4);
                    String msg = string.substring(msgStart);
                    return new ErrInfo(path, 0, 0, false, msg);
                }
                int gc = m.groupCount();
                String positionInLine = m.group(gc - 1);
                String line = m.group(gc - 2);
                int lineNumberStart = m.start(gc - 2);
                String path = string.substring(0, lineNumberStart - (isP1 ? 2 : 1));
                String message = m.group(gc);
                // just use start of file in case no line info
                return new ErrInfo(path, Math.max(0, Integer.parseInt(line)), Math.max(0, Integer.parseInt(positionInLine)), isP1, message);
            }

            // For unit tests, allow windows
            private static Pattern pattern1() {
                switch (mode) {
                    case DEFAULT:
                        return FM1;
                    case WINDOWS:
                        return win ? FM1 : Pattern.compile(WINDOWS_FILE_PATTERN + ERR_MATCH_1);
                    case UNIX:
                        return !win ? FM1 : Pattern.compile(UNIX_FILE_PATTERN + ERR_MATCH_1);
                    default:
                        throw new AssertionError(mode);
                }
            }

            private static Pattern pattern2() {
                switch (mode) {
                    case DEFAULT:
                        return FM2;
                    case WINDOWS:
                        return win ? FM2 : Pattern.compile(WINDOWS_FILE_PATTERN + ERR_MATCH_2);
                    case UNIX:
                        return !win ? FM2 : Pattern.compile(UNIX_FILE_PATTERN + ERR_MATCH_2);
                    default:
                        throw new AssertionError(mode);
                }
            }

            private static Pattern pattern3() {
                switch (mode) {
                    case DEFAULT:
                        return FM3;
                    case WINDOWS:
                        return win ? FM3 : Pattern.compile(WINDOWS_FILE_PATTERN + ERR_MATCH_2);
                    case UNIX:
                        return !win ? FM3 : Pattern.compile(UNIX_FILE_PATTERN + ERR_MATCH_3);
                    default:
                        throw new AssertionError(mode);
                }
            }

            private static ParsingMode mode = ParsingMode.DEFAULT;

            static void setParsingMode(ParsingMode mode) {
                ErrInfo.mode = mode;
            }

            public enum ParsingMode {
                DEFAULT,
                WINDOWS,
                UNIX;
            }
        }
    }
}
