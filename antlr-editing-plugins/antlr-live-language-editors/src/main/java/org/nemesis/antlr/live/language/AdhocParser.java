package org.nemesis.antlr.live.language;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.debug.api.Debug;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
final class AdhocParser extends Parser {

    static final Logger LOG = Logger.getLogger(AdhocParser.class.getName());
    private static Map<Task, AdhocParserResult> RESULT_FOR_TASK = new WeakHashMap<>();
    private final ChangeSupport supp = new ChangeSupport(this);
    static Set<AdhocParser> LIVE_PARSERS = new WeakSet<>();

    AdhocParserResult last = null;
    private final String mimeType;
    private final EmbeddedAntlrParser parser;

    AdhocParser(String mimeType, EmbeddedAntlrParser parser) {
        this.mimeType = mimeType;
        this.parser = parser;
    }

    static void forceInvalidate(FileObject fo) {
        System.out.println("AdhocParser.forceInvalidate");
        Set<Task> all = new HashSet<>();
        String mime = fo.getMIMEType();
        for (Map.Entry<Task, AdhocParserResult> e : RESULT_FOR_TASK.entrySet()) {
            AdhocParserResult res = e.getValue();
            if (res != null) {
                if (fo.equals(res.getSnapshot().getSource().getFileObject()))  {
                    res.invalidate();
                    all.add(e.getKey());
                }
            }
        }
        for (AdhocParser p : LIVE_PARSERS) {
            if (p.isMimeType(mime)) {
                p.updated();
                if (p.last != null) {
                    p.last.invalidate();
                    p.last = null;
                }
            }
        }
        for (Task t : all) {
            RESULT_FOR_TASK.remove(t);
        }
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
        System.out.println("PARSER FIRE CHANGE");
        supp.fireChange();
    }

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        try {
            Debug.runThrowing(this, "adhoc-parser " + AdhocMimeTypes.loggableMimeType(snapshot.getMimeType()), () -> {
                return snapshot.getSource().getDocument(false) + "\n\nTEXT:\n"
                        + snapshot.getText() + "\n\n"
                        + snapshot.getSource().getFileObject();
            }, () -> {
                System.out.println("AdhocParser parse " + task);
                AntlrProxies.ParseTreeProxy res = parser.parse(snapshot.getText().toString());
                GrammarRunResult<?> gbrg = parser.lastResult();
                AdhocParserResult result = new AdhocParserResult(snapshot, res, inv);
                System.out.println("CREATE PARSER RESULT FOR " + task + " result");
                last = result;
                RESULT_FOR_TASK.put(task, result);
                AdhocReparseListeners.reparsed(mimeType, snapshot.getSource(), gbrg, res);
            });
        } catch (Exception ex) {
            throw new ParseException("Exception parsing " + snapshot, ex);
        }
    }

    private final Inv inv = new Inv();

    class Inv implements Consumer<AdhocParserResult> {

        @Override
        public void accept(AdhocParserResult t) {
//            if (true) {
//                return;
//            }
            System.out.println("INV accept " + t + " last " + last);
            if (last == t) {
                System.out.println("clear last");
                last = null;
            }
            Set<Task> toRemove = new HashSet<>();
            for (Map.Entry<Task, AdhocParserResult> e : RESULT_FOR_TASK.entrySet()) {
                if (t == e.getValue()) {
                    System.out.println("remove task entry " + e.getKey());
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
        System.out.println("GET RESULT FOR TASK " + task + " gets " + result);
        if (result == null) {
//            new Exception("Null task result " + task).printStackTrace();
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
