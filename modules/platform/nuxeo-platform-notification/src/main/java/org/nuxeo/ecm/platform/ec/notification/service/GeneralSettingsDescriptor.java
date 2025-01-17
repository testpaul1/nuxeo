/*
 * (C) Copyright 2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.ec.notification.service;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @author <a href="mailto:npaslaru@nuxeo.com">Narcis Paslaru</a>
 */
@XObject("settings")
public class GeneralSettingsDescriptor {

    @XNode("serverPrefix")
    protected String serverPrefix;

    @XNode("eMailSubjectPrefix")
    protected String eMailSubjectPrefix;

    @XNode("mailSessionJndiName")
    protected String mailSessionJndiName;

    @XNode("mailSenderName")
    protected String mailSenderName;

    public String getEMailSubjectPrefix() {
        return eMailSubjectPrefix;
    }

    public String getServerPrefix() {
        return serverPrefix;
    }

    /**
     * @deprecated since 2023.4 use {@link #getMailSenderName()} instead.
     */
    @Deprecated(since = "2023.4")
    public String getMailSessionJndiName() {
        return mailSessionJndiName;
    }

    /**
     * Gets the name of the {@link org.nuxeo.mail.MailSender} to use.
     *
     * @since 2023.4
     */
    public String getMailSenderName() {
        return mailSenderName;
    }

}
