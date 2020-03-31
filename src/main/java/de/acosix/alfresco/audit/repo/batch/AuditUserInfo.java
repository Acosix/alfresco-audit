/*
 * Copyright 2017 - 2020 Acosix GmbH
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

import java.util.Date;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.ParameterCheck;

/**
 * DTO implementation used to aggregate data about a user by the {@link PersonAuditWorker}
 *
 * @author Axel Faust
 */
public class AuditUserInfo implements Comparable<AuditUserInfo>
{

    public static enum AuthorisedState
    {
        AUTHORISED, DEAUTHORISED, UNKNOWN;
    }

    protected final String userName;

    protected final NodeRef personRef;

    protected final AuthorisedState authorisedState;

    protected final Date firstActive;

    protected final Date lastActive;

    /**
     * Constructs a new instance of this class denoting an inactive user
     *
     * @param userName
     *            the name of the user
     * @param personRef
     *            the reference to the person node
     * @param authorisedState
     *            the state of authorisation in the Enterprise-specific licensing handling
     */
    public AuditUserInfo(final String userName, final NodeRef personRef, final AuthorisedState authorisedState)
    {
        ParameterCheck.mandatoryString("userName", userName);
        ParameterCheck.mandatory("personRef", personRef);
        ParameterCheck.mandatory("authorisedState", authorisedState);
        this.userName = userName;
        this.personRef = personRef;
        this.authorisedState = authorisedState;
        this.firstActive = null;
        this.lastActive = null;
    }

    /**
     * Constructs a new instance of this class denoting an active user
     *
     * @param userName
     *            the name of the user
     * @param personRef
     *            the reference to the person node
     * @param authorisedState
     *            the state of authorisation in the Enterprise-specific licensing handling
     * @param firstActive
     *            the earliest timestamp of activity for the user
     * @param lastActive
     *            the latest timestamp of activity for the user
     */
    public AuditUserInfo(final String userName, final NodeRef personRef, final AuthorisedState authorisedState, final Date firstActive,
            final Date lastActive)
    {
        ParameterCheck.mandatoryString("userName", userName);
        ParameterCheck.mandatory("personRef", personRef);
        ParameterCheck.mandatory("authorisedState", authorisedState);
        ParameterCheck.mandatory("firstActive", firstActive);
        ParameterCheck.mandatory("lastActive", lastActive);

        this.userName = userName;
        this.personRef = personRef;
        this.authorisedState = authorisedState;
        this.firstActive = new Date(firstActive.getTime());
        this.lastActive = new Date(lastActive.getTime());
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
     * Retrieves the name of the user
     *
     * @return the name of the user
     */
    public String getUserName()
    {
        return this.userName;
    }

    /**
     * Retrieves the reference to the person node of the user
     *
     * @return the person node reference
     */
    public NodeRef getPersonRef()
    {
        return this.personRef;
    }

    /**
     * Retrieves the Enterprise-specific authorisation state of the user
     *
     * @return the authorisation state
     */
    public AuthorisedState getAuthorisedState()
    {
        return this.authorisedState;
    }

    /**
     * Retrieves the earliest timestamp of activity by the user
     *
     * @return the earliest activity timestamp or {@code null} if this instance represents an inactive user
     */
    public Date getFirstActive()
    {
        final Date result = this.firstActive != null ? new Date(this.firstActive.getTime()) : null;
        return result;
    }

    /**
     * Retrieves the latest timestamp of activity by the user
     *
     * @return the latest activity timestamp or {@code null} if this instance represents an inactive user
     */
    public Date getLastActive()
    {
        final Date result = this.lastActive != null ? new Date(this.lastActive.getTime()) : null;
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.userName == null) ? 0 : this.userName.hashCode());
        result = prime * result + ((this.personRef == null) ? 0 : this.personRef.hashCode());
        result = prime * result + ((this.authorisedState == null) ? 0 : this.authorisedState.hashCode());
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
        if (this.authorisedState != other.authorisedState)
        {
            return false;
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("AuditUserInfo [");
        if (this.userName != null)
        {
            builder.append("userName=");
            builder.append(this.userName);
            builder.append(", ");
        }
        if (this.personRef != null)
        {
            builder.append("personRef=");
            builder.append(this.personRef);
            builder.append(", ");
        }
        if (this.authorisedState != null)
        {
            builder.append("authorisedState=");
            builder.append(this.authorisedState);
            builder.append(", ");
        }
        if (this.firstActive != null)
        {
            builder.append("firstActive=");
            builder.append(this.firstActive);
            builder.append(", ");
        }
        if (this.lastActive != null)
        {
            builder.append("lastActive=");
            builder.append(this.lastActive);
        }
        builder.append("]");
        return builder.toString();
    }

}
