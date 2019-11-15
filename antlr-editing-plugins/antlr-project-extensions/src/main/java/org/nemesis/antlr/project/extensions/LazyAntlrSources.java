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

import java.util.concurrent.TimeUnit;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class LazyAntlrSources implements Sources, Runnable, ChangeListener {

    private AntlrSources realSources;
    private final Lookup lookup;
    private final ChangeSupport supp = new ChangeSupport(this);

    LazyAntlrSources(Lookup lookup, RequestProcessor proc, long delay) {
        this.lookup = lookup;
        proc.schedule(this, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public SourceGroup[] getSourceGroups(String string) {
        AntlrSources sources;
        synchronized(this) {
            sources = realSources;
        }
        return sources == null ? new SourceGroup[0] : sources.getSourceGroups(string);
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        supp.addChangeListener(cl);
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        supp.removeChangeListener(cl);
    }

    @Override
    public void run() {
        AntlrSources result = new AntlrSources(lookup);
        synchronized(this) {
            realSources = result;
        }
        supp.fireChange();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        supp.fireChange();
    }

}
