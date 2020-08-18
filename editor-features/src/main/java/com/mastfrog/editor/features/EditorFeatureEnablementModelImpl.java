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

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class EditorFeatureEnablementModelImpl implements EditorFeatureEnablementModel {

    static final String NO_CATEGORY = "_";
    private final String mimeType;

    private final EnablableEditProcessorFactory<?> item;
    private boolean state;

    EditorFeatureEnablementModelImpl(String mimeType, EnablableEditProcessorFactory<?> item) {
        this.mimeType = mimeType;
        this.item = item;
        state = item.isEnabled();
    }

    @Override
    public String name() {
        return item.name();
    }

    @Override
    public String description() {
        return item.description();
    }

    @Override
    public String id() {
        return item.id();
    }

    @Override
    public String category() {
        String result = item.category();
        return result == null ? NO_CATEGORY : result;
    }

    @Override
    public boolean isEnabled() {
        return state;
    }

    @Override
    public void setEnabled(boolean val) {
        state = val;
    }

    @Override
    public String toString() {
        return item.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof EditorFeatureEnablementModelImpl) {
            EditorFeatureEnablementModelImpl efd = (EditorFeatureEnablementModelImpl) o;
            return Objects.equals(efd.id(), id())
                    && efd.mimeType.equals(mimeType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id().hashCode() * 31;
    }

    @Override
    public boolean isChanged() {
        return state != item.isEnabled();
    }

    @Override
    public void commit() {
        if (isChanged()) {
            item.setEnabled(state);
        }
    }
}
