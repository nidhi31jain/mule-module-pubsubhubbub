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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.annotations.Configurable;
import org.mule.api.annotations.Module;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.InboundHeaders;
import org.mule.api.annotations.param.Optional;
import org.mule.api.annotations.param.OutboundHeaders;
import org.mule.api.annotations.param.Payload;
import org.mule.api.context.MuleContextAware;
import org.mule.api.retry.RetryPolicyTemplate;
import org.mule.api.store.PartitionableObjectStore;
import org.mule.module.pubsubhubbub.data.DataStore;
import org.mule.module.pubsubhubbub.handler.AbstractHubActionHandler;
import org.mule.retry.async.AsynchronousRetryTemplate;
import org.mule.retry.policies.SimpleRetryPolicyTemplate;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;

/**
 * Allows Mule to act as a PubSubHubbub (aka PuSH) hub and subscriber.<br/>
 * Pubsubhubbub is a simple, open, web-hook-based pubsub protocol & open source reference implementation. <br/>
 * This module implements the <a
 * href="http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html">PubSubHubbub Core 0.3 -- Working Draft
 * specification</a>, except <a
 * href="http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html#aggregatedistribution">7.5 Aggregated
 * Content Distribution</a>.
 * 
 * @author MuleSoft, Inc.
 */
@Module(name = "pubsubhubbub", schemaVersion = "3.3")
public class PuSHModule implements MuleContextAware
{
    private MuleContext muleContext;

    private DataStore dataStore;

    private Map<HubMode, AbstractHubActionHandler> requestHandlers;

    /**
     * Any implementation of {@link PartitionableObjectStore} can be used as a back-end for the hub.
     */
    @Configurable
    private PartitionableObjectStore<Serializable> objectStore;

    /**
     * The retry frequency in milliseconds. Defaults to 5 minutes.
     */
    @Default(value = "300000")
    @Optional
    @Configurable
    private int retryFrequency;

    /**
     * The retry count. Defaults to 12 times.
     */
    @Default(value = "12")
    @Optional
    @Configurable
    private int retryCount;

    /**
     * The default lease time in milliseconds. Defaults to 7 days.
     */
    @Default(value = "604800")
    @Optional
    @Configurable
    private long defaultLeaseSeconds;

    @PostConstruct
    public void wireResources()
    {
        dataStore = new DataStore(objectStore);

        final SimpleRetryPolicyTemplate delegate = new SimpleRetryPolicyTemplate(retryFrequency, retryCount);
        delegate.setMuleContext(getMuleContext());
        final RetryPolicyTemplate hubRetryPolicyTemplate = new AsynchronousRetryTemplate(delegate);

        requestHandlers = new HashMap<HubMode, AbstractHubActionHandler>();
        for (final HubMode hubMode : HubMode.values())
        {
            requestHandlers.put(hubMode, hubMode.newHandler(muleContext, dataStore, hubRetryPolicyTemplate));
        }
    }

    /**
     * Handle all hub requests.
     * <p/>
     * {@sample.xml ../../../doc/pubsubhubbub-connector.xml.sample pubsubhubbub:handleRequest}
     * 
     * @param payload the message payload
     * @param httpMethod the HTTP method name
     * @param contentType the content-type of the request
     * @param responseHeaders the outbound/response headers
     * @return the response body
     * @throws MuleException
     * @throws DecoderException
     */
    @Processor(name = "hub")
    public String handleHubRequest(@InboundHeaders(HttpConnector.HTTP_METHOD_PROPERTY) final String httpMethod,
                                   @InboundHeaders(HttpConstants.HEADER_CONTENT_TYPE) final String contentType,
                                   @OutboundHeaders final Map<String, Object> responseHeaders,
                                   @Payload final String payload) throws MuleException, DecoderException
    {
        return handleRequest(
            httpMethod,
            contentType,
            responseHeaders,
            payload,
            Collections.singletonMap(Constants.HUB_DEFAULT_LEASE_SECONDS_PARAM,
                Long.toString(defaultLeaseSeconds)));
    }

