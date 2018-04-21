/*
 * Copyright 2017, 2018 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.audit.repo.web.auth;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.repo.audit.model.AuditApplication;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.web.auth.AuthenticationListener;
import org.alfresco.repo.web.auth.WebCredentials;
import org.alfresco.repo.web.scripts.servlet.BasicHttpAuthenticatorFactory.BasicHttpAuthenticator;
import org.alfresco.repo.webdav.auth.BaseAuthenticationFilter;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.acosix.alfresco.audit.repo.AuditModuleConstants;

/**
 * This class provides the capability to audit authentication events that occur on the {@link BaseAuthenticationFilter authentication
 * filter} or {@link BasicHttpAuthenticator HTTP authenticator} layer, and which may avoid the traditional
 * {@link AuthenticationService#authenticate(String, char[]) password-based authentication API} that would produce audit entries via the
 * {@code alfresco-api} audit data producer.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuthenticationAuditingListener implements AuthenticationListener, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationAuditingListener.class);

    protected AuditComponent auditComponent;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "auditComponent", this.auditComponent);
    }

    /**
     * @param auditComponent
     *            the auditComponent to set
     */
    public void setAuditComponent(final AuditComponent auditComponent)
    {
        this.auditComponent = auditComponent;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void userAuthenticated(final WebCredentials credentials)
    {
        final String rootPath = AuditApplication.buildPath(AuditModuleConstants.AUDIT_PRODUCER_ROOT_PATH,
                AuthenticationAuditingListener.class.getSimpleName(), "authentication");
        final Map<String, Serializable> auditMap = new HashMap<>();

        final String fullyAuthenticatedUser = AuthenticationUtil.getFullyAuthenticatedUser();
        auditMap.put("userName", fullyAuthenticatedUser);

        // interface of credentials and all of its implementations provide no accessors to get meaningful data
        auditMap.put("credentialsType", credentials.getClass().getName());

        LOGGER.debug("Recording authentication audit data {}", auditMap);
        // explicitly avoid the user filter
        this.auditComponent.recordAuditValuesWithUserFilter(rootPath, auditMap, false);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void authenticationFailed(final WebCredentials credentials, final Exception ex)
    {
        final String rootPath = AuditApplication.buildPath(AuditModuleConstants.AUDIT_PRODUCER_ROOT_PATH,
                AuthenticationAuditingListener.class.getSimpleName(), "failedAuthentication");
        final Map<String, Serializable> auditMap = new HashMap<>();

        // interface of credentials and all of its implementations provide no accessors to get meaningful data
        auditMap.put("credentialsType", credentials.getClass().getName());
        auditMap.put("error", ex);

        LOGGER.debug("Recording failed authentication audit data {}", auditMap);
        // explicitly avoid the user filter
        this.auditComponent.recordAuditValuesWithUserFilter(rootPath, auditMap, false);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void authenticationFailed(final WebCredentials credentials)
    {
        final String rootPath = AuditApplication.buildPath(AuditModuleConstants.AUDIT_PRODUCER_ROOT_PATH,
                AuthenticationAuditingListener.class.getSimpleName(), "failedAuthentication");
        final Map<String, Serializable> auditMap = new HashMap<>();

        // interface of credentials and all of its implementations provide no accessors to get meaningful data
        auditMap.put("credentialsType", credentials.getClass().getName());

        LOGGER.debug("Recording failed authentication audit data {}", auditMap);
        // explicitly avoid the user filter
        this.auditComponent.recordAuditValuesWithUserFilter(rootPath, auditMap, false);
    }

}
