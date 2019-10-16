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
package org.nemesis.antlr.v4.netbeans.v8.generic.parsing;

import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.csl.api.Error.Badging;
import org.netbeans.modules.csl.api.Severity;

import org.openide.filesystems.FileObject;

/**
 * It is mandatory to implement Error.Badging rather than Error because, we want 
 * error badges to be shown in 'Projects' tab.
 * 
 * @author Frédéric Yvon Vinet
 */
public class ParsingError implements Badging {
    protected FileObject fileObject;
    protected Severity   severity;
    protected String     key;
    protected int        parsingErrorStartOffset; // in the root char stream
    protected int        parsingErrorEndOffset; // in the root char stream
    protected String     displayName;
    protected String     description;
    
    public ParsingError
        (GrammarSource<?> src,
         Severity   severity               ,
         String     key                    ,
         int        parsingErrorStartOffset,
         int        parsingErrorEndOffset  ,
         String     displayName            ,
         String     description            ) {
        src.lookup(FileObject.class, fo -> {
            this.fileObject = fo;
        });
        this.severity = severity;
        this.key = key;
        this.parsingErrorStartOffset = parsingErrorStartOffset;
        this.parsingErrorEndOffset = parsingErrorEndOffset;
        this.displayName = displayName;
        this.description = description;
    }

    public ParsingError
        (FileObject fo,
         Severity   severity               ,
         String     key                    ,
         int        parsingErrorStartOffset,
         int        parsingErrorEndOffset  ,
         String     displayName            ,
         String     description            ) {
        this.fileObject = fo;
        this.severity = severity;
        this.key = key;
        this.parsingErrorStartOffset = parsingErrorStartOffset;
        this.parsingErrorEndOffset = parsingErrorEndOffset;
        this.displayName = displayName;
        this.description = description;
    }

 /**
  * @return Provide a short user-visible (and therefore localized) description
  * of this error
  */
    @Override
    public String getDisplayName() {
        return displayName;
    }

 /**
  * @return Provide a full sentence description of this item, suitable for 
  * display in a tooltip for example
  */
    @Override
    public String getDescription() {
        return description;
    }

 /**
  * @return Return a unique id/key for this error, such as 
  * "compiler.err.abstract.cant.be.instantiated". This key is used for error
  * hints providers.
  */
    @Override
    public String getKey() {
        return key;
    }

  /**
   * @return Get the file object associated with this error, if any
   */
    @Override
    public FileObject getFile() {
        return fileObject;
    }

 /**
  * @return Get the position of the error in the parsing input source (in other
  * words, this is the AST-based offset and may need translation to obtain the 
  * document offset in documents with an embedding model).
  */
    @Override
    public int getStartPosition() {
        return parsingErrorStartOffset;
    }

 /**
  * Get the end offset of the error in the parsing input source (in other words,
  * this is the AST-based offset and may need translation to obtain the document
  * offset in documents with an embedding model).
  * 
  * @return The end position, or -1 if unknown.
  */
    @Override
    public int getEndPosition() {
        return parsingErrorEndOffset;
    }

 /**
  * Defines the way how an error annotation for this error behaves in the 
  * editor.
  * 
  * @return true if the error annotation should span over the whole line, false 
  * if the annotation is restricted exactly by the range defined by 
  * getStart/EndPostion().
  */
    @Override
    public boolean isLineError() {
        return false;
    }

 /**
  * @return Get the severity of this error
  */
    @Override
    public Severity getSeverity() {
        return severity;
    }

 /**
  * @return Return optional parameters for this message. The parameters may 
  * provide the specific unknown symbol name for an unknown symbol error, etc.
  */
    @Override
    public Object[] getParameters() {
        return new Object[0];
    }

    
    @Override
    public boolean showExplorerBadge() {
        return true;
    }
    
    public boolean equals(ParsingError err) {
        boolean answer = true;
        if (!fileObject.equals(err.fileObject))
            answer = false;
        if (!severity.equals(err.severity))
            answer = false;
        if (!severity.equals(err.severity))
            answer = false;
        if (!key.equals(err.key))
            answer = false;
        if (parsingErrorStartOffset != err.parsingErrorStartOffset)
            answer = false;
        if (parsingErrorEndOffset != err.parsingErrorEndOffset)
            answer = false;
        if (!displayName.equals(err.displayName))
            answer = false;
        if (!description.equals(err.description))
            answer = false;
        return answer;
    }
}