    /**
     * Handle all subscriber requests.
     * <p/>
     * {@sample.xml ../../../doc/pubsubhubbub-connector.xml.sample pubsubhubbub:handleRequest}
     * 
     * @param payload the message payload
     * @param httpMethod the HTTP method name
     * @param contentType the content-type of the request
     * @param responseHeaders the outbound/response headers
     * @param hubUrl the URL of the PubSubHubbub to connect to
     * @param callbackUrl the URL that the hub should call back, which is the public URL of the HTTP inbound endpoint
     *            placed before this element
     * @param topic The topic URL that the subscriber wishes to subscribe to.
     * @param leaseSeconds (Optional) Number of seconds for which the subscriber would like to have the subscription
     *            active.
     * @param secret (Optional) A subscriber-provided secret string that will be used to compute an HMAC digest for
     *            authorized content distribution.
     * @return the response body
     * @throws MuleException
     * @throws DecoderException
     */
    @Processor(name = "subscribe")
    public String handleSubscriberRequest(@InboundHeaders(HttpConnector.HTTP_METHOD_PROPERTY) final String httpMethod,
                                          @InboundHeaders(HttpConstants.HEADER_CONTENT_TYPE) final String contentType,
                                          @OutboundHeaders final Map<String, Object> responseHeaders,
                                          @Payload final String payload,
                                          final String hubUrl,
                                          final String callbackUrl,
                                          final String topic,
                                          @Optional final Long leaseSeconds,
                                          @Optional final String secret)
        throws MuleException, DecoderException
    {
        Validate.notEmpty(hubUrl, "hubUrl can't be empty");
        Validate.notEmpty(callbackUrl, "callbackUrl can't be empty");
        Validate.notEmpty(topic, "topic can't be empty");

        // TODO send subscription message

        // TODO handle incoming requests
        // if POST application/atom+xml application/rss+xml -> content delivery
        // if GET Hub Verifies Intent of the Subscriber -> handle

        throw new UnsupportedOperationException();
    }

    private String handleRequest(final String httpMethod,
                                 final String contentType,
                                 final Map<String, Object> responseHeaders,
                                 final String payload,
                                 final Map<String, String> extraParameters)
        throws MuleException, DecoderException
    {
        HubResponse response = null;
        if (!StringUtils.equalsIgnoreCase(httpMethod, HttpConstants.METHOD_POST))
        {
            response = HubResponse.badRequest("HTTP method must be: POST");
        }

        if (!StringUtils.startsWith(contentType, Constants.WWW_FORM_URLENCODED_CONTENT_TYPE))
        {
            response = HubResponse.badRequest("Content type must be: application/x-www-form-urlencoded");
        }

        if (response == null)
        {
            // FIXME get encoding from current message:
            // @Expr(value = "#[message:encoding]") final String encoding,
            response = handleRequest(payload, "UTF-8", extraParameters);
        }

        responseHeaders.put(HttpConnector.HTTP_STATUS_PROPERTY, response.getStatus());
        return response.getBody();
    }

    private HubResponse handleRequest(final String payload,
                                      final String encoding,
                                      final Map<String, String> extraParameters)
        throws MuleException, DecoderException
    {
        final Map<String, List<String>> parameters = Utils.getHttpPostParameters(payload, encoding);

        for (final Entry<String, List<String>> param : parameters.entrySet())
        {
            if ((param.getValue().size() > 1)
                && (!Constants.SUPPORTED_MULTIVALUED_PARAMS.contains(param.getKey())))
            {
                return HubResponse.badRequest("Multivalued parameters are only supported for: "
                                              + StringUtils.join(Constants.SUPPORTED_MULTIVALUED_PARAMS, ','));
            }
        }

        // carry the request encoding as a parameters for usage downstream
        Utils.setSingleValue(parameters, Constants.REQUEST_ENCODING_PARAM, encoding);
        for (final Entry<String, String> extraParameter : extraParameters.entrySet())
        {
            Utils.setSingleValue(parameters, extraParameter.getKey(), extraParameter.getValue());
        }

        try
        {
            return handleRequest(parameters);
        }
        catch (final IllegalArgumentException exception)
        {
            return HubResponse.badRequest(exception.getMessage());
        }
    }

    private HubResponse handleRequest(final Map<String, List<String>> parameters)
    {
        final HubMode hubMode = HubMode.parse(Utils.getMandatoryStringParameter(Constants.HUB_MODE_PARAM,
            parameters));

        return requestHandlers.get(hubMode).handle(parameters);
    }

    public void setMuleContext(final MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    public MuleContext getMuleContext()
    {
        return muleContext;
    }

    public void setObjectStore(final PartitionableObjectStore<Serializable> objectStore)
    {
        this.objectStore = objectStore;
    }

    public PartitionableObjectStore<Serializable> getObjectStore()
    {
        return objectStore;
    }

    public int getRetryFrequency()
    {
        return retryFrequency;
    }

    public void setRetryFrequency(final int retryFrequency)
    {
        this.retryFrequency = retryFrequency;
    }

    public int getRetryCount()
    {
        return retryCount;
    }

    public void setRetryCount(final int retryCount)
    {
        this.retryCount = retryCount;
    }

    public long getDefaultLeaseSeconds()
    {
        return defaultLeaseSeconds;
    }

    public void setDefaultLeaseSeconds(final long defaultLeaseSeconds)
    {
        this.defaultLeaseSeconds = defaultLeaseSeconds;
    }

    public DataStore getDataStore()
    {
        return dataStore;
    }

}
