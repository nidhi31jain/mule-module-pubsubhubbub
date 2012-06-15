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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.Validate;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.annotations.Configurable;
import org.mule.api.annotations.Module;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.lifecycle.Start;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.InboundHeaders;
import org.mule.api.annotations.param.Optional;
import org.mule.api.annotations.param.OutboundHeaders;
import org.mule.api.annotations.param.Payload;
import org.mule.api.callback.SourceCallback;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;
import org.mule.util.NumberUtils;
import org.mule.util.StringUtils;

/**
 * Allows Mule to act as a PubSubHubbub (aka PuSH) subscriber.<br/>
 * Pubsubhubbub is a simple, open, web-hook-based pubsub protocol & open source reference implementation. <br/>
 * This module implements the <a
 * href="http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html">PubSubHubbub Core 0.3 -- Working Draft
 * specification</a>, except <a
 * href="http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html#aggregatedistribution">7.5 Aggregated
 * Content Distribution</a>.
 * 
 * @author MuleSoft, Inc.
 */
@Module(name = "PuSH-subscriber", schemaVersion = "3.3")
public class PuSHSubscriberModule extends AbstractPuSHModule
{
    /**
     * The URL of the PubSubHubbub to connect to.
     */
    @Configurable
    private String hubUrl;

    /**
     * The URL that the hub should call back, which is the public URL of the HTTP inbound endpoint placed in front of
     * the <code>subscriber</code> element.
     */
    @Configurable
    private String callbackUrl;

    /**
     * The topic URL that the subscriber wishes to subscribe to.
     */
    @Configurable
    private String topic;

    /**
     * (Optional) Number of seconds for which the subscriber would like to have the subscription active. Defaults to 7
     * days.
     */
    @Default(value = "604800")
    @Configurable
    @Optional
    private Long leaseSeconds;

    /**
     * (Optional) A subscriber-provided secret string that will be used to compute an HMAC digest for authorized content
     * distribution.
     */
    @Configurable
    @Optional
    private String secret;

    @Start
    public void subscribe() throws Exception
    {
        Validate.notEmpty(hubUrl, "hubUrl can't be empty");
        Validate.notEmpty(callbackUrl, "callbackUrl can't be empty");
        Validate.notEmpty(topic, "topic can't be empty");

        final StringBuilder queryBuilder = new StringBuilder();
        Utils.appendToQuery(Constants.HUB_MODE_PARAM, "subscribe", queryBuilder);
        Utils.appendToQuery(Constants.HUB_CALLBACK_PARAM, callbackUrl, queryBuilder);
        Utils.appendToQuery(Constants.HUB_TOPIC_PARAM, topic, queryBuilder);

        // support both sync and async
        Utils.appendToQuery(Constants.HUB_VERIFY_PARAM, "sync", queryBuilder);
        Utils.appendToQuery(Constants.HUB_VERIFY_PARAM, "async", queryBuilder);
        Utils.appendToQuery(Constants.HUB_VERIFY_TOKEN_PARAM, getVerifyToken(), queryBuilder);

        if (leaseSeconds != null)
        {
            Utils.appendToQuery(Constants.HUB_LEASE_SECONDS_PARAM, leaseSeconds.toString(), queryBuilder);
        }
        if (StringUtils.isNotBlank(secret))
        {
            Utils.appendToQuery(Constants.HUB_SECRET_PARAM, secret, queryBuilder);
        }

        final MuleMessage response = getMuleContext().getClient().send(
            hubUrl,
            queryBuilder.toString(),
            Collections.singletonMap(HttpConstants.HEADER_CONTENT_TYPE,
                (Object) Constants.WWW_FORM_URLENCODED_CONTENT_TYPE), Constants.SUBSCRIBER_TIMEOUT_MILLIS);

        final String responseStatus = response.getInboundProperty(HttpConnector.HTTP_STATUS_PROPERTY);

        if ("202".equalsIgnoreCase(responseStatus))
        {
            logger.info("Subscription request sent to hub: " + hubUrl);
        }
        else if ("204".equalsIgnoreCase(responseStatus))
        {
            logger.info("Subscription request accepted by hub: " + hubUrl);
        }
        else
        {
            logger.error("Unexpected response from hub: " + responseStatus + " "
                         + response.getPayloadAsString());
        }
    }

