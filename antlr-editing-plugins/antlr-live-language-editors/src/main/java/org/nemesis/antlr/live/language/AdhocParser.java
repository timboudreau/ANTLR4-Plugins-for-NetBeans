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
    private int parses;

    AdhocParser(String mimeType) {
        this.mimeType = mimeType;
        LIVE_PARSERS.add(this);
    }

    static void forceInvalidate(FileObject fo) {
        System.out.println("AdhocParser.forceInvalidate");
        Set<Task> all = new HashSet<>();
        String mime = fo.getMIMEType();
        for (Map.Entry<Task, AdhocParserResult> e : RESULT_FOR_TASK.entrySet()) {
            AdhocParserResult res = e.getValue();
            if (res != null) {
                if (fo.equals(res.getSnapshot().getSource().getFileObject())) {
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
        return "AdhocParser(" + mimeType + ")";
    }

    boolean isMimeType(String type) {
        return mimeType.equals(type);
    }

    private int parsesAtLastFire = -1;

    void updated() {
        LOG.log(Level.FINE, "Fire parser change from {0}", this);
//        last = null;
//        RESULT_FOR_TASK.clear();
        if (parsesAtLastFire != parses) {
            System.out.println("PARSER FIRE CHANGE " + AdhocMimeTypes.loggableMimeType(mimeType));
            Thread.dumpStack();
            parsesAtLastFire = parses;
            supp.fireChange();
        }
    }

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        if (!snapshot.getMimeType().equals(mimeType)) {
            String msg = "AdhocParser asked to parse snapshot for wrong mime type.\n Pars Mime: " + mimeType
                    + "\nSnap Mime: " + snapshot.getMimeType() + "\n" + " for task "
                    + task + "\nEvent source:" + event.getModifiedSource();
            LOG.log(Level.SEVERE, msg, new Exception(msg));
        }
        if (!snapshot.getSource().getMimeType().equals(mimeType)) {
            String msg = "Snapshot mime type and snapshot source mime type do not match: "
                    + "\nSnp  mime type: " + snapshot.getMimeType()
                    + "\nSrc  mime type: " + snapshot.getSource().getMimeType()
                    + "\nThis mime type: " + mimeType
                    + "\nAdhocParser asked to parse snapshot for wrong mime type.\n Pars Mime: " + mimeType
                    + "\nSnap Mime: " + snapshot.getMimeType() + "\n" + " for task "
                    + task + "\nEvent source:" + event.getModifiedSource();
            LOG.log(Level.SEVERE, msg, new Exception(msg));
        }

        parses++;
        try {
            Debug.runThrowing(this, "adhoc-parser " + AdhocMimeTypes.loggableMimeType(snapshot.getMimeType()), () -> {
                return snapshot.getSource().getDocument(false) + "\n\nTEXT:\n"
                        + snapshot.getText() + "\n\n"
                        + snapshot.getSource().getFileObject();
            }, () -> {
                EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
                AntlrProxies.ParseTreeProxy res = parser.parse(snapshot.getText());
                if (!res.mimeType().equals(mimeType)) {
                    String msg = "Bad ParseTreeProxy mime type from EmbeddedAntlrParser\n"
                            + "Exp: " + mimeType + "\nGot: " + res.mimeType() + "\n"
                            + "From: " + parser;
                    LOG.log(Level.SEVERE, msg, new Exception(msg));
                }
                GrammarRunResult<?> gbrg = parser.lastResult();
                AdhocParserResult result = new AdhocParserResult(snapshot, res, inv);
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
            if (last == t) {
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