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

    @Override
    public Set<? extends OutputProcessor> createProcessorsSet(Project prjct) {
        if (prjct != null && prjct.getLookup().lookup(HideAntlrSourceDirsFromMavenOtherSources.class) == null) {
            return Collections.emptySet();
        }
        return Collections.singleton(new AntlrLineProcessor());
    }

    static class AntlrLineProcessor implements OutputProcessor {

        private static final String[] SEQS = new String[]{"mojo-execute#antlr4:antlr4"};
        private static final int STATE_READY = 0;
        private static final int STATE_RECEIVED_LINE = 1;
        private static final int STATE_ANTICIPATING_EXCEPTION_TYPE = 2;
        private static final int STATE_IN_STACK_TRACE = 3;
        private int state = STATE_READY;
        private static final Pattern EXCEPTION_PATTERN = Pattern.compile("^org\\.antlr\\.\\S+Exception");
        private static final Pattern STACK_TRACE_LINE = Pattern.compile("^\\s+at \\S+$");

        @Override
        public void sequenceStart(String string, OutputVisitor ov) {
            state = STATE_READY;
        }

        @Override
        public void sequenceEnd(String string, OutputVisitor ov) {
            state = STATE_READY;
        }

        @Override
        public void sequenceFail(String string, OutputVisitor ov) {
            state = STATE_READY;
        }

        @Override
        public String[] getRegisteredOutputSequences() {
            return SEQS;
        }

        @Override
        public void processLine(String line, OutputVisitor ov) {
            switch (state) {
                case STATE_READY:
                case STATE_RECEIVED_LINE:
                    ErrInfo info = ErrInfo.parse(line);
                    if (info != null) {
                        if (info.isExceptionMessage) {
                            state = 2;
                            ov.skipLine();
                        } else {
                            state = STATE_RECEIVED_LINE;
                            attachToLine(info, ov);
                        }
                    } else {
                        state = STATE_READY;
                    }
                    break;
                case STATE_ANTICIPATING_EXCEPTION_TYPE:
                    Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
                    if (exceptionMatcher.matches()) {
                        state = STATE_IN_STACK_TRACE;
                        ov.skipLine();
                    } else {
                        state = STATE_READY;
                    }
                    break;
                case STATE_IN_STACK_TRACE:
                    Matcher traceMatcher = STACK_TRACE_LINE.matcher(line);
                    if (traceMatcher.matches()) {
                        ov.skipLine();
                    } else {
                        state = STATE_READY;
                    }
                    break;
                default:
                    state = STATE_READY;
                    break;
            }
        }

        private void attachToLine(ErrInfo info, OutputVisitor ov) {
            ov.setOutputType(IOColors.OutputType.HYPERLINK_IMPORTANT);
            ov.setOutputListener(new Listener(info));
        }

        static class Listener implements OutputListener {

            private final ErrInfo info;

            public Listener(ErrInfo info) {
                this.info = info;
            }

            @Override
            public void outputLineAction(OutputEvent oe) {
                Path path = Paths.get(info.file);
                if (Files.exists(path)) {
                    FileObject fo = FileUtil.toFileObject(path.toFile());
                    try {
                        DataObject dob = DataObject.find(fo);
                        EditorCookie cookie = dob.getLookup().lookup(EditorCookie.class);
                        if (cookie != null) {
                            try {
                                cookie.getLineSet().getCurrent(info.line).show(
                                        Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
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
            private static final String ERR_MATCH_1 = "\\s\\[(\\d+):(\\d+)\\]:\\s(\\w.*)$";
            private static final String ERR_MATCH_2 = ":(\\d+):(\\d+):\\s(\\w.*)$";

            private static final Pattern FM1 = Pattern.compile(FILE_PATTERN + ERR_MATCH_1);
            private static final Pattern FM2 = Pattern.compile(FILE_PATTERN + ERR_MATCH_2);

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
                if (!m.find()) {
                    m = pattern2().matcher(string);
                    if (!m.find()) {
                        System.out.println("no match for '" + pattern1().pattern() + "' or '" + pattern2().pattern() + "'");
                        return null;
                    }
                    isP1 = false;
                }
                int gc = m.groupCount();
                String positionInLine = m.group(gc - 1);
                String line = m.group(gc - 2);
                int lineNumberStart = m.start(gc - 2);
                String path = string.substring(0, lineNumberStart - (isP1 ? 2 : 1));
                String message = m.group(gc);
                return new ErrInfo(path, Integer.parseInt(line), Integer.parseInt(positionInLine), isP1, message);
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
