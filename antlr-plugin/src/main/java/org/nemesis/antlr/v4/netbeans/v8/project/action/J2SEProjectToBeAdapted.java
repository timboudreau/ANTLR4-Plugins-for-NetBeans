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
package org.nemesis.antlr.v4.netbeans.v8.project.action;

import org.netbeans.api.project.Project;

import org.openide.util.Lookup;

import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public abstract class J2SEProjectToBeAdapted {
    protected static final InputOutput IO = IOProvider.getDefault().getIO
                                                        ("ANTLR plugin", false);
    protected static final OutputWriter out = IO.getOut();
    protected static final OutputWriter err = IO.getErr();
    
    protected final Project project;
    protected final Lookup  projectLookup;
    
    public J2SEProjectToBeAdapted(Project project) {
        this.project = project;
        this.projectLookup = project.getLookup();
    }
    
    public abstract void addANTLRSupport() throws TaskException;
}