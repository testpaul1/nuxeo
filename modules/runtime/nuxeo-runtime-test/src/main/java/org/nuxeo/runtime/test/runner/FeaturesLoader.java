/*
 * (C) Copyright 2014-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Stephane Lacoin
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.runtime.test.runner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.runners.model.TestClass;

import com.google.inject.Module;

class FeaturesLoader {

    private final FeaturesRunner runner;

    FeaturesLoader(FeaturesRunner featuresRunner) {
        runner = featuresRunner;
    }

    protected static class Holder {
        protected final Class<? extends RunnerFeature> type;

        protected final TestClass testClass;

        protected RunnerFeature feature;

        Holder(Class<? extends RunnerFeature> aType) throws ReflectiveOperationException {
            type = aType;
            testClass = new TestClass(aType);
            feature = aType.getDeclaredConstructor().newInstance();
        }

        @Override
        public String toString() {
            return "Holder [type=" + type + "]";
        }

    }

    protected final Map<Class<? extends RunnerFeature>, Holder> index = new HashMap<>();

    protected final List<Holder> holders = new LinkedList<>();

    Collection<Holder> holders() {
        return Collections.unmodifiableCollection(holders);
    }

    /**
     * @since 11.1
     */
    Collection<Holder> reversedHolders() {
        return reversed(holders);
    }

    Collection<RunnerFeature> features() {
        return holders.stream().map(h -> h.feature).collect(Collectors.toList());
    }

    protected <T> List<T> reversed(List<T> list) {
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    protected boolean contains(Class<? extends RunnerFeature> aType) {
        return index.containsKey(aType);
    }

    public void loadFeatures(Class<?> classToRun) throws Exception {
        // load required features from annotation
        List<Features> annos = FeaturesRunner.getScanner().getAnnotations(classToRun, Features.class);
        for (Features anno : annos) {
            for (Class<? extends RunnerFeature> cl : anno.value()) {
                loadFeature(new HashSet<>(), cl);
            }
        }

    }

    protected void loadFeature(HashSet<Class<?>> cycles, Class<? extends RunnerFeature> clazz) throws Exception {
        if (index.containsKey(clazz)) {
            return;
        }
        if (cycles.contains(clazz)) {
            throw new IllegalStateException("Cycle detected in features dependencies of " + clazz);
        }
        cycles.add(clazz);
        // load required features from annotation
        List<Features> annos = FeaturesRunner.getScanner().getAnnotations(clazz, Features.class);
        for (Features anno : annos) {
            for (Class<? extends RunnerFeature> cl : anno.value()) {
                loadFeature(cycles, cl);
            }
        }
        final Holder actual = new Holder(clazz);
        holders.add(actual);
        index.put(clazz, actual);
    }

    public <T extends RunnerFeature> T getFeature(Class<T> aType) {
        if (!index.containsKey(aType)) {
            return null;
        }
        return aType.cast(index.get(aType).feature);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Module onModule() {
        return aBinder -> {
            for (Holder each : holders) {
                each.feature.configure(runner, aBinder);
                aBinder.bind((Class) each.feature.getClass()).toInstance(each.feature);
                aBinder.requestInjection(each.feature);
            }
        };
    }

}
