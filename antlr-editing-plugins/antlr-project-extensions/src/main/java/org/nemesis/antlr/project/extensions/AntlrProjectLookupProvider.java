package org.nemesis.antlr.project.extensions;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.LookupProvider;
import org.netbeans.spi.project.LookupProvider.Registration.ProjectType;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
@LookupProvider.Registration(projectTypes = @ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
public class AntlrProjectLookupProvider implements LookupProvider {

    static final Logger LOG = Logger.getLogger(AntlrProjectLookupProvider.class.getName());

    public AntlrProjectLookupProvider() {
        LOG.log(Level.FINE, "Created an {0}", AntlrProjectLookupProvider.class.getName());
    }

    @Override
    public Lookup createAdditionalLookup(Lookup baseContext) {
        Project project = baseContext.lookup(Project.class);
        LOG.log(Level.FINER, "Create antlr lookup for project {0}",
                project.getProjectDirectory().getName());
        InstanceContent content = new InstanceContent();
        content.add(new AntlrFileBuiltQuery());
        content.add(new AntlrSources(baseContext));
        content.add(AntlrRecommendedTemplates.INSTANCE);
        return new AbstractLookup(content);
    }

}
