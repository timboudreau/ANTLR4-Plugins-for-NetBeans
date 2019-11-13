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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.LookupProvider;
import org.netbeans.spi.project.LookupProvider.Registration.ProjectType;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
@LookupProvider.Registration(projectTypes = @ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
public class AntlrProjectLookupProvider implements LookupProvider {

    static final Logger LOG = Logger.getLogger(AntlrProjectLookupProvider.class.getName());
    private long created = System.currentTimeMillis();
    private static final long DELAY = 65000;
    private final RequestProcessor initThreadPool = new RequestProcessor("antlr-project-lookup-init", 3, false);

    static {
        LOG.setLevel(Level.ALL);
    }

    public AntlrProjectLookupProvider() {
        LOG.log(Level.FINE, "Created an {0}", AntlrProjectLookupProvider.class.getName());
    }

    @Override
    public Lookup createAdditionalLookup(Lookup baseContext) {
        long delay = remainingDelay();
        InstanceContent content = new InstanceContent();
        Runnable init = () -> {
            Project project = baseContext.lookup(Project.class);
            LOG.log(Level.FINER, "Create antlr lookup for project {0}",
                    project.getProjectDirectory().getName());
            content.add(new AntlrFileBuiltQuery());
            content.add(new AntlrSources(baseContext));
            content.add(AntlrRecommendedTemplates.INSTANCE);
        };
        if (delay > 0) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Postpone project lookup init for {0} for {1}ms.",
                        new Object[]{baseContext.lookup(Project.class), delay});
            }
            RequestProcessor.Task task = initThreadPool.create(init);
            task.schedule((int) delay);
        } else {
            init.run();
        }
        return new AbstractLookup(content);
    }

    private long remainingDelay() {
        long target = created + DELAY;
        return Math.max(0, target - System.currentTimeMillis());
    }

}
