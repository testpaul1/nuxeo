/*
 * (C) Copyright 2007-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.ec.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.mail.MailServiceImpl.DEFAULT_SENDER;

import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.ec.notification.email.EmailHelper;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.notification.api.Notification;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @author <a href="mailto:rspivak@nuxeo.com">Ruslan Spivak</a>
 */
@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, NotificationFeature.class })
@Deploy("org.nuxeo.mail")
@WithFrameworkProperty(name = "org.nuxeo.ecm.notification.serverPrefix", value = "testServerPrefix")
@WithFrameworkProperty(name = "org.nuxeo.ecm.notification.eMailSubjectPrefix", value = "testSubjectPrefix")
public class TestRegisterNotificationService {

    protected EmailHelper mailHelper = new EmailHelper();

    @Inject
    protected HotDeployer hotDeployer;

    @Test
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib.xml")
    public void testRegistration() {

        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");

        assertEquals(1, notifications.size());

        Notification notif = notifications.get(0);
        assertEquals("email", notif.getChannel());
        assertFalse(notif.getAutoSubscribed());
        assertEquals("section", notif.getAvailableIn());
        // assertEquals(true, notif.getEnabled());
        assertEquals("Test Notification Label", notif.getLabel());
        assertEquals("Test Notification Subject", notif.getSubject());
        assertEquals("Test Notification Subject Template", notif.getSubjectTemplate());
        assertEquals("test-template", notif.getTemplate());
        assertEquals("NotificationContext['exp1']", notif.getTemplateExpr());

        Map<String, Serializable> infos = new HashMap<>();
        infos.put("exp1", "myDynamicTemplate");
        String template = mailHelper.evaluateMvelExpresssion(notif.getTemplateExpr(), infos);
        assertEquals("myDynamicTemplate", template);

        notifications = getService().getNotificationsForSubscriptions("section");
        assertEquals(1, notifications.size());

        URL newModifTemplate = NotificationService.getTemplateURL("test-template");
        assertTrue(newModifTemplate.getFile().endsWith("templates/test-template.ftl"));

    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib-disabled.xml")
    public void testRegistrationDisabled() {
        assertEquals(0, getService().getNotificationsForEvents("testEvent").size());
    }

    @Test
    public void testRegistrationOverrideWithDisabled() throws Exception {
        hotDeployer.deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib.xml");
        assertEquals(1, getService().getNotificationsForEvents("testEvent").size());
        hotDeployer.deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib-disabled.xml");
        assertEquals(0, getService().getNotificationsForEvents("testEvent").size());
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib.xml")
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib-overridden.xml")
    public void testRegistrationOverride() {

        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");
        assertEquals(0, notifications.size());

        notifications = getService().getNotificationsForEvents("testEvent-ov");
        assertEquals(1, notifications.size());

        Notification notif = notifications.get(0);
        assertEquals("email-ov", notif.getChannel());
        assertTrue(notif.getAutoSubscribed());
        assertEquals("folder", notif.getAvailableIn());
        // assertEquals(true, notif.getEnabled());
        assertEquals("Test Notification Label-ov", notif.getLabel());
        assertEquals("Test Notification Subject-ov", notif.getSubject());
        assertEquals("Test Notification Subject Template-ov", notif.getSubjectTemplate());
        assertEquals("test-template-ov", notif.getTemplate());
        assertEquals("NotificationContext['exp1-ov']", notif.getTemplateExpr());

        notifications = getService().getNotificationsForSubscriptions("section");
        assertEquals(0, notifications.size());

        notifications = getService().getNotificationsForSubscriptions("folder");
        assertEquals(0, notifications.size());

        URL newModifTemplate = NotificationService.getTemplateURL("test-template");
        assertTrue(newModifTemplate.getFile().endsWith("templates/test-template-ov.ftl"));
    }

    @Test
    public void testExpandVarsInGeneralSettings() throws Exception {
        hotDeployer.deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib.xml");

        // these should not be altered
        assertEquals("http://localhost:8080/nuxeo/", getService().getServerUrlPrefix());
        assertEquals("[Nuxeo]", getService().getEMailSubjectPrefix());
        assertEquals(DEFAULT_SENDER, getService().getMailSenderName());

        hotDeployer.deploy("org.nuxeo.ecm.platform.notification.tests:notification-contrib-overridden.xml");

        // these should be expanded
        assertEquals("http://testServerPrefix/nuxeo", getService().getServerUrlPrefix());
        assertEquals("testSubjectPrefix", getService().getEMailSubjectPrefix());

        // this one should not be expanded
        assertEquals("${not.existing.property}", getService().getMailSenderName());
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-veto-contrib.xml")
    @Deploy("org.nuxeo.ecm.platform.notification.tests:notification-veto-contrib-overridden.xml")
    public void testVetoRegistration() {

        Collection<NotificationListenerVeto> vetos = getService().getNotificationVetos();
        assertEquals(3, vetos.size());
        assertEquals("org.nuxeo.ecm.platform.ec.notification.veto.NotificationVeto1",
                getService().getNotificationListenerVetoRegistry().getVeto("veto1").getClass().getCanonicalName());
        assertEquals("org.nuxeo.ecm.platform.ec.notification.veto.NotificationVeto20",
                getService().getNotificationListenerVetoRegistry().getVeto("veto2").getClass().getCanonicalName());

    }

    protected NotificationService getService() {
        return (NotificationService) Framework.getService(NotificationManager.class);
    }

}
