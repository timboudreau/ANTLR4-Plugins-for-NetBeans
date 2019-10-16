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
package org.nemesis.debug.spi;

import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public interface Emitter {

    public void enterContext(int depth, long globalOrder, long timestamp, String threadName, long threadId, boolean reentry, String ownerType, String ownerToString, String action, Supplier<String> details, long creationThreadId, String creationThreadString);

    public void exitContext(int depth);

    public void thrown(int depth, long globalOrder, long timestamp, String threadName, long threadId, Throwable thrown, Supplier<String> stackTrace);

    public void message(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, Supplier<String> msg);

    public void successOrFailure(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, boolean success, Supplier<String> msg);

}
