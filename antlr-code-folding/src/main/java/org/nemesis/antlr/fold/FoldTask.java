package org.nemesis.antlr.fold;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
class FoldTask<P extends Parser.Result & ExtractionParserResult> extends ParserResultTask<P> {

    private final AtomicLong rev = new AtomicLong();
    private final Set<FoldMgr> managers = Collections.synchronizedSet(new WeakSet<>());
    private static final Logger LOG = Logger.getLogger(FoldTask.class.getName());
    private final String identifier;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    FoldTask(String identifier) {
        this.identifier = identifier;
    }

    FoldTask uncancel() {
        cancelled.set(false);
        return this;
    }

    @Override
    public String toString() {
        List<FoldMgr> mgrs = new ArrayList<>(managers);
        StringBuilder sb = new StringBuilder("FoldTask{")
                .append(System.identityHashCode(this))
                .append(": ").append(identifier)
                .append("; managerCount=").append(mgrs.size())
                .append("managers=[");
        for (FoldMgr m : mgrs) {
            sb.append(m).append("; ");
        }
        return sb.append("]}").toString();
    }

    void invalidate() {
        rev.incrementAndGet();
    }

    long version() {
        return rev.get();
    }

    void add(FoldMgr mgr) {
        LOG.log(Level.FINEST, "Add manager {0} to task {1}",
                new Object[]{mgr, this});
        managers.add(mgr);
    }

    void remove(FoldMgr mgr) {
        LOG.log(Level.FINEST, "Remove manager {0} from task {1}",
                new Object[]{mgr, this});
        managers.remove(mgr);
    }

    boolean isEmpty() {
        return managers.isEmpty();
    }

    @Override
    public void run(P result, SchedulerEvent event) {
        if (cancelled.compareAndSet(true, false)) {
            return;
        }
        if (isEmpty()) {
            LOG.log(Level.FINER, "Parse result received on empty task {0}", this);
            return;
        }
        LOG.log(Level.FINE, "Run {0} with parse result {1} for {2}", new Object[]{this, result, event});
        // do our synchronization here and be done with it
        Extraction extraction = result.extraction();
        if (extraction != null) {
            List<Runnable> committers = new ArrayList<>(16);
            List<FoldMgr> copy = new ArrayList<>(managers);
            for (FoldMgr manager : copy) {
                Runnable run = manager.withNewExtraction(extraction);
                if (run != null) {
                    committers.add(run);
                }
            }
            AggregateRunnable.invokeOnEventThread(committers);
        } else {
            LOG.log(Level.WARNING, "Null extraction from {0}", result);
        }
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
        LOG.log(Level.FINE, "Cancel {0}", this);
        cancelled.set(true);
        // XXX what here?  Increment rev?
    }

    static final class AggregateRunnable implements Runnable {

        private final List<Runnable> all;

        AggregateRunnable(List<Runnable> all) {
            this.all = all;
        }

        static void invokeOnEventThread(List<Runnable> all) {
            int size = all.size();
            switch (size) {
                case 0:
                    return;
                case 1:
                    EventQueue.invokeLater(all.get(0));
                    break;
                default:
                    EventQueue.invokeLater(new AggregateRunnable(all));
                    break;
            }
        }

        @Override
        public void run() {
            for (Runnable curr : all) {
                try {
                    curr.run();
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception committing folds: " + curr, ex);
                }
            }
        }
    }

//    UserTask asUserTask() {
//        return new UserTask() {
//            @Override
//            public void run(ResultIterator resultIterator) throws Exception {
//                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//            }
//        };
//    }
}
