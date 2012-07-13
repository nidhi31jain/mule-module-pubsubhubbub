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

import java.util.Collections;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.Validate;
import org.mule.api.MuleMessage;
import org.mule.api.annotations.Configurable;
import org.mule.api.annotations.Module;
import org.mule.api.annotations.Processor;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;

/**
 * Allows Mule to act as a PubSubHubbub (aka PuSH) publisher.
 * 
 * @author MuleSoft, Inc.
 */
@Module(name = "PuSH-publisher", schemaVersion = "3.3")
public class PuSHPublisherModule extends AbstractPuSHModule
{
    /**
     * The URL of the PubSubHubbub to connect to.
     */
    @Configurable
    private String hubUrl;

    @PostConstruct
    public void validateConfiguration()
    {
        Validate.notEmpty(hubUrl, "hubUrl can't be empty");
    }

    /**
     * Notifies the configured hub that new content is available for the considered
     * topic.
     * <p/>
     * {@sample.xml ../../../doc/pubsubhubbub-connector.xml.sample
     * PuSH-publisher:notifyNewContent}
     * 
     * @param topic the topic URL for which the publisher wants to notify that new
     *            content is available
     * @throws Exception if something goes wrong with the notification
     */
    @Processor
    public void notifyNewContent(final String topic) throws Exception
    {
        Validate.notEmpty(topic, "topicUrl can't be empty");

        final StringBuilder queryBuilder = new StringBuilder();
        Utils.appendToQuery(Constants.HUB_MODE_PARAM, "publish", queryBuilder);
        // yep, the topicUrl is passed in the hub.url param... see:
        // http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html#rfc.section.7.1
        Utils.appendToQuery(Constants.HUB_URL_PARAM, topic, queryBuilder);

        final MuleMessage response = getMuleContext().getClient().send(
            hubUrl,
            queryBuilder.toString(),
            Collections.singletonMap(HttpConstants.HEADER_CONTENT_TYPE,
                (Object) Constants.WWW_FORM_URLENCODED_CONTENT_TYPE), Constants.SUBSCRIBER_TIMEOUT_MILLIS);

        final String responseStatus = response.getInboundProperty(HttpConnector.HTTP_STATUS_PROPERTY);

        Validate.isTrue("204".equals(responseStatus), "Unexpected response status from hub: "
                                                      + responseStatus + " " + response.getPayloadAsString());

        if (logger.isDebugEnabled())
        {
            logger.debug("New content for topic: " + topic + " notified to hub: " + hubUrl);
        }
    }

    public void setHubUrl(final String hubUrl)
    {
        this.hubUrl = hubUrl;
    }

    public String getHubUrl()
    {
        return hubUrl;
    }
}
