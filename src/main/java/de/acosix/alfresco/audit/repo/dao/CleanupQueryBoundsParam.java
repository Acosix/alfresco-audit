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
package de.acosix.alfresco.audit.repo.dao;

import java.io.Serializable;

/**
 * A simple ID-based bounds entity for from-to select statements.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 *
 */
public class CleanupQueryBoundsParam implements Serializable
{

    private static final long serialVersionUID = 8229371144056350651L;

    protected Long fromId;

    protected Long toId;

    protected Integer maxItems;

    /**
     * Retrieves the from ID boundary condition
     *
     * @return the from ID
     */
    public Long getFromId()
    {
        return this.fromId;
    }

    /**
     * Sets the from ID boundary condition
     *
     * @param fromId
     *            the from ID
     */
    public void setFromId(final Long fromId)
    {
        this.fromId = fromId;
    }

    /**
     * Retrieves the to ID boundary condition
     *
     * @return the to ID
     */
    public Long getToId()
    {
        return this.toId;
    }

    /**
     * Sets the to ID boundary condition
     *
     * @param toId
     *            the to ID
     */
    public void setToId(final Long toId)
    {
        this.toId = toId;
    }

    /**
     * Retrieves the maximum number of items to load
     *
     * @return the maximum number of items to load
     */
    public Integer getMaxItems()
    {
        return this.maxItems;
    }

    /**
     * Sets the maximum number of items to load
     *
     * @param maxItems
     *            the maximum number of items to load
     */
    public void setMaxItems(final Integer maxItems)
    {
        this.maxItems = maxItems;
    }

}
