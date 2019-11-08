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
package com.mastfrog.type.code.generation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Ant-style glob pattern matchers (borrowed from project-helpers).
 *
 * @author Tim Boudreau
 */
final class GlobFilter {

    private final Pattern pattern;
    private final String globPattern;
    private static final Map<String, Pattern> PATTERN_CACHE
            = new HashMap<>();

    GlobFilter(String pattern) {
        this.globPattern = pattern;
        this.pattern = patternFor(pattern);
    }

    public static Predicate<String> create(String pattern) {
        if (pattern.contains(",")) {
            Predicate<String> result = null;
            for (String s : pattern.split(",")) {
                s = s.trim();
                GlobFilter oneFilter = new GlobFilter(s);
                if (result == null) {
                    result = oneFilter.create();
                } else {
                    result = result.or(oneFilter.create());
                }
            }
            return result;
        } else {
            return new GlobFilter(pattern).create();
        }
    }

    private static Pattern patternFor(String pattern) {
        Pattern result = PATTERN_CACHE.get(pattern);
        if (result != null) {
            return result;
        }
        String[] parts = pattern.split("\\.");
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.isEmpty()) {
                continue;
            }
            switch (s) {
                case "**":
                    sb.append(".*");
                    break;
                case "?":
                    sb.append("[^\\.]+?");
                    break;
                case "*":
                    sb.append("[^\\.]*?");
                    break;
                default:
                    if (s.contains("*") || s.contains("?")) {
                        int max = s.length();
                        StringBuilder curr = new StringBuilder();
                        for (int j = 0; j < max; j++) {
                            char c = s.charAt(j);
                            if (c == '*' || c == '?') {
                                boolean any = c == '*';
                                if (curr.length() > 0) {
                                    sb.append(Pattern.quote(curr.toString()));
                                    curr.setLength(0);
                                }
                                if (any) {
                                    sb.append("[^\\.]*?");
                                } else {
                                    sb.append("[^\\.]+?");
                                }
                            } else {
                                sb.append(c);
                            }
                        }
                        if (curr.length() > 0) {
                            sb.append(Pattern.quote(curr.toString()));
                            curr.setLength(0);
                        }
                    } else {
                        sb.append(Pattern.quote(s));
                    }
            }
            if (i != parts.length - 1) {
                sb.append("\\.");
            }
        }
        result = Pattern.compile(sb.toString());
        PATTERN_CACHE.put(pattern, result);
        return result;
    }

    public Predicate<String> create() {
        return path -> {
            return pattern.matcher(path).matches();
        };
    }

    @Override
    public String toString() {
        return '"' + globPattern + "\" -> " + pattern.pattern();
    }
}
