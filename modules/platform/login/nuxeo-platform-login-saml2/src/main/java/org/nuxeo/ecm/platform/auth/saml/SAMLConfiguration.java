/*
 * (C) Copyright 2015-2023 Nuxeo (http://nuxeo.com/) and others.
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
 *      Nelson Silva
 */
package org.nuxeo.ecm.platform.auth.saml;

import static java.util.stream.Collectors.toSet;
import static org.nuxeo.ecm.platform.auth.saml.SAMLUtils.buildSAMLObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.platform.auth.saml.key.KeyManager;
import org.nuxeo.ecm.platform.auth.saml.processor.binding.SAMLInboundBinding;
import org.nuxeo.runtime.api.Framework;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.signature.KeyInfo;

/**
 * @since 7.3
 */
public class SAMLConfiguration {

    private static final Logger log = LogManager.getLogger(SAMLConfiguration.class);

    public static final String ENTITY_ID = "nuxeo.saml2.entityId";

    public static final String LOGIN_BINDINGS = "nuxeo.saml2.loginBindings";

    public static final String AUTHN_REQUESTS_SIGNED = "nuxeo.saml2.authnRequestsSigned";

    public static final String WANT_ASSERTIONS_SIGNED = "nuxeo.saml2.wantAssertionsSigned";

    public static final String SKEW_TIME_MS = "nuxeo.saml2.skewTimeMs";

    public static final int DEFAULT_SKEW_TIME_MS = 1000 * 60; // 1 minute;

    public static final String BINDING_PREFIX = "urn:oasis:names:tc:SAML:2.0:bindings";

    public static final String DEFAULT_LOGIN_BINDINGS = "HTTP-Redirect,HTTP-POST";

    public static final Collection<String> nameID = Arrays.asList(NameIDType.EMAIL, NameIDType.TRANSIENT,
            NameIDType.PERSISTENT, NameIDType.UNSPECIFIED, NameIDType.X509_SUBJECT);

    private SAMLConfiguration() {

    }

    public static String getEntityId() {
        return Framework.getProperty(ENTITY_ID, Framework.getProperty("nuxeo.url"));
    }

    public static List<String> getLoginBindings() {
        Set<String> supportedBindings = Stream.of(SAMLInboundBinding.values())
                                              .map(SAMLInboundBinding::getBindingURI)
                                              .collect(toSet());
        List<String> bindings = new ArrayList<>();
        String[] suffixes = Framework.getProperty(LOGIN_BINDINGS, DEFAULT_LOGIN_BINDINGS).split(",");
        for (String sufix : suffixes) {
            String binding = BINDING_PREFIX + ":" + sufix;
            if (supportedBindings.contains(binding)) {
                bindings.add(binding);
            } else {
                log.warn("Unknown SAML binding: {}", binding);
            }
        }
        return bindings;
    }

    public static boolean getAuthnRequestsSigned() {
        return Boolean.parseBoolean(Framework.getProperty(AUTHN_REQUESTS_SIGNED));
    }

    public static boolean getWantAssertionsSigned() {
        return Boolean.parseBoolean(Framework.getProperty(WANT_ASSERTIONS_SIGNED));
    }

    public static int getSkewTimeMillis() {
        String skewTimeMs = Framework.getProperty(SKEW_TIME_MS);
        return skewTimeMs != null ? Integer.parseInt(skewTimeMs) : DEFAULT_SKEW_TIME_MS;
    }

    /**
     * Returns the {@link EntityDescriptor} for the Nuxeo Service Provider
     */
    public static EntityDescriptor getEntityDescriptor(String baseURL) {

        // Entity Descriptor
        EntityDescriptor descriptor = buildSAMLObject(EntityDescriptor.DEFAULT_ELEMENT_NAME);
        // descriptor.setID(id);
        descriptor.setEntityID(getEntityId());

        // SPSSO Descriptor
        descriptor.getRoleDescriptors().add(getSPSSODescriptor(baseURL));

        return descriptor;
    }

    /**
     * Returns the {@link SPSSODescriptor} for the Nuxeo Service Provider
     */
    public static SPSSODescriptor getSPSSODescriptor(String baseURL) {
        SPSSODescriptor spDescriptor = buildSAMLObject(SPSSODescriptor.DEFAULT_ELEMENT_NAME);
        spDescriptor.setAuthnRequestsSigned(getAuthnRequestsSigned());
        spDescriptor.setWantAssertionsSigned(getWantAssertionsSigned());
        spDescriptor.addSupportedProtocol(SAMLConstants.SAML20P_NS);

        // Name ID
        spDescriptor.getNameIDFormats().addAll(buildNameIDFormats(nameID));

        // Generate key info
        KeyManager keyManager = Framework.getService(KeyManager.class);
        if (keyManager.getSigningCredential() != null) {
            spDescriptor.getKeyDescriptors()
                        .add(buildKeyDescriptor(UsageType.SIGNING,
                                generateKeyInfoForCredential(keyManager.getSigningCredential())));
        }
        if (keyManager.getEncryptionCredential() != null) {
            spDescriptor.getKeyDescriptors()
                        .add(buildKeyDescriptor(UsageType.ENCRYPTION,
                                generateKeyInfoForCredential(keyManager.getEncryptionCredential())));
        }
        if (keyManager.getTlsCredential() != null) {
            spDescriptor.getKeyDescriptors()
                        .add(buildKeyDescriptor(UsageType.UNSPECIFIED,
                                generateKeyInfoForCredential(keyManager.getTlsCredential())));
        }

        // LOGIN
        int index = 0;
        for (String binding : getLoginBindings()) {
            AssertionConsumerService consumer = buildSAMLObject(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
            consumer.setLocation(baseURL);
            consumer.setBinding(binding);
            consumer.setIsDefault(index == 0);
            consumer.setIndex(index++);
            spDescriptor.getAssertionConsumerServices().add(consumer);
        }

        // LOGOUT - SAML2_POST_BINDING_URI
        SingleLogoutService logoutService = buildSAMLObject(SingleLogoutService.DEFAULT_ELEMENT_NAME);
        logoutService.setLocation(baseURL);
        logoutService.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        spDescriptor.getSingleLogoutServices().add(logoutService);
        return spDescriptor;
    }

    private static KeyDescriptor buildKeyDescriptor(UsageType type, KeyInfo key) {
        KeyDescriptor descriptor = buildSAMLObject(KeyDescriptor.DEFAULT_ELEMENT_NAME);
        descriptor.setUse(type);
        descriptor.setKeyInfo(key);
        return descriptor;
    }

    private static Collection<NameIDFormat> buildNameIDFormats(Collection<String> nameIDs) {

        Collection<NameIDFormat> formats = new LinkedList<>();

        // Populate nameIDs
        for (String nameIDValue : nameIDs) {
            NameIDFormat nameID = buildSAMLObject(NameIDFormat.DEFAULT_ELEMENT_NAME);
            nameID.setURI(nameIDValue);
            formats.add(nameID);
        }

        return formats;
    }

    private static KeyInfo generateKeyInfoForCredential(Credential credential) {
        try {
            KeyInfoGenerator keyInfoGenerator = DefaultSecurityConfigurationBootstrap.buildBasicKeyInfoGeneratorManager()
                                                                                     .getDefaultManager()
                                                                                     .getFactory(credential)
                                                                                     .newInstance();
            return keyInfoGenerator.generate(credential);
        } catch (SecurityException e) {
            log.error("Failed to  generate key info.");
        }
        return null;
    }
}
