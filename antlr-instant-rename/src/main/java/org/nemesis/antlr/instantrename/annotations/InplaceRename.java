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
package org.nemesis.antlr.instantrename.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.nemesis.antlr.instantrename.RenameParticipant;
import org.nemesis.charfilter.anno.CharFilterSpec;

/**
 * Annotate a NameReferenceSetKey, NamedRegionKey or SingletonKey with this to
 * generate an inplace rename action for it. For basic rename functionality,
 * simply annotate a NameRegionReferenceKey or NameRegionKey with it. A
 * character filter, which will block illegal characters in new names, and a
 * RenameParticipant, which can perform complex validation and processing of
 * renames, can optionally be provided.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface InplaceRename {

    /**
     * The mime type being targeted.
     *
     * @return The mime type
     */
    String mimeType();

    /**
     * Provide a filter for what characters are allowed in names, distinguishing
     * the initial and subsequent characters. In inplace rename, key-typed
     * events are consumed by filters that do not pass the test will be ignored;
     * in instant rename, they will be marked as errors.
     *
     * @return A filter spec
     */
    CharFilterSpec filter() default @CharFilterSpec();

    /**
     * Provide a RenameParticipant which can approve or veto rename initiation
     * and provide hooks which are called on change or finish.
     * Mutually exclusive with specifying useRefactoringApi.
     *
     * @return A rename participant type
     */
    Class<? extends RenameParticipant> renameParticipant() default DummyRenameParticipant.class;

    /**
     * If true, just invoke the rename refactoring - don't try instant rename.
     * Mutually exclusive with specifying renameParticipant.
     *
     * @return
     */
    boolean useRefactoringApi() default false;
}
