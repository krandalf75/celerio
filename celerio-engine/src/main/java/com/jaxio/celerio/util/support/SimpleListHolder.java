/*
 * Copyright 2015 JAXIO http://www.jaxio.com
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

package com.jaxio.celerio.util.support;

import com.jaxio.celerio.util.Named;

import java.util.Objects;
import java.util.function.Predicate;

import static com.google.common.collect.Iterables.filter;

/**
 * A list holder whose underlying List is built out of an Iterable and a predicate.
 */
public class SimpleListHolder<T extends Named> extends AbstractListHolder<T> {
    private final Iterable<T> original;
    private final Predicate<T> filter;

    public SimpleListHolder(Iterable<T> original) {
        this(original, ignored -> true);
    }

    @SuppressWarnings("java:S4738") // Existing template packs pass Guava predicates.
    public SimpleListHolder(Iterable<T> original, com.google.common.base.Predicate<T> filter) {
        this.original = Objects.requireNonNull(original, "original");
        this.filter = Objects.requireNonNull(filter, "filter")::apply;
    }

    @SuppressWarnings("java:S4738") // Existing template packs pass Guava predicates.
    public SimpleListHolder(Iterable<T> original, com.google.common.base.Predicate<T> filter, String sortProperty) {
        this(original, filter);
        setSortProperty(sortProperty);
    }

    @Override
    protected Iterable<T> getIterable() {
        return filter(original, filter::test);
    }
}
