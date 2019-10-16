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

import java.awt.Toolkit;

import java.io.File;
import java.io.IOException;

import javax.swing.SwingUtilities;

import org.openide.ErrorManager;

import org.openide.cookies.EditorCookie;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

import org.openide.text.Line;

import org.openide.util.RequestProcessor;

import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRCodeGenerationListener implements OutputListener {
    private static final RequestProcessor RP = new RequestProcessor
                                            (ANTLRCodeGenerationListener.class);
    
                  File   grammarFile;
    private final int    line;
    private final String text;
   
    
    public ANTLRCodeGenerationListener(File grammarFile, int line, String textAnn) {
        this.grammarFile = grammarFile;
        this.text = textAnn;
        this.line = line;
    }

    @Override
    public void outputLineSelected(OutputEvent oe) {
    }

    @Override
    public void outputLineAction(OutputEvent oe) {
//        System.out.println("ANTLRCodeGenerationListener.outputLineAction(OutputEvent)");
        RP.post(new Runnable() {
            @Override
            public void run() {
                FileUtil.refreshFor(grammarFile);
                FileObject file = FileUtil.toFileObject(grammarFile);
                if (file == null) {
                    beep();                   
                    return;
                }
                try {
                    DataObject dob = DataObject.find(file);
                    final EditorCookie ed = dob.getLookup().lookup(EditorCookie.class);
                    if (ed != null && file == dob.getPrimaryFile()) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (line == -1) {
                                        ed.open();
                                    } else {
                                        ed.openDocument();
                                        try {
                                            Line l = ed.getLineSet().getOriginal(line - 1);
                                            if (!l.isDeleted()) {
                                                l.show(Line.ShowOpenType.REUSE, Line.ShowVisibilityType.FOCUS);
                                            }
                                        } catch (IndexOutOfBoundsException ioobe) {
                                            // Probably harmless. Bogus line number.
                                            ed.open();
                                        }
                                    }
                                } catch (IOException ioe) {
                                    ErrorManager.getDefault().notify(ioe);
                                }
                            }
                        });
                    } else {
                        beep();
                    }
                } catch (DataObjectNotFoundException donfe) {
                    ErrorManager.getDefault().notify(donfe);
                }
            }
        });
    }

    @Override
    public void outputLineCleared(OutputEvent oe) {
    }
    
    
    @Override
    public String toString() {
        return "error[" + grammarFile + ":" + line + ":" + text + "]"; // NOI18N
    }
    
    
    private void beep() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Toolkit.getDefaultToolkit().beep();
            }
        });
    }
}