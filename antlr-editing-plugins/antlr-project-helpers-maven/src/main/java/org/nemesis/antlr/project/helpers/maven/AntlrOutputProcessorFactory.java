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
        private boolean win = Utilities.isWindows();
        private static final String WINDOWS_FILE_PATTERN = "^(?:[a-zA-Z]\\:|\\\\\\\\[\\w\\.]+\\\\[\\w.$]+)\\\\(?:[\\w]+\\\\)*\\w([\\w.])+";
//        private static final String UNIX_FILE_PATTERN = "^\\([^\\0 !$`&*()+]\\|\\\\\\(\\ |\\!|\\$|\\`|\\&|\\*|\\(|\\)|\\+\\)\\)\\+";
//        private static final String UNIX_FILE_PATTERN = "^\\([^!$`&*()+]\\|\\\\\\(\\ |\\!|\\$|\\`|\\&|\\*|\\(|\\)|\\+\\)\\)\\+";
        private static final String UNIX_FILE_PATTERN = "^(\\/\\S.*?)+\\S.*?\\.g4";
        private static final String FILE_PATTERN = Utilities.isWindows() ? WINDOWS_FILE_PATTERN : UNIX_FILE_PATTERN;
        private static final String ERR_MATCH_1 = "\\s\\[(\\d+):(\\d+)\\]:\\s(\\w.*)$";
        private static final String ERR_MATCH_2 = ":(\\d+):(\\d+):\\s(\\w.*)$";

        private static final String FILE_MATCH_1 = FILE_PATTERN + ERR_MATCH_1;
        private static final String FILE_MATCH_2 = FILE_PATTERN + ERR_MATCH_2;

        private static final Pattern FM1 = Pattern.compile(FILE_MATCH_1);
        private static final Pattern FM2 = Pattern.compile(FILE_MATCH_2);

        @Override
        public String[] getRegisteredOutputSequences() {
            return SEQS;
        }

        @Override
        public void processLine(String line, OutputVisitor ov) {
            ErrInfo info = ErrInfo.parse(line);
            if (info != null) {
                attachToLine(info, ov);
            }
        }

        private void attachToLine(ErrInfo info, OutputVisitor ov) {
            ov.setOutputType(IOColors.OutputType.ERROR);
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

            private final String file;
            private final int line;
            private final int lineOffset;
            private final String message;

            public ErrInfo(String file, int line, int lineOffset, boolean matchPattern1, String message) {
                this.file = file;
                this.line = line;
                this.lineOffset = lineOffset;
                this.message = message;
            }

            public String toString() {
                return file + " " + "[" + line + ":" + lineOffset + "] " + message;
            }

            static ErrInfo parse(String string) {
                Matcher m = FM1.matcher(string);
                boolean isP1 = true;
                if (!m.find()) {
                    m = FM2.matcher(string);
                    if (!m.find()) {
                        return null;
                    }
                    isP1 = false;
                }
                int gc = m.groupCount();
                String positionInLine = m.group(gc - 1);
                String line = m.group(gc - 2);
                int lineNumberStart = m.start(gc - 1);
                String path = string.substring(0, lineNumberStart - 1);
                String message = m.group(gc);
                return new ErrInfo(path, Integer.parseInt(line), Integer.parseInt(positionInLine), isP1, message);
            }
        }

        @Override
        public void sequenceStart(String string, OutputVisitor ov) {
        }

        @Override
        public void sequenceEnd(String string, OutputVisitor ov) {
        }

        @Override
        public void sequenceFail(String string, OutputVisitor ov) {
        }

    }
}
