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

package com.mastfrog.antlr.ant.task;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 *
 * @author Tim Boudreau
 */
class TaskToolListener implements ANTLRToolListener {

    private final Task task;
    private final boolean isLog;
    private final List<ANTLRMessage> errors = new CopyOnWriteArrayList<>();

    public TaskToolListener(Task task, boolean isLog) {
        this.task = task;
        this.isLog = isLog;
    }

    public void rethrow() throws BuildException {
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Iterator<ANTLRMessage> it = errors.iterator(); it.hasNext();) {
                ANTLRMessage err = it.next();
                sb.append(err.getMessageTemplate(true).render());
                if (it.hasNext()) {
                    sb.append('\n');
                }
            }
            throw new BuildException(sb.toString());
        }
    }

    @Override
    public void info(String msg) {
        task.log("INFO: " + msg);
        if (isLog) {
            task.log(msg, Project.MSG_VERBOSE);
        }
    }

    @Override
    public void error(ANTLRMessage msg) {
        errors.add(msg);
        task.log(msg.getMessageTemplate(true).render(), Project.MSG_ERR);
    }

    @Override
    public void warning(ANTLRMessage msg) {
        task.log(msg.getMessageTemplate(true).render(), Project.MSG_INFO);
    }

}
