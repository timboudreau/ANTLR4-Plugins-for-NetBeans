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

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Hyperlinks class regroups all hyperlinks of a given document.
 * Hyperlinks instance are created by the parser class NBANTLRv4Parser in
 * package org.nemesis.antlr.v4.netbeans.v8.grammar.hyperlink.parser.
 * Then, hyperlinks are used by GrammarHyperlinkProvider in order to decide
 * if what is beneath mouse cursor is an hyperlink or not (method 
 * isHyperlinkPoint(Document,int)) and in order to activate an hyperlink
 * (method performClickAction(Document, int)).
 * 
 * @author Frédéric Yvon Vinet
 */
public class Hyperlinks {
    private final HashMap<Integer, ArrayList<Hyperlink>> links;
    
    public Hyperlinks() {
        this.links = new HashMap<>();
    }
    
    public void addLink(Hyperlink hyperlink) {
        int start = hyperlink.getStart();
        int end = hyperlink.getEnd();
     // For each position between start and end, we define our link as the target
        for (int i = start; i <= end; i++) {
            ArrayList<Hyperlink> list = links.get(i);
            if (list == null) {
                list = new ArrayList<>();
                links.put(i, list);
            }
            list.add(hyperlink);
        }
    }
    
    
    public void removeLink(Hyperlink hyperlink) {
        int start = hyperlink.getStart();
        int end = hyperlink.getEnd();
     // For each position between start and end, we define our link as the target
        for (int i = start; i <= end; i++) {
            ArrayList<Hyperlink> list = links.get(i);
            if (list != null)
                list.remove(hyperlink);
        }
    }
    
    
  /**
   * Gives the possible hyperlinks asociated to the given offset.
   * Only one may be the right one. For each link, you test existence of the
   * corresponding file and if it exists, you have found the target.
   * 
   * @param offset: mouse position in open document
   * @return the hyperlink associated with mouse position defined by offset
   */
    public ArrayList<Hyperlink> getLinks(int offset) {
        return links.get(offset);
    }
}
