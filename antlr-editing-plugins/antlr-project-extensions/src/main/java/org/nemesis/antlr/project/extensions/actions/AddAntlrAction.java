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
package org.nemesis.antlr.project.extensions.actions;

import java.awt.event.ActionEvent;
import java.util.Collection;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
@ActionID(
        category = "Project",
        id = "org.nemesis.antlr.project.ui.AddAntlrToProject"
)
@ActionRegistration(
        displayName = "#CTL_AddAntlrToProject",
        lazy = false,
        asynchronous = false
)
@ActionReferences({
    @ActionReference(path = "Projects/org-netbeans-modules-maven/Actions", position = 897, separatorBefore = 896),
    @ActionReference(path = "Projects/org-netbeans-modules-java-j2seproject/Actions", position = 897, separatorBefore = 896),
    @ActionReference(path = "Projects/org-netbeans-modules-gradle/Actions", position = 897, separatorBefore = 896),})
@NbBundle.Messages("CTL_AddAntlrToProject=Add Antlr Support to Project")
public class AddAntlrAction extends AbstractAction implements ContextAwareAction, LookupListener {

    private final Lookup lkp;
    private Lookup.Result<Project> res;

    @SuppressWarnings("LeakingThisInConstructor")
    public AddAntlrAction(Lookup lkp) {
        super(Bundle.CTL_AddAntlrToProject());
        this.lkp = lkp;
        res = lkp.lookupResult(Project.class);
        res.addLookupListener(this);
        res.allInstances();
        putValue("hideWhenDisabled", Boolean.TRUE);
    }

    public AddAntlrAction() {
        this(Utilities.actionsGlobalContext());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (lkp == Utilities.actionsGlobalContext()) {
            throw new AssertionError("Don't invoke the controlling instance");
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        Project p = lkp.lookup(Project.class);
        AddAntlrToProjectAction result = new AddAntlrToProjectAction(p);
        return result;
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        Collection<? extends Project> all = res.allInstances();
        boolean enable = all.size() == 1;
        if (enable) {
            Project p = all.iterator().next();
            enable = AntlrConfiguration.antlrAdder(p) != null;
        }
        setEnabled(enable);
    }
}
