/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package com.mastfrog.antlr.cc;

import com.mastfrog.util.collections.IntList;
import org.antlr.v4.runtime.misc.IntervalSet;

public class FollowSetWithPath {

    public IntervalSet intervals;
    public IntList path;
    public IntList following;

    public String toString() {
        return "FSWithPath(" + intervals + " / " + path + " / " + following + ")";
    }

}