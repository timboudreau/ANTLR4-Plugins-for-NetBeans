package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.Reason.REPARSE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
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

    private static Map<Task, AdhocParserResult> resultForTask = new WeakHashMap<>();
    private AntlrProxies.ParseTreeProxy proxy;
    private GenerateBuildAndRunGrammarResult buildResult;
    private final ChangeSupport supp = new ChangeSupport(this);

    AdhocParser(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy proxy) {
        this.proxy = proxy;
        this.buildResult = buildResult;
    }

    void updated(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy prox) {
        if (prox != this.proxy) {
            this.buildResult = buildResult;
            LOG.log(Level.FINEST, "Replace parser proxy for {0}", prox.summary());
            this.proxy = prox;
            supp.fireChange();
        }
    }
    AdhocParserResult last = null;

    @Override
    public void parse(Snapshot snpsht, Task task, SourceModificationEvent sme) throws ParseException {
        LOG.log(Level.FINEST, "Parse {0} by {1}", new Object[]{task, System.identityHashCode(this)});
        String txt = snpsht.getText().toString();
        AntlrProxies.ParseTreeProxy px = proxy;
//        if (!txt.equals(proxy.text())) {
        if (txt.isEmpty()) {
            px = proxy = px.toEmptyParseTreeProxy(txt);
        } else {
            px = proxy = DynamicLanguageSupport.parseImmediately(proxy.mimeType(), txt, REPARSE);
            System.out.println("PROXY: " + px);
            System.out.println("IS UNPARSED: " + px.isUnparsed());
        }
        buildResult = DynamicLanguageSupport.lastBuildResult(proxy.mimeType(), txt, REPARSE);
        System.out.println("BUILD RESULT " + buildResult);
        LOG.log(Level.FINEST, "Parsed {0} for {1}", new Object[]{px.summary(), task});
//        }
        AdhocParserResult result = new AdhocParserResult(snpsht, px, buildResult);
        resultForTask.put(task, last = result);
    }

    @Override
    public Result getResult(Task task) throws ParseException {
        Result res = resultForTask.get(task);
        if (res == null) {
            LOG.log(Level.WARNING, "Null result for task {0} from {1}", new Object[]{task, System.identityHashCode(this)});
        }
        if ("org.netbeans.modules.java.hints.infrastructure.EmbeddedHintsCollector".equals(task.getClass().getName())) {
            return null;
        }
        if (res == null && last != null) {
            System.out.println("USING PREVIOUS RESULT FOR " + task + " " + last.parseTree().summary());
            return last;
        }
        return res;
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
