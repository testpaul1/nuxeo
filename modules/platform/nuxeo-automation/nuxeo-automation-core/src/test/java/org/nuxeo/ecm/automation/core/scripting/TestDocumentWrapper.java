/*
 * (C) Copyright 2012-2018 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors: Sun Seng David TAN <stan@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.scripting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
@RepositoryConfig(init = DefaultRepositoryInit.class)
public class TestDocumentWrapper {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected TrashService trashService;

    /**
     * Test the documentWrapper getRef method to be used in script. This test use the operation chain defined in the
     * test extension src/test/resources/test-doc-wrapper-get-ref.xml
     */
    @Test
    @Deploy("org.nuxeo.ecm.automation.core:test-doc-wrapper-get-ref.xml")
    public void testDocumentWrapperGetRef() throws Exception {

        // testing scripts using document wrapper get ref method: follow
        // transition

        // create a File document
        DocumentModel doc = session.createDocumentModel("/", "TestFile", "File");
        doc = session.createDocument(doc);

        // before starting, check it's in project
        String lifecycleState = session.getCurrentLifeCycleState(doc.getRef());
        assertEquals("At first, the document currentlifecycle state is", "project", lifecycleState);

        // The automation chain should be similar to the following:
        //
        // session.followTransition(doc.getRef(), "delete");
        // session.save();
        //
        // Run the script operation:
        runChain(doc, "followTransitionApprove");

        // the script operation should run a Session.followTransition using
        // getRef of document wrapper
        lifecycleState = session.getCurrentLifeCycleState(doc.getRef());
        assertEquals("After the test, the document currentlifecycle state is", "approved", lifecycleState);
    }

    @Test
    @Deploy("org.nuxeo.ecm.automation.core:test-doc-wrapper-is-trashed.xml")
    public void testDocumentWrapperIsTrashed() throws Exception {
        // create a trashed File document
        DocumentModel doc = session.createDocumentModel("/", "TestFile", "File");
        doc = session.createDocument(doc);
        trashService.trashDocument(doc);

        OperationContext ctx = runChain(doc, "checkDocIsTrashed");

        assertEquals(true, ctx.get("isTrashed"));
    }

    protected OperationContext runChain(DocumentModel inputDoc, String chainId) throws Exception {
        try (OperationContext ctx = new OperationContext(session)) {
            ctx.setInput(inputDoc);
            automationService.run(ctx, chainId);
            return ctx;
        }
    }

    @Test
    public void testGetParentRef() {
        DocumentModel doc = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        DocumentWrapper workspaces = new DocumentWrapper(session, doc);
        DocumentWrapper domain = workspaces.getParent("Domain");
        assertNotNull(domain);
        DocumentWrapper unknown = workspaces.getParent("pfff");
        assertNull(unknown);
    }
}
