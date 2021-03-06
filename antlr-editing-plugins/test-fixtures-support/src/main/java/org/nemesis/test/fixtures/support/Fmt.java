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
package org.nemesis.test.fixtures.support;

import com.mastfrog.util.strings.Strings;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogRecord;

/**
 *
 * @author Tim Boudreau
 */
public final class Fmt extends java.util.logging.Formatter {

    private boolean nbeventsLogged;
    private final AtomicLong creation = new AtomicLong(System.currentTimeMillis());

    private long touch() {
        long now = System.currentTimeMillis();
        long then = creation.getAndSet(now);
        return now - then;
    }

    String pad(long val) {
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toString(val));
        while (sb.length() < 4) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder().append(pad(record.getSequenceNumber())).append("|");
        sb.append(pad(touch())).append(':');
        String nm = record.getLoggerName();
        if (!nbeventsLogged && "NbEvents".equals(nm)) {
            nbeventsLogged = true;
            new Exception("Got message from " + nm + " - " + " something is attempting to start the full IDE.").printStackTrace();
        }
        if (nm.indexOf('.') > 0 && nm.indexOf('.') < nm.length() - 1) {
            nm = nm.substring(nm.lastIndexOf('.') + 1);
        }
        if (!TestFixtures.excludedLogs.isEmpty() && TestFixtures.excludedLogs.contains(nm)) {
            return "";
        }
        if (!TestFixtures.includedLogs.isEmpty() && !TestFixtures.includedLogs.contains(nm)) {
            return "";
        }
        sb.append(nm).append(": ");
        String msg = record.getMessage();
        if (msg != null && record.getParameters() != null && record.getParameters().length > 0) {
            msg = MessageFormat.format(msg, record.getParameters());
        }
        if (msg != null) {
            sb.append(msg);
        }
        if (record.getThrown() != null) {
            sb.append(Strings.toString(record.getThrown()));
        }
        return sb.append('\n').toString();
    }

}
