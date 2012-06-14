/**
 * Mule PubSubHubbub Connector
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.pubsubhubbub;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mule.api.MuleContext;
import org.mule.api.context.MuleContextAware;
import org.mule.transport.http.HttpConnector;

public abstract class AbstractPuSHModule implements MuleContextAware
{
    protected final Log logger = LogFactory.getLog(getClass());

    private MuleContext muleContext;

    protected String respond(final Map<String, Object> responseHeaders, final PuSHResponse response)
    {
        responseHeaders.put(HttpConnector.HTTP_STATUS_PROPERTY, response.getStatus());
        return response.getBody();
    }

    public void setMuleContext(final MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    public MuleContext getMuleContext()
    {
        return muleContext;
    }
}
