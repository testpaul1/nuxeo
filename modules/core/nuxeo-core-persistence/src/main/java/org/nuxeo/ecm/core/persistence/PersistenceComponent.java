/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     "Stephane Lacoin (aka matic) <slacoin@nuxeo.org>"
 */
package org.nuxeo.ecm.core.persistence;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @author Stephane Lacoin [aka matic]
 */
public class PersistenceComponent extends DefaultComponent
        implements HibernateConfigurator, PersistenceProviderFactory {

    protected final Map<String, HibernateConfiguration> registry = new HashMap<>();

    @Override
    public int getApplicationStartedOrder() {
        return 50; // even before repository init
    }

    @Override
    public void start(ComponentContext context) {
        /*
         * Initialize all the persistence units synchronously at startup, otherwise init may end up being called during
         * the first asynchronous event, which means hibernate init may happen in parallel with the main Nuxeo startup
         * thread which may be doing the hibernate init for someone else (JBPM for instance).
         */
        for (String name : registry.keySet()) {
            PersistenceProvider pp = newProvider(name);
            pp.openPersistenceUnit(); // creates tables etc.
            pp.closePersistenceUnit();
        }
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if ("hibernate".equals(extensionPoint)) {
            registerHibernateContribution((HibernateConfiguration) contribution, contributor.getName());
        }
    }

    protected void registerHibernateContribution(HibernateConfiguration contribution, ComponentName contributorName) {
        if (contribution.name == null) {
            throw new PersistenceError(
                    contributorName + " should set the 'name' attribute of hibernate configurations");
        }
        if (contribution.hibernateProperties != null) {
            doPatchForTests(contribution.hibernateProperties);
        }
        if (!registry.containsKey(contribution.name)) {
            registry.put(contribution.name, contribution);
        } else {
            registry.get(contribution.name).merge(contribution);
        }
    }

    protected void doPatchForTests(Map<String, String> hibernateProperties) {
        String url = hibernateProperties.get(AvailableSettings.URL);
        if (url != null) {
            url = Framework.expandVars(url);
            hibernateProperties.put("hibernate.connection.url", url);
        }
    }

    @Override
    public PersistenceProvider newProvider(String name) {
        EntityManagerFactoryProvider emfProvider = registry.get(name);
        if (emfProvider == null) {
            throw new PersistenceError("no hibernate configuration identified by '" + name + "' is available");
        }
        return new PersistenceProvider(emfProvider);
    }

    @Override
    public HibernateConfiguration getHibernateConfiguration(String name) {
        HibernateConfiguration config = registry.get(name);
        if (config == null) {
            throw new PersistenceError("no hibernate configuration identified by '" + name + "' is available");
        }
        return config;
    }

}
