/*
 * (C) Copyright 2009-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Alexandre Russel
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.DOCUMENT_ROUTE_DOCUMENT_TYPE;
import static org.nuxeo.ecm.platform.task.TaskConstants.TASK_PROCESS_ID_PROPERTY_NAME;
import static org.nuxeo.ecm.platform.task.TaskConstants.TASK_TYPE_NAME;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.UserEntry;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.api.security.impl.UserEntryImpl;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class TestDocumentRoutingService extends DocumentRoutingTestCase {

    @Inject
    protected TransactionalFeature txFeature;

    protected File tmp;

    @After
    public void tearDown() {
        if (tmp != null) {
            tmp.delete();
        }
    }

    @Test
    public void testSaveInstanceAsNewModel() {
        DocumentRoute route = createDocumentRoute(session, ROUTE1);
        service.lockDocumentRoute(route, session);
        route = service.validateRouteModel(route, session);
        service.unlockDocumentRouteUnrestrictedSession(route, session);
        session.save();
        waitForAsyncExec();
        route = service.createNewInstance(route, new ArrayList<>(), session, true);
        assertNotNull(route);
        session.save();
        CoreSession managersSession = CoreInstance.getCoreSession(session.getRepositoryName(), "routeManagers");
        DocumentModel step = managersSession.getChildren(route.getDocument().getRef()).get(0);
        service.lockDocumentRoute(route, managersSession);
        managersSession.removeDocument(step.getRef());
        service.unlockDocumentRoute(route, managersSession);
        DocumentRoute newModel = service.saveRouteAsNewModel(route, managersSession);
        assertNotNull(newModel);
        assertEquals("(COPY) route1", newModel.getDocument().getPropertyValue("dc:title"));
    }

    @Test
    public void testCreateNewInstance() {
        DocumentRoute route = createDocumentRoute(session, ROUTE1);
        assertNotNull(route);
        session.save();
        List<DocumentRoute> routes = service.getAvailableDocumentRoute(session);
        assertEquals(1, routes.size());
        DocumentRoute routeModel = routes.get(0);
        DocumentModel doc1 = createTestDocument("test1", session);
        session.save();
        service.lockDocumentRoute(route, session);
        route = service.validateRouteModel(route, session);
        service.unlockDocumentRouteUnrestrictedSession(route, session);
        session.save();
        assertEquals("validated", route.getDocument().getCurrentLifeCycleState());
        assertEquals("validated", session.getChildren(route.getDocument().getRef()).get(0).getCurrentLifeCycleState());
        session.save();
        waitForAsyncExec();
        DocumentRoute routeInstance = service.createNewInstance(routeModel, Collections.singletonList(doc1.getId()),
                session, true);
        assertTrue(routeInstance.isDone());

        // check that we don't get route instances when querying for models
        String routeDocId = service.getRouteModelDocIdWithId(session, ROUTE1);
        DocumentModel doc = session.getDocument(new IdRef(routeDocId));
        route = doc.getAdapter(DocumentRoute.class);

        assertNotNull(route);
        // this API does not restrict itself to models actually
        routes = service.getAvailableDocumentRoute(session);
        assertEquals(2, routes.size());
    }

    @Test
    public void testGetAvailableDocumentRouteModel() {
        DocumentRoute route = createDocumentRoute(session, ROUTE1);
        assertNotNull(route);
        session.save();
        List<DocumentRoute> routes = service.getAvailableDocumentRoute(session);
        assertEquals(1, routes.size());
    }

    @Test
    public void testRouteModel() {
        DocumentModel folder = createDocumentModel(session, "TestFolder", "Folder", "/");
        session.save();
        assertNotNull(folder);
        setPermissionToUser(folder, "jdoe", SecurityConstants.WRITE);
        DocumentModel route = createDocumentRouteModel(session, ROUTE1, folder.getPathAsString());
        session.save();
        assertNotNull(route);
        service.lockDocumentRoute(route.getAdapter(DocumentRoute.class), session);
        route = service.validateRouteModel(route.getAdapter(DocumentRoute.class), session).getDocument();
        session.save();
        service.unlockDocumentRouteUnrestrictedSession(route.getAdapter(DocumentRoute.class), session);
        route = session.getDocument(route.getRef());
        assertEquals("validated", route.getCurrentLifeCycleState());

        CoreSession jdoeSession = CoreInstance.getCoreSession(session.getRepositoryName(), "jdoe");
        assertFalse(jdoeSession.hasPermission(route.getRef(), SecurityConstants.WRITE));
        assertTrue(jdoeSession.hasPermission(route.getRef(), SecurityConstants.READ));
    }

    @Test
    public void testOrphanTasksDeletion() {
        DocumentModel route = session.createDocumentModel("/default-domain/workspaces", "dummyRoute",
                DOCUMENT_ROUTE_DOCUMENT_TYPE);
        route = session.createDocument(route);
        DocumentModel task = session.createDocumentModel("/default-domain/workspaces", "dummyTask", TASK_TYPE_NAME);
        task.setPropertyValue(TASK_PROCESS_ID_PROPERTY_NAME, route.getId());
        task = session.createDocument(task);

        session.removeDocument(route.getRef());
        txFeature.nextTransaction();
        assertFalse(session.exists(task.getRef()));
    }

    @Test
    public void testOrphanDocumentRoutesDeletion() {
        DocumentModel file1 = session.createDocumentModel("/", "File1", "File");
        file1 = session.createDocument(file1);
        DocumentModel file2 = session.createDocumentModel("/", "File2", "File");
        file2 = session.createDocument(file2);
        List<String> docIds = List.of(file1.getId(), file2.getId());
        DocumentModel route = session.createDocumentModel("/default-domain/workspaces", "dummyRoute1",
                DOCUMENT_ROUTE_DOCUMENT_TYPE);
        route.setPropertyValue(DocumentRoutingConstants.ATTACHED_DOCUMENTS_PROPERTY_NAME, (Serializable) docIds);
        route = session.createDocument(route);

        session.removeDocument(file1.getRef());
        txFeature.nextTransaction();
        // Still attached to file2
        assertTrue(session.exists(route.getRef()));
        session.removeDocument(file2.getRef());
        txFeature.nextTransaction();
        // Not attached anymore
        assertFalse(session.exists(route.getRef()));
    }

    protected void setPermissionToUser(DocumentModel doc, String username, String... perms) {
        ACP acp = doc.getACP();
        if (acp == null) {
            acp = new ACPImpl();
        }
        UserEntry userEntry = new UserEntryImpl(username);
        for (String perm : perms) {
            userEntry.addPrivilege(perm, true);
        }
        acp.setRules("test", new UserEntry[] { userEntry });
        doc.setACP(acp, true);
        session.save();
    }

    protected void waitForAsyncExec() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
        Framework.getService(EventService.class).waitForAsyncCompletion();
    }

}
