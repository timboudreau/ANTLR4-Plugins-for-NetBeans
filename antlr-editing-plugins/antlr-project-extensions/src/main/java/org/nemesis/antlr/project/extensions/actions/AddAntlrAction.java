/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
    @ActionReference(path = "Projects/org-netbeans-modules-maven/Actions", position = 297, separatorBefore = 296),
    @ActionReference(path = "Projects/org-netbeans-modules-java-j2seproject/Actions", position = 297, separatorBefore = 296),
    @ActionReference(path = "Projects/org-netbeans-modules-gradle/Actions", position = 297, separatorBefore = 296),})
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
        AddAntlrToProject result = new AddAntlrToProject(p);
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
