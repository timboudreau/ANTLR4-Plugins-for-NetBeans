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
package org.nemesis.antlr.v4.netbeans.v8.maven.output;

import java.util.Set;

import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.execute.RunConfig;

import org.netbeans.modules.maven.api.output.OutputProcessor;
import org.netbeans.modules.maven.api.output.OutputProcessorFactory;
import org.netbeans.modules.maven.output.DefaultOutputProcessorFactory;

import org.openide.util.lookup.ServiceProvider;

/**
 * DefaultOutputProcessorFactory implements interface 
 * ContextOutputProcessorFactory that extends interface OutputProcessorFactory.
 * 
 * @author Frédéric Yvon Vinet
 */
@ServiceProvider(service=OutputProcessorFactory.class)
public class ANTLROutputProcessorFactory extends DefaultOutputProcessorFactory {
    @Override
    public Set<OutputProcessor> createProcessorsSet(Project project) {
//        System.out.println("ANTLROutputProcessorFactory.createProcessorsSet(Project)");
        Set<OutputProcessor> toReturn = super.createProcessorsSet(project);
        if (project != null) {
            toReturn.add(new ANTLROutputProcessor());
        }
        return toReturn;
    }

    @Override
    public Set<? extends OutputProcessor> createProcessorsSet
            (Project project, RunConfig config) {
        return super.createProcessorsSet(project, config);
    }
}
