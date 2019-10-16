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

/**
 * An hyperlink points to a given file (it may exist or not).
 * If the given file does not exist then the hyperlink will not work (without
 * error message). As soon as the pointed file is created, the hyperlink will
 * be functional without reparsing the source file.
 * 
 * If the target is in the same file as the source of hyperlink, then there is
 * one and only one hyperlink per source.
 * 
 * But if the target may be in a file that can be in several directory, then
 * there is several hyperlinks per source. The hyperlinkProvider will chosse the
 * first that is pointing to an existing file.
 * .
 * @author Frédéric Yvon Vinet
 */
public class Hyperlink {
    private final int               start;
    private final int               end;
    private final HyperlinkCategory category;
    private final File              targetFile;
    private final String            targetWord;
    private final int               targetOffset;

    
    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public HyperlinkCategory getCategory() {
        return category;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public String getTargetWord() {
        return targetWord;
    }

    public int getTargetOffset() {
        return targetOffset;
    }
    
    
    public Hyperlink(int               start       ,
                     int               end         ,
                     HyperlinkCategory category    ,
                     File              targetFile  ,
                     String            targetWord  ,
                     int               targetOffset) {
        this.start        = start;
        this.end          = end;
        this.category     = category;
        this.targetFile   = targetFile;
        this.targetWord   = targetWord;
        this.targetOffset = targetOffset;
    }
}
