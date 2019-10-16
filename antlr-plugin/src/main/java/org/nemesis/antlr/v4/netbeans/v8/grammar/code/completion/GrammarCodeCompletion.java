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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion;

import javax.swing.text.JTextComponent;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;

import org.netbeans.api.editor.mimelookup.MimeRegistration;

import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;

import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@MimeRegistration(mimeType = ANTLR_MIME_TYPE            ,
                  service = CompletionProvider.class,
                  position = 668                    )
public class GrammarCodeCompletion implements CompletionProvider {
    public GrammarCodeCompletion() {
//        System.out.println("GrammarCodeCompletion:GrammarCodeCompletion() : begin");
//        System.out.println("GrammarCodeCompletion:GrammarCodeCompletion() : end");
    }
    
    
 /**
  * We need to create a task only if the required task type is completion.
  * In all other cases, we do nothing.
  * 
  * @param type
  * @param component
  * @return 
  */
    @Override
    public CompletionTask createTask(int type, JTextComponent component) {
//        System.out.println("GrammarCodeCompletion:createTask(int,JTextComponent) -> CompletionTask : begin");
        CompletionTask answer;
        switch (type) {
            case COMPLETION_QUERY_TYPE:
                answer =  new AsyncCompletionTask
                    (new GrammarCompletionQuery(),
                     component                   );
                break;
            default:
                answer = null;
        }
//        System.out.println("GrammarCodeCompletion:createTask(int, JTextComponent) -> CompletionTask : end");
        return answer;
    }
    
    
  /**
   * This method determines whether the code completion box appears 
   * automatically or not.
   * 
   * @param component
   * @param typedText
   * @return 0 means that the code completion box will never appear unless the
   * user explicitly asks for it.
   */
    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
//        System.out.println("GrammarCodeCompletion:getAutoQueryTypes(JTextComponent, String) -> int: begin");
//        System.out.println("typed text=" + typedText);
//        System.out.println("GrammarCodeCompletion:getAutoQueryTypes(JTextComponent, String) -> int: end");
        return 0;
    }
}