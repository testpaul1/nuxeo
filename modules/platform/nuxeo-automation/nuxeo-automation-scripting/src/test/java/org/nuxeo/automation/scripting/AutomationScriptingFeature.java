/*
 * (C) Copyright 2016-2019 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.automation.scripting;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.inject.Inject;

import org.nuxeo.automation.scripting.api.AutomationScriptingService;
import org.nuxeo.automation.scripting.internals.AutomationScriptingServiceImpl;
import org.nuxeo.automation.scripting.internals.ScriptingOperationImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

import com.google.inject.Binder;

/**
 * @since 8.4
 */
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.automation.features")
@Deploy("org.nuxeo.ecm.automation.scripting")
@Deploy("org.nuxeo.ecm.automation.scripting:automation-scripting-contrib.xml")
@Deploy("org.nuxeo.ecm.automation.scripting:core-types-contrib.xml")
public class AutomationScriptingFeature implements RunnerFeature {

    @Inject
    protected AutomationScriptingService scripting;

    protected FeaturesRunner runner;

    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        this.runner = runner;
    }

    InputStream load(String location) throws IOException {
        return runner.getTargetTestResource(location).openStream();
    }

    public <T> T run(String location, CoreSession session, Class<T> typeof) throws Exception {
        try (AutomationScriptingService.Session context = scripting.get(session)) {
            return typeof.cast(context.run(load(location)));
        }
    }

    /**
     * This version receives an explicit {@code scripting} parameter because of a bug in injection that doesn't allow to
     * use the previous method with a test-method-level {@code Deploy} (the old value of a component stays injected).
     *
     * @since 10.2
     */
    public <T> T run(AutomationScriptingService scripting, String location, CoreSession session, Class<T> typeof)
            throws Exception {
        try (AutomationScriptingService.Session context = scripting.get(session)) {
            return typeof.cast(context.run(load(location)));
        }
    }

    /**
     * Runs {@link ScriptingOperationImpl} with @{@link org.nuxeo.runtime.test.runner.WithFrameworkProperty} awareness.
     *
     * @since 2023.9
     */
    public void runScriptWithFrameworkProperties(Object input, Map<String, Object> params, String location,
            CoreSession session) throws Exception {
        // The ScriptEngine is final and instantiated before annotations are taken into account. We need a new one.
        try (AutomationScriptingService.Session s = new AutomationScriptingServiceImpl().get(session)) {
            @SuppressWarnings("ConstantConditions")
            var scriptInputStream = getClass().getClassLoader().getResource(location).openStream();
            s.handleof(scriptInputStream, ScriptingOperationImpl.Runnable.class).run(input, params);
        }
    }

}
