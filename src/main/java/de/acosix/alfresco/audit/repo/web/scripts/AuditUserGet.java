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
package de.acosix.alfresco.audit.repo.web.scripts;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.error.AlfrescoRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import de.acosix.alfresco.audit.repo.batch.AuditUserInfo;
import de.acosix.alfresco.audit.repo.batch.PersonAuditWorker;
import de.acosix.alfresco.audit.repo.batch.PersonAuditWorker.PersonAuditQueryMode;

/**
 * This web script queries active/inactive users within a specific timeframe into the past from the data of an audit application within
 * Alfresco. Only users that currently exist as person nodes in the system will be considered for inclusion in the report.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuditUserGet extends AbstractAuditUserWebScript
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditUserGet.class);

    protected Object authorisationService;

    protected Method isAuthorizedHandle;

    protected Method isDeauthorizedHandle;

    protected boolean queryActiveUsers = true;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        if (this.authorisationService == null)
        {
            // try to lookup the Enterprise Edition service
            Class<?> beanCls;
            try
            {
                beanCls = Class.forName("org.alfresco.enterprise.repo.authorization.AuthorizationService");
                this.authorisationService = this.applicationContext.getBean("AuthorizationService", beanCls);

                this.isAuthorizedHandle = beanCls.getMethod("isAuthorized", String.class);
                this.isDeauthorizedHandle = beanCls.getMethod("isDeauthorized", String.class);
            }
            catch (final ClassNotFoundException | NoSuchBeanDefinitionException ex)
            {
                LOGGER.info("Enterprise AuthorizationService is not available - must be running in Alfresco Community Edition");
            }
            catch (final NoSuchMethodException ex)
            {
                LOGGER.warn("Enterprise AuthorizationService does not support either isAuthorized or isDeauthorized operation");
            }
        }
    }

    /**
     * @param authorisationService
     *            the authorisationService to set
     */
    public void setAuthorisationService(final Object authorisationService)
    {
        this.authorisationService = authorisationService;
    }

    /**
     * @param queryActiveUsers
     *            the queryActiveUsers to set
     */
    public void setQueryActiveUsers(final boolean queryActiveUsers)
    {
        this.queryActiveUsers = queryActiveUsers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, Object> executeImpl(final WebScriptRequest req, final Status status, final Cache cache)
    {
        Map<String, Object> model = super.executeImpl(req, status, cache);
        if (model == null)
        {
            model = new HashMap<>();
        }

        final AuditUserWebScriptParameters parameters = this.parseRequest(() -> {
            return new AuditUserWebScriptParameters();
        }, req);

        final List<AuditUserInfo> auditUsers = this
                .queryAuditUsers(this.queryActiveUsers ? PersonAuditQueryMode.ACTIVE_ONLY : PersonAuditQueryMode.INACTIVE_ONLY, parameters);

        LOGGER.debug("Query for {} users using {} yielded {} results", this.queryActiveUsers ? "active" : "inactive", parameters,
                auditUsers.size());
        LOGGER.trace("Detailed {} user results: {}", this.queryActiveUsers ? "active" : "inactive", auditUsers);

        final List<Object> modelUsers = new ArrayList<>();
        model.put("users", modelUsers);
        auditUsers.forEach(activeUser -> {
            final Map<String, Object> modelActiveUser = new HashMap<>();
            modelActiveUser.put("info", activeUser);
            modelActiveUser.put("node", activeUser.getPersonRef());
            modelUsers.add(modelActiveUser);
        });

        return model;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected <T extends AuditUserWebScriptParameters> PersonAuditWorker createBatchWorker(final PersonAuditQueryMode mode,
            final T parameters)
    {
        final PersonAuditWorker personAuditWorker = super.createBatchWorker(mode, parameters);

        if (this.isAuthorizedHandle != null && this.isDeauthorizedHandle != null)
        {
            personAuditWorker.setIsAuthorisedCheck(userName -> {
                try
                {
                    final Object result = this.isAuthorizedHandle.invoke(this.authorisationService, userName);
                    return (Boolean) result;
                }
                catch (InvocationTargetException | IllegalAccessException e)
                {
                    throw new AlfrescoRuntimeException("Unexpected error invoking Enterprise-only API", e);
                }
            });
            personAuditWorker.setIsDeauthorisedCheck(userName -> {
                try
                {
                    final Object result = this.isDeauthorizedHandle.invoke(this.authorisationService, userName);
                    return (Boolean) result;
                }
                catch (InvocationTargetException | IllegalAccessException e)
                {
                    throw new AlfrescoRuntimeException("Unexpected error invoking Enterprise-only API", e);
                }
            });
        }

        return personAuditWorker;
    }
}
