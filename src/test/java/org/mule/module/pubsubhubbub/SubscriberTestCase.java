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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.junit.Test;
import org.mule.module.pubsubhubbub.data.TopicSubscription;

public class SubscriberTestCase extends AbstractPuSHTestCase
{
    private static final String SUBSCRIBER_FLOW_NAME = "subscriber";

    @Override
    protected String getConfigResources()
    {
        return "push-subscriber-tests-config.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        setupSubscriberFTC(SUBSCRIBER_FLOW_NAME, 0);
        super.doSetUp();
    }

    @Test
    public void testSubscribed() throws Exception
    {
        ensureSubscribed();
        assertThat(subscriberFTC.getReceivedMessagesCount(), is(0));
    }

    @Test
    public void testContentDistribution() throws Exception
    {
        ensureSubscribed();
        setupPublisherFTC(1);
        setupSubscriberFTC(SUBSCRIBER_FLOW_NAME, 1);
        doTestSuccessfulContentDistribution(getTestTopics().get(0));
    }

    protected void ensureSubscribed() throws InterruptedException, URISyntaxException
    {
        final Set<TopicSubscription> stored = ponderUntilSubscriptionStored(new URI(getTestTopics().get(0)));
        assertThat(stored.size(), is(1));
    }
}
