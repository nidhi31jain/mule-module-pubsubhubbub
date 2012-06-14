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

import org.mule.transport.http.HttpConstants;

public class PuSHResponse
{
    private final int status;
    private final String body;

    private PuSHResponse(final int status, final String body)
    {
        this.status = status;
        this.body = body;
    }

    public String getBody()
    {
        return body;
    }

    public int getStatus()
    {
        return status;
    }

    public static PuSHResponse ok(final String entity)
    {
        return new PuSHResponse(HttpConstants.SC_OK, entity);
    }

    public static PuSHResponse noContent()
    {
        return new PuSHResponse(HttpConstants.SC_NO_CONTENT, "");
    }

    public static PuSHResponse accepted()
    {
        return new PuSHResponse(HttpConstants.SC_ACCEPTED, "");
    }

    public static PuSHResponse badRequest(final String message)
    {
        return new PuSHResponse(HttpConstants.SC_BAD_REQUEST, message);
    }

    public static PuSHResponse notFound()
    {
        return new PuSHResponse(HttpConstants.SC_NOT_FOUND, "");
    }

    public static PuSHResponse serverError(final String message)
    {
        return new PuSHResponse(HttpConstants.SC_INTERNAL_SERVER_ERROR, message);
    }
}
