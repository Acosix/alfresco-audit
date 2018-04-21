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
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import de.acosix.alfresco.audit.repo.batch.AuditUserInfo;
import de.acosix.alfresco.audit.repo.batch.PersonAuditWorker;
import de.acosix.alfresco.audit.repo.batch.PersonAuditWorker.PersonAuditQueryMode;
import de.acosix.alfresco.utility.repo.batch.PersonBatchWorkProvider;

/**
 * This web script queries active/inactive users within a specific timeframe into the past from the data of an audit application within
 * Alfresco. Only users that currently exist as person nodes in the system will be considered for inclusion in the report.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuditUserGet extends DeclarativeWebScript implements InitializingBean, ApplicationContextAware
{

    public static enum LookBackMode
    {
        YEARS, MONTHS, DAYS;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditUserGet.class);

    protected static final int DEFAULT_LOOK_BACK_DAYS = 90;

    protected static final int DEFAULT_LOOK_BACK_MONTHS = 3;

    protected static final int DEFAULT_LOOK_BACK_YEARS = 1;

    protected static final int DEFAULT_WORKER_THREADS = 4;

    protected static final int DEFAULT_BATCH_SIZE = 20;

    protected static final int DEFAULT_LOGGING_INTERVAL = 100;

    protected ApplicationContext applicationContext;

    protected NamespaceService namespaceService;

    protected TransactionService transactionService;

    protected NodeService nodeService;

    protected PersonService personService;

    protected SearchService searchService;

    protected AuditService auditService;

    protected LookBackMode lookBackMode = LookBackMode.MONTHS;

    protected Object authorisationService;

    protected Method isAuthorizedHandle;

    protected Method isDeauthorizedHandle;

    protected int lookBackDays = DEFAULT_LOOK_BACK_DAYS;

    protected int lookBackMonths = DEFAULT_LOOK_BACK_MONTHS;

    protected int lookBackYears = DEFAULT_LOOK_BACK_YEARS;

    protected int workerThreads = DEFAULT_WORKER_THREADS;

    protected int batchSize = DEFAULT_BATCH_SIZE;

    protected int loggingInterval = DEFAULT_LOGGING_INTERVAL;

    protected boolean queryActiveUsers = true;

    protected String auditApplicationName = "alfresco-access";

    // in default alfresco-access we query by directly associated user
    protected String userAuditPath = null;

    // if audit data contains a date range / time frame we need to extract semantically separate from/to values
    protected String dateFromAuditPath = null;

    protected String dateToAuditPath = null;

    // simple "effective" date in audit data
    protected String dateAuditPath = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "personService", this.personService);
        PropertyCheck.mandatory(this, "searchService", this.searchService);
        PropertyCheck.mandatory(this, "auditService", this.auditService);
        PropertyCheck.mandatory(this, "lookBackMode", this.lookBackMode);

        if (this.lookBackDays < 1)
        {
            throw new IllegalStateException("lookBackDays must be a positive integer");
        }

        if (this.lookBackMonths < 1)
        {
            throw new IllegalStateException("lookBackMonths must be a positive integer");
        }

        if (this.lookBackYears < 1)
        {
            throw new IllegalStateException("lookBackYears must be a positive integer");
        }

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
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param transactionService
     *            the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param personService
     *            the personService to set
     */
    public void setPersonService(final PersonService personService)
    {
        this.personService = personService;
    }

    /**
     * @param searchService
     *            the searchService to set
     */
    public void setSearchService(final SearchService searchService)
    {
        this.searchService = searchService;
    }

    /**
     * @param auditService
     *            the auditService to set
     */
    public void setAuditService(final AuditService auditService)
    {
        this.auditService = auditService;
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
     * @param lookBackMode
     *            the lookBackMode to set
     */
    public void setLookBackMode(final LookBackMode lookBackMode)
    {
        this.lookBackMode = lookBackMode;
    }

    /**
     * @param lookBackDays
     *            the lookBackDays to set
     */
    public void setLookBackDays(final int lookBackDays)
    {
        this.lookBackDays = lookBackDays;
    }

    /**
     * @param lookBackMonths
     *            the lookBackMonths to set
     */
    public void setLookBackMonths(final int lookBackMonths)
    {
        this.lookBackMonths = lookBackMonths;
    }

    /**
     * @param lookBackYears
     *            the lookBackYears to set
     */
    public void setLookBackYears(final int lookBackYears)
    {
        this.lookBackYears = lookBackYears;
    }

    /**
     * @param workerThreads
     *            the workerThreads to set
     */
    public void setWorkerThreads(final int workerThreads)
    {
        this.workerThreads = workerThreads;
    }

    /**
     * @param batchSize
     *            the batchSize to set
     */
    public void setBatchSize(final int batchSize)
    {
        this.batchSize = batchSize;
    }

    /**
     * @param loggingInterval
     *            the loggingInterval to set
     */
    public void setLoggingInterval(final int loggingInterval)
    {
        this.loggingInterval = loggingInterval;
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
     * @param auditApplicationName
     *            the auditApplicationName to set
     */
    public void setAuditApplicationName(final String auditApplicationName)
    {
        if (auditApplicationName == null || !auditApplicationName.trim().isEmpty())
        {
            this.auditApplicationName = auditApplicationName;
        }
    }

    /**
     * @param userAuditPath
     *            the userAuditPath to set
     */
    public void setUserAuditPath(final String userAuditPath)
    {
        if (userAuditPath == null || !userAuditPath.trim().isEmpty())
        {
            this.userAuditPath = userAuditPath;
        }
    }

    /**
     * @param dateFromAuditPath
     *            the dateFromAuditPath to set
     */
    public void setDateFromAuditPath(final String dateFromAuditPath)
    {
        if (dateFromAuditPath == null || !dateFromAuditPath.trim().isEmpty())
        {
            this.dateFromAuditPath = dateFromAuditPath;
        }
    }

    /**
     * @param dateToAuditPath
     *            the dateToAuditPath to set
     */
    public void setDateToAuditPath(final String dateToAuditPath)
    {
        if (dateToAuditPath == null || !dateToAuditPath.trim().isEmpty())
        {
            this.dateToAuditPath = dateToAuditPath;
        }
    }

    /**
     * @param dateAuditPath
     *            the dateAuditPath to set
     */
    public void setDateAuditPath(final String dateAuditPath)
    {
        if (dateAuditPath == null || !dateAuditPath.trim().isEmpty())
        {
            this.dateAuditPath = dateAuditPath;
        }
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

        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);

        final String lookBackModeParam = req.getParameter("lookBackMode");
        LookBackMode lookBackMode = this.lookBackMode;
        if (lookBackModeParam != null)
        {
            try
            {
                lookBackMode = LookBackMode.valueOf(lookBackModeParam.toUpperCase(Locale.ENGLISH));
            }
            catch (final IllegalArgumentException e)
            {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Unsupported mode: " + lookBackModeParam);
            }
        }

        final String lookBackAmountParam = req.getParameter("lookBackAmount");
        int lookBackAmount = -1;
        if (lookBackAmountParam != null)
        {
            if (lookBackAmountParam.matches("^\\d+$"))
            {
                lookBackAmount = Integer.parseInt(lookBackAmountParam, 10);
            }
            else
            {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Invalid value for lookBackAmount: " + lookBackAmountParam);
            }
        }

        switch (lookBackMode)
        {
            case DAYS:
                cal.add(Calendar.DATE, -(lookBackAmount > 0 ? lookBackAmount : this.lookBackDays));
                break;
            case MONTHS:
                cal.add(Calendar.MONTH, -(lookBackAmount > 0 ? lookBackAmount : this.lookBackMonths));
                break;
            case YEARS:
                cal.add(Calendar.YEAR, -(lookBackAmount > 0 ? lookBackAmount : this.lookBackYears));
                break;
            default:
                throw new UnsupportedOperationException("Unsupported mode: " + this.lookBackMode);
        }

        final long fromTime = cal.getTimeInMillis();

        int effectiveWorkerThreads = this.workerThreads;
        int effectiveBatchSize = this.batchSize;

        final String workerThreadsParam = req.getParameter("workerThreads");
        if (workerThreadsParam != null)
        {
            effectiveWorkerThreads = Integer.parseInt(workerThreadsParam, 10);
            if (effectiveWorkerThreads < 1)
            {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "workerThreads must be a positive integer");
            }
        }

        final String batchSizeParam = req.getParameter("batchSize");
        if (batchSizeParam != null)
        {
            effectiveBatchSize = Integer.parseInt(batchSizeParam, 10);
            if (effectiveBatchSize < 1)
            {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "batchSize must be a positive integer");
            }
        }

        final List<AuditUserInfo> auditUsers = this.queryAuditUsers(fromTime, this.workerThreads, this.batchSize, this.loggingInterval);
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

    protected List<AuditUserInfo> queryAuditUsers(final long fromTime, final int workerThreads, final int batchSize,
            final int loggingInterval)
    {
        final PersonAuditWorker personAuditWorker = new PersonAuditWorker(fromTime,
                this.queryActiveUsers ? PersonAuditQueryMode.ACTIVE_ONLY : PersonAuditQueryMode.INACTIVE_ONLY, this.auditApplicationName,
                this.nodeService, this.auditService);

        personAuditWorker.setUserAuditPath(this.userAuditPath);
        personAuditWorker.setDateAuditPath(this.dateAuditPath);
        personAuditWorker.setDateFromAuditPath(this.dateFromAuditPath);
        personAuditWorker.setDateToAuditPath(this.dateToAuditPath);
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

        final BatchProcessor<NodeRef> processor = new BatchProcessor<>(AuditUserGet.class.getName(),
                this.transactionService.getRetryingTransactionHelper(),
                new PersonBatchWorkProvider(this.namespaceService, this.nodeService, this.personService, this.searchService), workerThreads,
                batchSize, null, LogFactory.getLog(AuditUserGet.class), loggingInterval);

        processor.process(personAuditWorker, true);

        final List<AuditUserInfo> auditUsers = new ArrayList<>(personAuditWorker.getUsers());
        Collections.sort(auditUsers);
        return auditUsers;
    }
}
