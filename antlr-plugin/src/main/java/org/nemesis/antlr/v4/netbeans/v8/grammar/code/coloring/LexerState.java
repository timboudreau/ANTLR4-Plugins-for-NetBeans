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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring;

import org.antlr.v4.runtime.misc.IntegerStack;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class LexerState {
    protected int          _mode;
    protected IntegerStack _modeStack;
    protected int          initialStackedModeNumber;
    
    public IntegerStack getModeStack() {
        return _modeStack;
    }
    
    public int getMode() {
        return _mode;
    }
    
    public int getInitialStackedModeNumber() {
        return initialStackedModeNumber;
    }
    
    
    public LexerState(int          _mode                   ,
                      IntegerStack _modeStack              ,
                      int          initialStackedModeNumber) {
        this._modeStack = _modeStack;
        this._mode = _mode;
        this.initialStackedModeNumber = initialStackedModeNumber;
    }
}