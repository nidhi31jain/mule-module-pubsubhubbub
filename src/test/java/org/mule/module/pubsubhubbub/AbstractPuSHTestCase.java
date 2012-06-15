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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.module.pubsubhubbub.data.DataStore;
import org.mule.module.pubsubhubbub.data.TopicSubscription;
import org.mule.tck.functional.CountdownCallback;
import org.mule.tck.functional.FunctionalTestComponent;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.transport.http.HttpConstants;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;

public abstract class AbstractPuSHTestCase extends FunctionalTestCase
{
    protected static final String TEST_TOPIC = "http://mulesoft.org/fake-topic";

    @Rule
    public DynamicPort hubPort = new DynamicPort("port1");
    @Rule
    public DynamicPort subscriberPort = new DynamicPort("port2");
    @Rule
    public DynamicPort publisherPort = new DynamicPort("port3");

    protected HttpClient httpClient;
    protected DataStore dataStore;

    protected FunctionalTestComponent publisherFTC;
    protected CountdownCallback publisherCC;

    protected FunctionalTestComponent subscriberFTC;

    protected CountdownCallback subscriberCC;

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        dataStore = muleContext.getRegistry().lookupObject(PuSHHubModule.class).getDataStore();
        httpClient = new HttpClient();
    }

    protected int getHubPort()
    {
        return hubPort.getNumber();
    }

    protected int getSubscriberCallbacksPort()
    {
        return subscriberPort.getNumber();
    }

    protected int getPublisherPort()
    {
        return publisherPort.getNumber();
    }

    protected List<String> getTestTopics()
    {
        return Arrays.asList("http://localhost:" + getPublisherPort() + "/feeds/mouth/rss",
            "http://localhost:" + getPublisherPort() + "/feeds/boca/rss");
    }

    protected void setupSubscriberFTC(final String flowName, final int messagesExpected) throws Exception
    {
        subscriberFTC = getFunctionalTestComponent(flowName);
        subscriberCC = new CountdownCallback(messagesExpected);
        subscriberFTC.setEventCallback(subscriberCC);
    }

    protected void setupPublisherFTC(final int messagesExpected) throws Exception
    {
        publisherFTC = getFunctionalTestComponent("publisher");
        publisherCC = new CountdownCallback(messagesExpected);
        publisherFTC.setEventCallback(publisherCC);
    }

    protected void checkTopicSubscriptionStored(final String callback,
                                                final Map<String, List<String>> subscriptionRequest)
        throws Exception
    {
        for (final String hubTopic : subscriptionRequest.get("hub.topic"))
        {
            final URI hubTopicUri = new URI(hubTopic);
            final Set<TopicSubscription> topicSubscriptions = ponderUntilSubscriptionStored(hubTopicUri);
            assertEquals(1, topicSubscriptions.size());
            final TopicSubscription topicSubscription = topicSubscriptions.iterator().next();
            assertEquals(hubTopicUri, topicSubscription.getTopicUrl());
            assertEquals(new URI(callback), topicSubscription.getCallbackUrl());
            assertTrue(topicSubscription.getExpiryTime() > 0L);
            final String secretAsString = subscriptionRequest.get("hub.secret") != null
                                                                                       ? subscriptionRequest.get(
                                                                                           "hub.secret")
                                                                                           .get(0)
                                                                                       : null;
            if (StringUtils.isNotBlank(secretAsString))
            {
                assertTrue(Arrays.equals(secretAsString.getBytes("utf-8"), topicSubscription.getSecret()));
            }
            else
            {
                assertNull(topicSubscription.getSecret());
            }
        }
    }

    protected void checkTopicSubscriptionCleared(final String callback,
                                                 final Map<String, List<String>> subscriptionRequest)
        throws Exception
    {
        for (final String hubTopic : subscriptionRequest.get("hub.topic"))
        {
            final URI hubTopicUri = new URI(hubTopic);
            final Set<TopicSubscription> topicSubscriptions = dataStore.getTopicSubscriptions(hubTopicUri);
            assertEquals(0, topicSubscriptions.size());
        }
    }

    protected Set<TopicSubscription> ponderUntilSubscriptionStored(final URI hubTopicUri)
        throws InterruptedException
    {

        for (int attempts = 0; attempts < 300; attempts++)
        {
            final Set<TopicSubscription> topicSubscriptions = dataStore.getTopicSubscriptions(hubTopicUri);
            if (!topicSubscriptions.isEmpty())
            {
                return topicSubscriptions;
            }
            Thread.sleep(100L);
        }
        return Collections.emptySet();
    }

    protected void doTestSuccessfullNewContentNotificationAndContentFetch(final String topicUrl)
        throws Exception
    {
        final Map<String, String> subscriptionRequest = new HashMap<String, String>();
        subscriptionRequest.put("hub.mode", "publish");
        subscriptionRequest.put("hub.url", topicUrl);

        final MuleMessage response = wrapAndSendRequestToHub(subscriptionRequest);
        assertEquals("204", response.getInboundProperty("http.status"));

        publisherCC.await(TimeUnit.SECONDS.toMillis(getTestTimeoutSecs()));
        assertEquals("/feeds/mouth/rss", publisherFTC.getLastReceivedMessage());
    }

    protected MuleMessage wrapAndSendRequestToHub(final Map<String, String> subscriptionRequest)
        throws Exception
    {
        final Map<String, List<String>> wrappedRequest = new HashMap<String, List<String>>();
        for (final Entry<String, String> param : subscriptionRequest.entrySet())
        {
            wrappedRequest.put(param.getKey(), Collections.singletonList(param.getValue()));
        }
        return sendRequestToHub(wrappedRequest);
    }

    protected MuleMessage sendRequestToHub(final Map<String, List<String>> subscriptionRequest)
        throws Exception
    {
        return sendRequestToHub(subscriptionRequest, "application/x-www-form-urlencoded");
    }

    protected MuleMessage sendRequestToHub(final Map<String, List<String>> subscriptionRequest,
                                           final String contentType) throws Exception
    {
        final String hubUrl = "http://localhost:" + getHubPort() + "/hub";

        final PostMethod postMethod = new PostMethod(hubUrl);
        postMethod.setRequestHeader(HttpConstants.HEADER_CONTENT_TYPE, contentType);
        for (final Entry<String, List<String>> param : subscriptionRequest.entrySet())
        {
            for (final String value : param.getValue())
            {
                postMethod.addParameter(param.getKey(), value);
            }
        }

        final Integer responseStatus = httpClient.executeMethod(postMethod);
        final MuleMessage response = new DefaultMuleMessage(postMethod.getResponseBodyAsString(),
            Collections.singletonMap("http.status", (Object) responseStatus.toString()), null, null,
            muleContext);
        return response;
    }

    protected void doTestSuccessfulContentDistribution(final String topicUrl)
        throws Exception, InterruptedException, FeedException, URISyntaxException
    {
        doTestSuccessfullNewContentNotificationAndContentFetch(topicUrl);

        // check RSS content has been pushed to callback FTC
        subscriberCC.await(TimeUnit.SECONDS.toMillis(getTestTimeoutSecs()));
        final SyndFeed syndFeed = new SyndFeedInput(true).build(new StringReader(
            (String) subscriberFTC.getLastReceivedMessage()));
        assertEquals("rss_2.0", syndFeed.getFeedType());
    }
}
