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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.mule.module.pubsubhubbub.data.DataStore;
import org.mule.module.pubsubhubbub.data.TopicSubscription;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

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
}
