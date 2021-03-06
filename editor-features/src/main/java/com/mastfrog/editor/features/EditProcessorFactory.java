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
package com.mastfrog.editor.features;

import java.util.function.BiFunction;

/**
 * Sub-processor that can intercept text insertion or deletion
 * and create an EditProcessor that can intervene to change the
 * edit the user is performing.
 *
 * @author Tim Boudreau
 */
interface EditProcessorFactory<T> extends BiFunction<EditPhase, ContextWrapper, EditProcessor> {

    Class<T> type();

    EditPhase initiatesFrom();

}
