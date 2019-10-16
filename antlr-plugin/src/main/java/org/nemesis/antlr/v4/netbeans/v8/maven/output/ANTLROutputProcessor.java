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
package org.nemesis.antlr.v4.netbeans.v8.maven.output;

import java.io.File;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.netbeans.modules.maven.api.output.OutputProcessor;
import org.netbeans.modules.maven.api.output.OutputVisitor;

import org.openide.filesystems.FileUtil;

/**
 * 
 * @author Frédéric Yvon Vinet
 */
public class ANTLROutputProcessor implements OutputProcessor {
    private static final String[] ANTLR_GOALS = new String[] {
        "mojo-execute#antlr4:antlr4", //NOI18N
    };
    /** @see org.codehaus.plexus.compiler.javac.JavacCompiler#compile */
    private static final String GROUP_GRAMMAR_PATH = "grammar";
    private static final String GROUP_LINE_NR = "linenr";
    private static final String GROUP_DESCRIPTION = "description";
    private static final String GROUP_DRIVE_NAME = "drive";
    private static final Pattern failPattern  = Pattern.compile
            ("(?<" + GROUP_GRAMMAR_PATH + ">[^\\s]+\\.g4)" + // named capturing group
             " " +
             "\\[" +
                 "(?<" + GROUP_LINE_NR + ">[0-9]+)"+ // named capturing group (line number)
                 "\\:" +
                 "([0-9]*)" + // non-named capturing group (column number)
             "\\]" +
             "\\: " +
             "[^\\:]+" + // 
             "\\:" +
             "(?<" + GROUP_DESCRIPTION + ">.*)", // NOI18N
             Pattern.DOTALL             );
    private static final Pattern windowsDriveInfoPattern = Pattern.compile
           ("(?:\\[INFO\\] )?" + // non-capturing group
            "Compiling " +
            "\\d+" + // 1 or n digits
            " source files? to " +
            "(?<" + GROUP_DRIVE_NAME + ">[A-Za-z]:)" + // named capturing group
            "\\\\.+");
    
    public ANTLROutputProcessor() {
//        System.out.println("ANTLROutputProcessor.ANTLROutputProcessor()");
    }

    @Override
    public String[] getRegisteredOutputSequences() {
        return ANTLR_GOALS;
    }

    
  /**
   * Called for each line in a Maven registered output sequence
   * @param line
   * @param outputVisitor 
   */
    @Override
    public void processLine(String line, OutputVisitor outputVisitor) {
//        System.out.println("ANTLROutputProcessor.processLine(String,OutputVisitor)");
        Matcher match = failPattern.matcher(line);
        if (match.matches()) {
            String grammarFilePath = match.group(GROUP_GRAMMAR_PATH);
            File grammarFile = new File(grammarFilePath);
            String lineNum = match.group(GROUP_LINE_NR);
            String description = match.group(GROUP_DESCRIPTION);
            int lineNumber;
            try {
                lineNumber = Integer.parseInt(lineNum);
            } catch (NumberFormatException exc) {
                lineNumber = -1;
            }
            outputVisitor.setOutputListener
                     (new ANTLRCodeGenerationListener
                            (grammarFile, lineNumber, description),
                      !description.contains("[deprecation]") ); //NOI18N
            FileUtil.refreshFor(grammarFile);
            StringBuilder newLine = new StringBuilder();
            newLine.append(grammarFilePath);
            newLine.append(":");
            newLine.append(lineNum);
            newLine.append(": ");
            newLine.append("error");
            newLine.append(":");
            newLine.append(description);
         // We update the output visitor with new line
            outputVisitor.setLine(newLine.toString());
        }    
    }

  /**
   * Called at the begining of a Maven registered output sequence
   * @param string
   * @param ov 
   */
    @Override
    public void sequenceStart(String string, OutputVisitor ov) {
    }

  /**
   * Called at the end of a Maven registered output sequence
   * @param string
   * @param ov 
   */
    @Override
    public void sequenceEnd(String string, OutputVisitor ov) {
    }

  /**
   * Called if a Maven registered output sequence fails
   * @param string
   * @param ov 
   */
    @Override
    public void sequenceFail(String string, OutputVisitor ov) {
    }
}