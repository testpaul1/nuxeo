/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.runtime.mockito;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runners.model.FrameworkMethod;
import org.mockito.MockitoAnnotations;
import org.mockito.configuration.IMockitoConfiguration;
import org.mockito.internal.configuration.GlobalConfiguration;
import org.nuxeo.runtime.api.DefaultServiceProvider;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

public class MockitoFeature implements RunnerFeature {

    protected final MockProvider provider = new MockProvider();

    protected AutoCloseable openedMocks;

    @Override
    public void start(FeaturesRunner runner) {
        provider.installSelf();
    }

    @Override
    public void testCreated(Object test) {
        DefaultServiceProvider.setProvider(provider);
        openedMocks = MockitoAnnotations.openMocks(test);
    }

    @Override
    public void afterTeardown(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        openedMocks.close();
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
        cleanupThread();
    }

    @Override
    public void stop(FeaturesRunner runner) {
        provider.uninstallSelf();
    }

    protected void cleanupThread() throws ReflectiveOperationException {
        ThreadLocal<IMockitoConfiguration> holder = (ThreadLocal<IMockitoConfiguration>) FieldUtils.readStaticField(GlobalConfiguration.class, "GLOBAL_CONFIGURATION", true);
        holder.remove();
    }
}