    /**
     * Handle all subscriber requests.
     * <p/>
     * {@sample.xml ../../../doc/pubsubhubbub-connector.xml.sample PuSH-subscriber:handleSubscriberRequest}
     * 
     * @param httpMethod the HTTP method name
     * @param contentType the content-type of the request
     * @param responseHeaders the outbound/response headers
     * @param payload the message payload
     * @param onBehalfOf (Optional) number of subscribers this subscriber is representing, should be either an integer
     *            or a Mule expression
     * @param sourceCallback the callback to call when content is propagated
     * @return the response body
     * @throws Exception
     * @throws MuleException
     * @throws DecoderException
     */
    @Processor(name = "subscriber", intercepting = true)
    public String handleSubscriberRequest(@InboundHeaders(HttpConnector.HTTP_METHOD_PROPERTY) final String httpMethod,
                                          @InboundHeaders(HttpConstants.HEADER_CONTENT_TYPE + "?") final String contentType,
                                          @OutboundHeaders final Map<String, Object> responseHeaders,
                                          @Optional final String onBehalfOf,
                                          @Payload final String payload,
                                          final SourceCallback sourceCallback) throws Exception
    {
        if (StringUtils.equalsIgnoreCase(httpMethod, HttpConstants.METHOD_POST))
        {
            return handleContentDelivery(contentType, responseHeaders, onBehalfOf, payload, sourceCallback);
        }

        if (StringUtils.equalsIgnoreCase(httpMethod, HttpConstants.METHOD_GET))
        {
            return handleSubscriptionVerification(responseHeaders, payload);
        }

        return respond(responseHeaders, PuSHResponse.badRequest("HTTP method must be POST or GET"));
    }

    private String handleSubscriptionVerification(final Map<String, Object> responseHeaders,
                                                  final String payload) throws URISyntaxException
    {
        final String query = new URI(payload).getQuery();
        final String[] params = StringUtils.split(query, '&');

        String challenge = null;
        boolean verified = false;

        for (final String param : params)
        {
            final String[] s = StringUtils.split(param, '=');
            if (s.length != 2)
            {
                continue;
            }
            if (StringUtils.equals(s[0], Constants.HUB_CHALLENGE_PARAM))
            {
                challenge = s[1];
            }
            else if (StringUtils.equals(s[0], Constants.HUB_VERIFY_TOKEN_PARAM))
            {
                verified = StringUtils.equals(s[1], getVerifyToken());
            }
        }

        if (!verified)
        {
            return respond(responseHeaders, PuSHResponse.notFound());
        }
        if (StringUtils.isBlank(challenge))
        {
            return respond(responseHeaders,
                PuSHResponse.badRequest("Missing query parameter: " + Constants.HUB_CHALLENGE_PARAM));
        }

        return respond(responseHeaders, PuSHResponse.ok(challenge));
    }

    protected String handleContentDelivery(final String contentType,
                                           final Map<String, Object> responseHeaders,
                                           final String onBehalfOf,
                                           final String payload,
                                           final SourceCallback sourceCallback) throws Exception
    {
        if ((!StringUtils.startsWith(contentType, Constants.RSS_CONTENT_TYPE))
            && (!StringUtils.startsWith(contentType, Constants.ATOM_CONTENT_TYPE)))
        {
            return respond(
                responseHeaders,
                PuSHResponse.badRequest("Content type must be either: " + Constants.RSS_CONTENT_TYPE + " or "
                                        + Constants.ATOM_CONTENT_TYPE));
        }

        handleOnBehalfOf(responseHeaders, onBehalfOf);

        sourceCallback.process(payload);
        return respond(responseHeaders, PuSHResponse.noContent());
    }

    protected void handleOnBehalfOf(final Map<String, Object> responseHeaders, final String onBehalfOf)
    {
        if (StringUtils.isBlank(onBehalfOf))
        {
            return;
        }

        if (NumberUtils.isDigits(onBehalfOf))
        {
            responseHeaders.put(Constants.HUB_ON_BEHALF_OF_HEADER, Integer.valueOf(onBehalfOf));
        }
        else
        {
            logger.warn("Ignoring non-numeric onBehalfOf value: " + onBehalfOf);
        }
    }

    public String getVerifyToken()
    {
        return DigestUtils.shaHex(hubUrl + topic);
    }

    public String getHubUrl()
    {
        return hubUrl;
    }

    public void setHubUrl(final String hubUrl)
    {
        this.hubUrl = hubUrl;
    }

    public String getCallbackUrl()
    {
        return callbackUrl;
    }

    public void setCallbackUrl(final String callbackUrl)
    {
        this.callbackUrl = callbackUrl;
    }

    public String getTopic()
    {
        return topic;
    }

    public void setTopic(final String topic)
    {
        this.topic = topic;
    }

    public Long getLeaseSeconds()
    {
        return leaseSeconds;
    }

    public void setLeaseSeconds(final Long leaseSeconds)
    {
        this.leaseSeconds = leaseSeconds;
    }

    public String getSecret()
    {
        return secret;
    }

    public void setSecret(final String secret)
    {
        this.secret = secret;
    }
}
