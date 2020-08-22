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
package org.nemesis.antlr.project.extensions;

import java.awt.EventQueue;
import java.awt.Image;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.projectupdatenotificaton.ProjectUpdates;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectIconAnnotator;
import org.openide.util.ChangeSupport;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = ProjectIconAnnotator.class)
public class AntlrIconAnnotator implements ProjectIconAnnotator, Consumer<Path> {

    private static final long INIT_DELAY_SECONDS = 30;
    private final long created = System.currentTimeMillis();
    private final ChangeSupport supp = new ChangeSupport(this);

    private static Reference<AntlrIconAnnotator> INSTANCE;
    private boolean lastResult;

    @SuppressWarnings("LeakingThisInConstructor")
    public AntlrIconAnnotator() {
        synchronized (AntlrIconAnnotator.class) {
            INSTANCE = new WeakReference<>(this);
        }
        RequestProcessor.getDefault()
                .schedule(new LazyInit(supp), 30, TimeUnit.SECONDS);
        ProjectUpdates.subscribeToChanges(this);
    }

    boolean isInInitDelay() {
        return (System.currentTimeMillis() - created) < INIT_DELAY_SECONDS * 1000;
    }

    static void fireIconBadgingChanges() {
        AntlrIconAnnotator anno = INSTANCE.get();
        if (anno != null && !anno.isInInitDelay()) {
            anno.fire();
        }
    }

    private void fire() {
        // invokeLater to avoid possibly firing an icon change while holding
        // ProjectManager.MUTEX, which results in
        // java.lang.IllegalStateException: Should not acquire Children.MUTEX
        //    while holding ProjectManager.mutex()
        // - create unit tests action updates libraries which will trigger a change
        EventQueue.invokeLater(supp::fireChange);
    }

    @Override
    public void accept(Path t) {
        if (!isInInitDelay()) {
            fire();
        }
    }

    static final class LazyInit implements Runnable {

        private final ChangeSupport supp;

        public LazyInit(ChangeSupport supp) {
            this.supp = supp;
        }

        @Override
        public void run() {
            supp.fireChange();
        }
    }

    @Override
    public Image annotateIcon(Project p, Image original, boolean openedNode) {
        if (isInInitDelay()) {
            return original;
        }
        // XXX with a lot of projects open, right after a git pull that
        // changes a whole bunch of POM files, this is going to wind up
        // recreating Antlrconfigurations for every project by reading
        // every POM and all its dependencies.  What might be good:
        // Create a tracking object that simply keeps a count of all calls
        // to this method globally in the last several seconds, and if more
        // than a threshold, returns the original and enqueues a delayed,
        // staggered with random jitter, change to be fired later
        AntlrConfiguration config = AntlrConfiguration.forProject(p);
        if (config == null || config.isSpeculativeConfig()) {
            return original;
        }
        return ImageUtilities.mergeImages(ImageUtilities.addToolTipToImage(original,
                NbBundle.getMessage(AntlrIconAnnotator.class, "antlr")),
                badge(), 0, 12);
    }

    private static Image badge() {
        if (BADGE == null) {
            BADGE = ImageUtilities.loadImage(
                    "org/nemesis/antlr/project/extensions/antlrProjectBadge.png");
        }
        return BADGE;
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        supp.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        supp.removeChangeListener(listener);
    }

    private static Image BADGE;
}
