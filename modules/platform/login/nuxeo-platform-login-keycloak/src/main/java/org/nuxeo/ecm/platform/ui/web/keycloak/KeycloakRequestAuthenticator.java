/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     François Maturel
 */
package org.nuxeo.ecm.platform.ui.web.keycloak;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.catalina.authenticator.FormAuthenticator;
import org.apache.catalina.connector.Request;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.AuthOutcome;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.tomcat.CatalinaCookieTokenStore;
import org.keycloak.adapters.tomcat.CatalinaHttpFacade;
import org.keycloak.adapters.tomcat.CatalinaSessionTokenStore;
import org.keycloak.adapters.tomcat.CatalinaUserSessionManagement;
import org.keycloak.adapters.tomcat.GenericPrincipalFactory;
import org.keycloak.adapters.tomcat.KeycloakAuthenticatorValve;
import org.keycloak.enums.TokenStore;
import org.keycloak.representations.AccessToken;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;

/**
 * @since 7.4
 */
public class KeycloakRequestAuthenticator extends RequestAuthenticator {

    private static final Logger log = LogManager.getLogger(KeycloakRequestAuthenticator.class);

    public static final String KEYCLOAK_ACCESS_TOKEN = "KEYCLOAK_ACCESS_TOKEN";

    private CatalinaUserSessionManagement userSessionManagement = new CatalinaUserSessionManagement();

    protected Request request;

    protected HttpServletResponse response;

    protected LoginConfig loginConfig;

    public HttpFacade getFacade() {
        return facade;
    }

    public KeycloakRequestAuthenticator(Request request, HttpServletResponse response, CatalinaHttpFacade facade,
            KeycloakDeployment deployment) {
        super(facade, deployment);
        this.request = request;
        this.response = response;
        tokenStore = getTokenStore();
        sslRedirectPort = request.getConnector().getRedirectPort();
    }

    @Override
    public AuthOutcome authenticate() {
        AuthOutcome outcome = super.authenticate();
        if (outcome == AuthOutcome.AUTHENTICATED) {
            return AuthOutcome.AUTHENTICATED;
        }
        return AuthOutcome.FAILED;
    }

    protected boolean forwardToErrorPageInternal() {
        if (loginConfig == null) {
            return false;
        }
        LoginConfig config = (LoginConfig) loginConfig;
        if (config.getErrorPage() == null) {
            return false;
        }
        try {
            // XXX buggy, this class doesn't extend FormAuthenticator
            Method method = FormAuthenticator.class.getDeclaredMethod("forwardToErrorPage", Request.class,
                    HttpServletResponse.class, LoginConfig.class);
            method.setAccessible(true);
            method.invoke(this, request, response, config);
        } catch (Exception e) {
            String message = "Error occurred during Keycloak authentication";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
        return true;
    }

    protected GenericPrincipalFactory createPrincipalFactory() {
        return new GenericPrincipalFactory() {
            @Override
            protected GenericPrincipal createPrincipal(Principal userPrincipal, List<String> roles) {
                return new GenericPrincipal(userPrincipal.getName(), null, roles, userPrincipal, null);
            }
        };
    }

    protected AdapterTokenStore getTokenStore() {
        final String TOKEN_STORE_NOTE = "TOKEN_STORE_NOTE";

        AdapterTokenStore store = (AdapterTokenStore) request.getNote(TOKEN_STORE_NOTE);
        if (store != null) {
            return store;
        }

        if (deployment.getTokenStore() == TokenStore.SESSION) {
            store = new CatalinaSessionTokenStore(request, deployment, userSessionManagement, createPrincipalFactory(),
                    new KeycloakAuthenticatorValve());
        } else {
            store = new CatalinaCookieTokenStore(request, facade, deployment, createPrincipalFactory());
        }

        request.setNote(TOKEN_STORE_NOTE, store);
        return store;
    }

    @Override
    protected OAuthRequestAuthenticator createOAuthAuthenticator() {
        return new OAuthRequestAuthenticator(this, facade, deployment, sslRedirectPort, tokenStore) {

            @Override
            protected String getRequestUrl() {
                final StringBuilder sb = new StringBuilder(VirtualHostHelper.getServerURL(request));
                if (VirtualHostHelper.getServerURL(request).endsWith("/")) {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append(request.getRequestURI());
                if (request.getQueryString() != null) {
                    sb.append("?");
                    sb.append(request.getQueryString());
                }
                return sb.toString();
            }

            @Override
            protected String stripOauthParametersFromRedirect() {
                return super.stripOauthParametersFromRedirect().replace(VirtualHostHelper.getServerURL(request, true),
                        VirtualHostHelper.getServerURL(request));
            }
        };
    }

    @Override
    protected void completeOAuthAuthentication(final KeycloakPrincipal<RefreshableKeycloakSecurityContext> skp) {
        final AccessToken token = skp.getKeycloakSecurityContext().getToken();
        request.setAttribute(KEYCLOAK_ACCESS_TOKEN, token);
    }

    @Override
    protected void completeBearerAuthentication(KeycloakPrincipal<RefreshableKeycloakSecurityContext> skp,
            String method) {
        completeOAuthAuthentication(skp);
    }

    @Override
    protected String changeHttpSessionId(boolean create) {
        HttpSession session = request.getSession(create);
        return session != null ? session.getId() : null;
    }

}
