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
package org.nemesis.antlrformatting.spi;

import javax.swing.text.BadLocationException;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.editor.indent.spi.ExtraLock;
import org.netbeans.modules.editor.indent.spi.ReformatTask;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrReformatTask<C, StateEnum extends Enum<StateEnum>> implements ReformatTask {

    private final DocumentReformatRunner<C, StateEnum> fmt;
    private final Context context;

    AntlrReformatTask(DocumentReformatRunner<C, StateEnum> fmt, Context context) {
        this.fmt = fmt;
        this.context = context;
    }

    @Override
    public void reformat() throws BadLocationException {
        fmt.reformat(context);
    }

    @Override
    public ExtraLock reformatLock() {
        return null;
    }

}
