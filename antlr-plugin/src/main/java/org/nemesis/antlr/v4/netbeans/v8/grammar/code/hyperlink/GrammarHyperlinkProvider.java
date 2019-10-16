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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink;

import java.io.File;

import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.source.api.ParsingBag;

import org.netbeans.api.editor.mimelookup.MimeRegistration;

import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProvider;

import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

import org.openide.text.Line;
import org.openide.text.Line.Set;
import org.openide.text.NbDocument;

import org.openide.util.Lookup;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = HyperlinkProvider.class)
public class GrammarHyperlinkProvider implements HyperlinkProvider {
    public GrammarHyperlinkProvider() {
    }
    
    
    @Override
    public boolean isHyperlinkPoint(Document doc, int offset) {
        boolean answer = false;
//        Hyperlinks links = (Hyperlinks) doc.getProperty(Hyperlinks.class);
        Hyperlinks links = ParsingBag.get(doc, ANTLR_MIME_TYPE).get(Hyperlinks.class);
        if (links != null) {
//            System.out.println("Links exists");
            ArrayList<Hyperlink> possibleLinks = links.getLinks(offset);
            if (possibleLinks != null) {
//                System.out.println("Possible links exist");
                answer = true;
            }
        }
        return answer;
    }
    
    
    @Override
    public int[] getHyperlinkSpan(Document doc, int offset) {
        int[] answer = null;
        Hyperlinks links = (Hyperlinks) doc.getProperty(Hyperlinks.class);
        if (links != null) {
            ArrayList<Hyperlink> possibleLinks = links.getLinks(offset);
            if (possibleLinks != null) {
//                System.out.println("Possible links exist");
                Iterator<Hyperlink> it = possibleLinks.iterator();
             // All possible links share the same span so we only recover 
             // first one
                if (it.hasNext()) {
                    Hyperlink hyperlink = it.next();
                    int startOffset = hyperlink.getStart();
                    int endOffset   = hyperlink.getEnd();
//                    System.out.println("startOffset=" + startOffset);
//                    System.out.println("endOffset=" + endOffset);
                    answer = new int[]{startOffset, endOffset};
                }
            }
        }
        return answer;
    }
    
    
    @Override
    public void performClickAction(Document doc, int offset) {
        Hyperlinks links = (Hyperlinks) doc.getProperty(Hyperlinks.class);
        if (links != null) {
            ArrayList<Hyperlink> possibleLinks = links.getLinks(offset);
//            System.out.println("hyperlink instance number=" + possibleLinks.size());
            Iterator<Hyperlink> it = possibleLinks.iterator();
            boolean found = false;
            Hyperlink hyperlink = null;
            while (it.hasNext() && !found) {
                hyperlink = it.next();
                File targetFile = hyperlink.getTargetFile();
//                System.out.println("file=" + targetFile.getPath());
                if (targetFile.exists()) {
                    found = true;
                    
                 // It is possible that the target file is the currently 
                 // open file, in such a case it is useless to open it but  
                 // it does not lead to an error so we do not manage that
                 // case
                    FileObject targetFileObject = FileUtil.toFileObject
                                                                   (targetFile);
                 // The file exists so its DataObject exists as well but
                 // we have to manage the DataObjectNotFoundException even 
                 // if it is impossible it raises (except in case of 
                 // NetBeans bug)
                    try {
                        DataObject grammarDataObject = DataObject.find
                                                             (targetFileObject);
                        EditorCookie ec = grammarDataObject.getLookup().lookup(EditorCookie.class);
                        Lookup grammarLookup = grammarDataObject.getLookup();
                        if (ec != null) {
                            ec.open();
                            StyledDocument sDoc = ec.getDocument();
                            LineCookie lc = grammarLookup.lookup(LineCookie.class);
                            if (lc != null) {
                                int targetOffset = hyperlink.getTargetOffset();
/*
                                System.out.println
                                              ("GrammarHyperlinkProvider : " +
                                               "targetOffset=" + targetOffset);
*/
                                int lineIndex = NbDocument.findLineNumber(sDoc, targetOffset);
//                                int lineIndex = LineDocumentUtils.getLineIndex
//                                                             (ld, targetOffset);
                                int lineStartOffset = NbDocument.findLineOffset(sDoc, lineIndex);
//                                int lineStartOffset = LineDocumentUtils.getLineStart
//                                                             (ld, targetOffset);
                                int columnIndex = targetOffset - lineStartOffset;
/*
                                System.out.println("GrammarHyperlinkProvider : " +
                                               "lineIndex=" + lineIndex +
                                               " lineStartOffset=" + lineStartOffset +
                                               " columnIndex=" + columnIndex);
*/
                                Set set = lc.getLineSet();
                                Line line = set.getOriginal(lineIndex);
                                line.show(Line.ShowOpenType.OPEN        ,
                                          Line.ShowVisibilityType.FOCUS ,
                                          columnIndex                   );
                            }
                        }
                        /*
                        EditCookie editCookie = grammarLookup.lookup
                                                             (EditCookie.class);
                        if (editCookie != null)
                            editCookie.edit();
                        */
                    } catch (DataObjectNotFoundException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
}