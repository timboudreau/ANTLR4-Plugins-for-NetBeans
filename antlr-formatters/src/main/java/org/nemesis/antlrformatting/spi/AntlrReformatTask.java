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
