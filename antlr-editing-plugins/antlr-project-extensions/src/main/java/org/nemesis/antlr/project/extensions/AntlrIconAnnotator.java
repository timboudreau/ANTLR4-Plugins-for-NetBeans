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
        supp.fireChange();
    }

    @Override
    public void accept(Path t) {
        if (!isInInitDelay()) {
            supp.fireChange();
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
        if (!AntlrConfiguration.isAntlrProject(p)) {
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
