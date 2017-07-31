/*
 * Copyright 2017 Acosix GmbH
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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorkerAdaptor;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.audit.AuditQueryParameters;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.cmr.audit.AuditService.AuditQueryCallback;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.ParameterCheck;
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

import de.acosix.alfresco.audit.repo.web.scripts.AuditUserGet.AuditUserInfo.AuthorisedState;
import de.acosix.alfresco.utility.repo.batch.PersonBatchWorkProvider;

/**
 * This web script queries active/inactive users within a specific timeframe into the past from the data of an audit application within
 * Alfresco. Only users that currently exist as person nodes in the system will be considered for inclusion in the report.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuditUserGet extends DeclarativeWebScript implements InitializingBean, ApplicationContextAware
{

    public static class AuditUserInfo implements Comparable<AuditUserInfo>
    {

        public static enum AuthorisedState
        {
            AUTHORISED, DEAUTHORISED, UNKNOWN;
        }

        protected final String userName;

        protected final NodeRef personRef;

        protected final AuthorisedState authorisedState;

        public AuditUserInfo(final String userName, final NodeRef personRef, final AuthorisedState authorisedState)
        {
            ParameterCheck.mandatoryString("userName", userName);
            ParameterCheck.mandatory("personRef", personRef);
            ParameterCheck.mandatory("authorisedState", authorisedState);
            this.userName = userName;
            this.personRef = personRef;
            this.authorisedState = authorisedState;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(final AuditUserInfo o)
        {
            return this.userName.compareTo(o.getUserName());
        }

        /**
         * @return the userName
         */
        public String getUserName()
        {
            return this.userName;
        }

        /**
         * @return the personRef
         */
        public NodeRef getPersonRef()
        {
            return this.personRef;
        }

        /**
         * @return the authorisedState
         */
        public AuthorisedState getAuthorisedState()
        {
            return this.authorisedState;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.userName == null) ? 0 : this.userName.hashCode());
            result = prime * result + ((this.personRef == null) ? 0 : this.personRef.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final AuditUserInfo other = (AuditUserInfo) obj;
            if (this.userName == null)
            {
                if (other.userName != null)
                {
                    return false;
                }
            }
            else if (!this.userName.equals(other.userName))
            {
                return false;
            }
            if (this.personRef == null)
            {
                if (other.personRef != null)
                {
                    return false;
                }
            }
            else if (!this.personRef.equals(other.personRef))
            {
                return false;
            }
            return true;
        }
    }

    /**
     *
     * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
     */
    public static class ActiveAuditUserInfo extends AuditUserInfo
    {

        protected final Date firstActive;

        protected final Date lastActive;

        public ActiveAuditUserInfo(final String userName, final NodeRef personRef, final AuthorisedState authorisedState,
                final Date firstActive, final Date lastActive)
        {
            super(userName, personRef, authorisedState);
            ParameterCheck.mandatory("firstActive", firstActive);
            ParameterCheck.mandatory("lastActive", lastActive);
            this.firstActive = new Date(firstActive.getTime());
            this.lastActive = new Date(lastActive.getTime());
        }

        /**
         * @return the firstActive
         */
        public Date getFirstActive()
        {
            return new Date(this.firstActive.getTime());
        }

        /**
         * @return the lastActive
         */
        public Date getLastActive()
        {
            return new Date(this.lastActive.getTime());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((this.firstActive == null) ? 0 : this.firstActive.hashCode());
            result = prime * result + ((this.lastActive == null) ? 0 : this.lastActive.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (!super.equals(obj))
            {
                return false;
            }
            final ActiveAuditUserInfo other = (ActiveAuditUserInfo) obj;
            if (this.firstActive == null)
            {
                if (other.firstActive != null)
                {
                    return false;
                }
            }
            else if (!this.firstActive.equals(other.firstActive))
            {
                return false;
            }
            if (this.lastActive == null)
            {
                if (other.lastActive != null)
                {
                    return false;
                }
            }
            else if (!this.lastActive.equals(other.lastActive))
            {
                return false;
            }
            return true;
        }

    }

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
        final PersonAuditWorker personAuditWorker = new PersonAuditWorker(fromTime, this.queryActiveUsers);
        final BatchProcessor<NodeRef> processor = new BatchProcessor<>(AuditUserGet.class.getName(),
                this.transactionService.getRetryingTransactionHelper(),
                new PersonBatchWorkProvider(this.namespaceService, this.nodeService, this.personService, this.searchService), workerThreads,
                batchSize, null, LogFactory.getLog(AuditUserGet.class), loggingInterval);
        processor.process(personAuditWorker, true);

        final List<AuditUserInfo> activeUsers = new ArrayList<>(personAuditWorker.getUsers());
        Collections.sort(activeUsers);
        return activeUsers;
    }

    protected class PersonAuditWorker extends BatchProcessWorkerAdaptor<NodeRef>
    {

        private final List<AuditUserInfo> users = new ArrayList<>();

        private final List<AuditUserInfo> usersSync = Collections.synchronizedList(this.users);

        private final long fromTime;

        private final boolean queryActiveUsers;

        private final String runAsUser = AuthenticationUtil.getRunAsUser();

        protected PersonAuditWorker(final long fromTime, final boolean queryActiveUsers)
        {
            this.fromTime = fromTime;
            this.queryActiveUsers = queryActiveUsers;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void beforeProcess() throws Throwable
        {
            AuthenticationUtil.setRunAsUser(this.runAsUser);
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public void process(final NodeRef personRef) throws Throwable
        {
            final Map<QName, Serializable> personProperties = AuditUserGet.this.nodeService.getProperties(personRef);
            final String userName = DefaultTypeConverter.INSTANCE.convert(String.class, personProperties.get(ContentModel.PROP_USERNAME));

            // TODO generalise to use alternative audit applications and optional search keys
            final AuditQueryParameters aqp = new AuditQueryParameters();
            aqp.setApplicationName("alfresco-access");

            aqp.setForward(true);
            aqp.setUser(userName);
            aqp.setFromTime(this.fromTime);

            final AtomicLong firstActive = new AtomicLong(-1);
            final AtomicLong lastActive = new AtomicLong(-1);
            AuditUserGet.this.auditService.auditQuery(new AuditQueryCallback()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean valuesRequired()
                {
                    return false;
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntry(final Long entryId, final String applicationName, final String user, final long time,
                        final Map<String, Serializable> values)
                {
                    firstActive.set(time);
                    return true;
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntryError(final Long entryId, final String errorMsg, final Throwable error)
                {
                    return true;
                }

            }, aqp, 1);

            aqp.setForward(false);
            AuditUserGet.this.auditService.auditQuery(new AuditQueryCallback()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean valuesRequired()
                {
                    return false;
                }

                @Override
                public boolean handleAuditEntry(final Long entryId, final String applicationName, final String user, final long time,
                        final Map<String, Serializable> values)
                {
                    lastActive.set(time);
                    return true;
                }

                @Override
                public boolean handleAuditEntryError(final Long entryId, final String errorMsg, final Throwable error)
                {
                    return true;
                }

            }, aqp, 1);

            final AuthorisedState authorisedState;
            if (AuditUserGet.this.isAuthorizedHandle != null && AuditUserGet.this.isDeauthorizedHandle != null)
            {
                final boolean isAuthorised = Boolean.TRUE
                        .equals(AuditUserGet.this.isAuthorizedHandle.invoke(AuditUserGet.this.authorisationService, userName));
                final boolean isDeauthorised = Boolean.TRUE
                        .equals(AuditUserGet.this.isDeauthorizedHandle.invoke(AuditUserGet.this.authorisationService, userName));
                authorisedState = isAuthorised ? AuthorisedState.AUTHORISED
                        : (isDeauthorised ? AuthorisedState.DEAUTHORISED : AuthorisedState.UNKNOWN);
            }
            else
            {
                authorisedState = AuthorisedState.UNKNOWN;
            }

            // we found an instance of the user
            if (this.queryActiveUsers && firstActive.get() != -1)
            {
                this.usersSync.add(new ActiveAuditUserInfo(userName, personRef, authorisedState, new Date(firstActive.get()),
                        new Date(lastActive.get())));
            }
            else if (!this.queryActiveUsers && firstActive.get() == -1)
            {
                this.usersSync.add(new AuditUserInfo(userName, personRef, authorisedState));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterProcess() throws Throwable
        {
            AuthenticationUtil.clearCurrentSecurityContext();
        }

        /**
         * @return the activeUsers
         */
        public List<AuditUserInfo> getUsers()
        {
            return Collections.unmodifiableList(this.users);
        }

    }
}
