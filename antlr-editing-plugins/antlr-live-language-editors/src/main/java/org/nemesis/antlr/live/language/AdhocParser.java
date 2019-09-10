package org.nemesis.antlr.live.language;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.util.ChangeSupport;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocParser extends Parser {

    static final Logger LOG = Logger.getLogger(AdhocParser.class.getName());
    private static Map<Task, AdhocParserResult> RESULT_FOR_TASK = new WeakHashMap<>();
    private final ChangeSupport supp = new ChangeSupport(this);

    AdhocParserResult last = null;
    private final String mimeType;
    private final EmbeddedAntlrParser parser;

    AdhocParser(String mimeType, EmbeddedAntlrParser parser) {
        this.mimeType = mimeType;
        this.parser = parser;
    }

    @Override
    public String toString() {
        return "AdhocParser(" + mimeType + " " + parser + ")";
    }

    boolean isMimeType(String type) {
        return mimeType.equals(type);
    }

    void updated() {
        LOG.log(Level.FINE, "Fire parser change from {0}", this);
//        last = null;
//        RESULT_FOR_TASK.clear();
        supp.fireChange();
    }

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        try {

            AntlrProxies.ParseTreeProxy res = parser.parse(snapshot.getText().toString());
            GrammarRunResult<?> gbrg = parser.lastResult();
            AdhocParserResult result = new AdhocParserResult(snapshot, res, inv);
            last = result;
            RESULT_FOR_TASK.put(task, result);
            AdhocReparseListeners.reparsed(mimeType, snapshot.getSource(), gbrg, res);
        } catch (Exception ex) {
            throw new ParseException("Exception parsing " + snapshot, ex);
        }
    }

    private final Inv inv = new Inv();

    class Inv implements Consumer<AdhocParserResult> {

        @Override
        public void accept(AdhocParserResult t) {
            if (last == t) {
                last = null;
            }
            Set<Task> toRemove = new HashSet<>();
            for (Map.Entry<Task, AdhocParserResult> e : RESULT_FOR_TASK.entrySet()) {
                if (t == e.getValue()) {
                    toRemove.add(e.getKey());
                }
            }
            for (Task task : toRemove) {
                RESULT_FOR_TASK.remove(task);
            }
        }

    }

    @Override
    public Result getResult(Task task) throws ParseException {
        if ("org.netbeans.modules.java.hints.infrastructure.EmbeddedHintsCollector".equals(task.getClass().getName())) {
            return null;
        }
        Result result = RESULT_FOR_TASK.get(task);
        if (result == null && last != null) {
            result = last;
        }
        return result;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        supp.addChangeListener(cl);
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        supp.removeChangeListener(cl);
    }
}
