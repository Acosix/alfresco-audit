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
package de.acosix.alfresco.audit.repo.batch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorkerAdaptor;
import org.alfresco.repo.domain.audit.AuditEntryEntity;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.audit.AuditQueryParameters;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.cmr.audit.AuditService.AuditQueryCallback;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.audit.repo.batch.AuditUserInfo.AuthorisedState;

/**
 * This batch process worker implementation processes a {@link ContentModel#TYPE_PERSON cm:person} node for a user and determines based on
 * audit data when / if that user has been last active, and optionally whether the user is currently "authorised" or "deauthorised", meaning
 * they have been assigned / revoked a user license (Alfresco Enterprise-only).
 *
 * @author Axel Faust
 */
public class PersonAuditWorker extends BatchProcessWorkerAdaptor<NodeRef>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonAuditWorker.class);

    /**
     * The operation mode for an instance of {@link PersonAuditWorker}
     *
     * @author Axel Faust
     */
    public static enum PersonAuditQueryMode
    {
        /**
         * Collect only users that have been active based on audit data
         */
        ACTIVE_ONLY,
        /**
         * Collect only users without any evidence of being active based on audit data
         */
        INACTIVE_ONLY,
        /**
         * Collect all users
         */
        ANY;
    }

    protected final String runAsUser = AuthenticationUtil.getRunAsUser();

    protected final List<AuditUserInfo> users = new ArrayList<>();

    protected final List<AuditUserInfo> usersSync = Collections.synchronizedList(this.users);

    protected final long fromTime;

    protected final PersonAuditQueryMode queryMode;

    protected final String auditApplicationName;

    protected String userAuditPath;

    protected String dateAuditPath;

    protected String dateFromAuditPath;

    protected String dateToAuditPath;

    protected final NodeService nodeService;

    protected final AuditService auditService;

    protected Function<String, Boolean> isAuthorisedCheck;

    protected Function<String, Boolean> isDeauthorisedCheck;

    /**
     * Constructs a new instance of this worker with all mandatory configuration.
     *
     * @param fromTime
     *            the start offset for any audit data querying as a regular timestamp
     * @param queryMode
     *            the mode of how person nodes should be processed with regards to the {@link #getUsers() aggregated result}
     * @param auditApplicationName
     *            the name of the audit application to query for evidence of users being active
     * @param nodeService
     *            the service to use to access the properties of a person node
     * @param auditService
     *            the service to use for querying audit data
     */
    public PersonAuditWorker(final long fromTime, final PersonAuditQueryMode queryMode, final String auditApplicationName,
            final NodeService nodeService, final AuditService auditService)
    {
        ParameterCheck.mandatory("queryMode", queryMode);
        ParameterCheck.mandatoryString("auditApplicationName", auditApplicationName);
        ParameterCheck.mandatory("nodeService", nodeService);
        ParameterCheck.mandatory("auditService", auditService);
        this.fromTime = fromTime;
        this.queryMode = queryMode;

        this.auditApplicationName = auditApplicationName;

        this.nodeService = nodeService;
        this.auditService = auditService;
    }

    /**
     * Sets the audit path used to query for entries only pertaining the a specific user. If this is not set or set to {@code null}, this
     * worker will use the {@link AuditQueryParameters#setUser(String) user in whose name the audit entry was created} as a filter
     * condition.
     *
     * @param userAuditPath
     *            the userAuditPath to set
     */
    public void setUserAuditPath(final String userAuditPath)
    {
        this.userAuditPath = userAuditPath;
    }

    /**
     * Sets the audit path for extracting a custom date value distinct from the {@link AuditEntryEntity#getAuditTime() audit time} to be
     * used as evidence a user has been active at a specific time. This path is used in preference to generalised
     * {@link #setDateFromAuditPath(String) from-}/{@link #setDateToAuditPath(String) to-}ranges of user activity.
     *
     * @param dateAuditPath
     *            the dateAuditPath to set
     */
    public void setDateAuditPath(final String dateAuditPath)
    {
        this.dateAuditPath = dateAuditPath;
    }

    /**
     * Sets the audit path for extracting a custom date value to be considered the lower bound of an abstract timeframe in which a user has
     * been active. This is only used in conjunction with {@link #setDateToAuditPath(String) an upper bound}, and only if no
     * {@link #setDateAuditPath(String) audit path for a specific date} has been set or contains a value in the audit entry.
     *
     * @param dateFromAuditPath
     *            the dateFromAuditPath to set
     */
    public void setDateFromAuditPath(final String dateFromAuditPath)
    {
        this.dateFromAuditPath = dateFromAuditPath;
    }

    /**
     * Sets the audit path for extracting a custom date value to be considered the upper bound of an abstract timeframe in which a user has
     * been active. This is only used in conjunction with {@link #setDateFromAuditPath(String) an lower bound}, and only if no
     * {@link #setDateAuditPath(String) audit path for a specific date} has been set or contains a value in the audit entry.
     *
     * @param dateToAuditPath
     *            the dateToAuditPath to set
     */
    public void setDateToAuditPath(final String dateToAuditPath)
    {
        this.dateToAuditPath = dateToAuditPath;
    }

    /**
     * Sets the callback function to be used to determine if a specific user is considered "authorised" with regards to the Alfresco
     * Enterprise handling of licenses.
     *
     * @param isAuthorisedCheck
     *            the isAuthorisedCheck to set
     */
    public void setIsAuthorisedCheck(final Function<String, Boolean> isAuthorisedCheck)
    {
        this.isAuthorisedCheck = isAuthorisedCheck;
    }

    /**
     * Sets the callback function to be used to determine if a specific user is considered "deauthorised" with regards to the Alfresco
     * Enterprise handling of licenses.
     *
     * @param isDeauthorisedCheck
     *            the isDeauthorisedCheck to set
     */
    public void setIsDeauthorisedCheck(final Function<String, Boolean> isDeauthorisedCheck)
    {
        this.isDeauthorisedCheck = isDeauthorisedCheck;
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
        final Map<QName, Serializable> personProperties = this.nodeService.getProperties(personRef);
        final String userName = DefaultTypeConverter.INSTANCE.convert(String.class, personProperties.get(ContentModel.PROP_USERNAME));

        LOGGER.trace("Processing user {} from person node {}", userName, personRef);

        final AuditQueryParameters aqp = new AuditQueryParameters();
        aqp.setApplicationName(this.auditApplicationName);

        aqp.setForward(true);
        if (this.userAuditPath != null)
        {
            aqp.addSearchKey(this.userAuditPath, userName);
        }
        else
        {
            aqp.setUser(userName);
        }
        aqp.setFromTime(this.fromTime);

        final AtomicLong firstActive = new AtomicLong(-1);
        final AtomicLong lastActive = new AtomicLong(-1);

        final boolean useDateValuesFromAuditData = this.dateFromAuditPath != null || this.dateToAuditPath != null
                || this.dateAuditPath != null;

        this.auditService.auditQuery(new AuditQueryCallback()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public boolean valuesRequired()
            {
                return useDateValuesFromAuditData;
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public boolean handleAuditEntry(final Long entryId, final String applicationName, final String user, final long time,
                    final Map<String, Serializable> values)
            {
                Long effectiveFirstTime = null;
                Long effectiveLastTime = null;

                if (PersonAuditWorker.this.dateAuditPath != null)
                {
                    // implicitly handles ISO8601 text values
                    final Date dateValue = DefaultTypeConverter.INSTANCE.convert(Date.class,
                            values.get(PersonAuditWorker.this.dateAuditPath));
                    if (dateValue != null)
                    {
                        LOGGER.trace("Retrieved active date {} for user {} from audit entry {}", dateValue, userName, entryId);
                        effectiveFirstTime = Long.valueOf(dateValue.getTime());
                        effectiveLastTime = effectiveFirstTime;
                    }
                }

                if ((effectiveFirstTime == null || effectiveLastTime == null) && PersonAuditWorker.this.dateFromAuditPath != null
                        && PersonAuditWorker.this.dateToAuditPath != null)
                {
                    // implicitly handles ISO8601 text values
                    final Date dateFromValue = DefaultTypeConverter.INSTANCE.convert(Date.class,
                            values.get(PersonAuditWorker.this.dateFromAuditPath));
                    final Date dateToValue = DefaultTypeConverter.INSTANCE.convert(Date.class,
                            values.get(PersonAuditWorker.this.dateToAuditPath));
                    if (dateFromValue != null && dateToValue != null)
                    {
                        LOGGER.trace("Retrieved active date from/to-range {} to {} for user {} from audit entry {}", dateFromValue,
                                dateToValue, userName, entryId);
                        effectiveFirstTime = Long.valueOf(dateFromValue.getTime());
                        effectiveLastTime = Long.valueOf(dateToValue.getTime());
                    }
                }

                if (effectiveFirstTime == null || effectiveLastTime == null)
                {
                    LOGGER.trace("Using audit timestamp {} as active date for user {} from audit entry {}", time, userName, entryId);
                    effectiveFirstTime = time;
                    effectiveLastTime = time;
                }

                if (firstActive.get() == -1 || firstActive.get() > effectiveFirstTime.longValue())
                {
                    firstActive.set(effectiveFirstTime.longValue());
                }

                if (lastActive.get() == -1 || lastActive.get() < effectiveLastTime.longValue())
                {
                    lastActive.set(effectiveLastTime.longValue());
                }

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

        }, aqp, useDateValuesFromAuditData ? Integer.MAX_VALUE : 1);
        // we cannot rely on date-based ordering to retrieve firstActive only by looking at first entry

        // and only if we can rely on date-based ordering is it appropriate to only look at last entry for lastActive
        if (!useDateValuesFromAuditData)
        {
            aqp.setForward(false);
            this.auditService.auditQuery(new AuditQueryCallback()
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
                    LOGGER.trace("Using audit timestamp {} as last active date for user {} from audit entry {}", time, userName, entryId);
                    lastActive.set(time);
                    return true;
                }

                @Override
                public boolean handleAuditEntryError(final Long entryId, final String errorMsg, final Throwable error)
                {
                    return true;
                }

            }, aqp, 1);
        }

        final AuthorisedState authorisedState;
        if (this.isAuthorisedCheck != null && this.isDeauthorisedCheck != null)
        {
            final boolean isAuthorised = Boolean.TRUE.equals(this.isAuthorisedCheck.apply(userName));
            final boolean isDeauthorised = Boolean.TRUE.equals(this.isDeauthorisedCheck.apply(userName));
            authorisedState = isAuthorised ? AuthorisedState.AUTHORISED
                    : (isDeauthorised ? AuthorisedState.DEAUTHORISED : AuthorisedState.UNKNOWN);
            LOGGER.trace("Determined authorised state {} for user {}", authorisedState, userName);
        }
        else
        {
            authorisedState = AuthorisedState.UNKNOWN;
        }

        // we found an instance of the user
        if (firstActive.get() != -1)
        {
            if (this.queryMode != PersonAuditQueryMode.INACTIVE_ONLY)
            {
                final AuditUserInfo userInfo = new AuditUserInfo(userName, personRef, authorisedState, new Date(firstActive.get()),
                        new Date(lastActive.get()));
                LOGGER.debug("Adding user info {} to results", userInfo);
                this.usersSync.add(userInfo);
            }
        }
        else if (this.queryMode != PersonAuditQueryMode.ACTIVE_ONLY)
        {
            final AuditUserInfo userInfo = new AuditUserInfo(userName, personRef, authorisedState);
            LOGGER.debug("Adding user info {} to results", userInfo);
            this.usersSync.add(userInfo);
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
     * Retrieves the result of a {@link BatchProcessor#process(org.alfresco.repo.batch.BatchProcessor.BatchProcessWorker, boolean)
     * process-run} using this worker.
     *
     * @return the activeUsers
     */
    public List<AuditUserInfo> getUsers()
    {
        return Collections.unmodifiableList(this.users);
    }
}
