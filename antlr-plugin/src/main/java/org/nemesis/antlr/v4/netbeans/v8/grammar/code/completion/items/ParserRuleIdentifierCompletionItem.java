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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion.items;

import java.awt.Color;

import javax.swing.ImageIcon;

import org.openide.util.ImageUtilities;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ParserRuleIdentifierCompletionItem extends GrammarCompletionItem {
    protected static ImageIcon icon =
        new ImageIcon(ImageUtilities.loadImage
          ("org/nemesis/antlr/v4/netbeans/v8/grammar/code/completion/items" +
           "/parserRuleIdIcon.png"));
    protected static Color color = Color.decode("0x326432");
    
    public ParserRuleIdentifierCompletionItem
            (String  text                       ,
             int     insertionOffset            ,
             int     caretOffset                ,
             boolean extraWhitespaceAlreadyThere) {
        super(text, insertionOffset, caretOffset, extraWhitespaceAlreadyThere);
//        System.out.println("IdentifierCompletionItem:IdentifierCompletionItem(String, int, int) : begin");
//        System.out.println("IdentifierCompletionItem:IdentifierCompletionItem(String, int, int) : end");
    }
    
    
    @Override
    public ImageIcon getIcon() {
        return icon;
    }
    
    
    @Override
    public Color getTextColor(boolean selected) {
        return (selected ? Color.white : color);
    }
    
    
 /**
  * It is the third method to be called when we press Ctrl-Space
  * 
  * @return the item's priority.
  */
    @Override
    public int getSortPriority() {
//        System.out.println("GrammarCompletionItem.getSortPriority() -> int : begin");
//        System.out.println("GrammarCompletionItem.getSortPriority() -> int : end");
        return SortOrder.PUNCTUATION;
    }
    
    
    @Override
    public boolean isExtraWhitespaceRequired() {
        return true;
    }
}