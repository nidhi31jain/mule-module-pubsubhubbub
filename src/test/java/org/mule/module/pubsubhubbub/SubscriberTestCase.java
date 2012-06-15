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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.junit.Test;
import org.mule.api.MuleContext;
import org.mule.module.pubsubhubbub.data.TopicSubscription;

public class SubscriberTestCase extends AbstractPuSHTestCase
{
    private static final int TEST_ON_BEHALF_COUNT = 789;

    @Override
    protected String getConfigResources()
    {
        return "push-subscriber-tests-config.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        setupSubscriberFTC("subscriber1", 0);
        super.doSetUp();
    }

    @Override
    protected MuleContext createMuleContext() throws Exception
    {
        final MuleContext mc = super.createMuleContext();
        mc.getRegistry().registerObject("_test_on_behalf_of_value", TEST_ON_BEHALF_COUNT);
        return mc;
    }

    @Test
    public void testSubscribed() throws Exception
    {
        ensureSubscribed(getMouthTestTopic());
        assertThat(subscriberFTC.getReceivedMessagesCount(), is(0));
    }

    @Test
    public void testContentDistribution() throws Exception
    {
        final String topicUrl = getMouthTestTopic();
        ensureSubscribed(topicUrl);
        setupPublisherFTC(1);
        setupSubscriberFTC("subscriber1", 1);
        doTestSuccessfulContentDistribution(topicUrl);
    }

    @Test
    public void testContentDistributionWithOnBehalf() throws Exception
    {
        final String topicUrl = getBocaTestTopic();
        ensureSubscribed(topicUrl);
        setupPublisherFTC(1);
        setupSubscriberFTC("subscriber2", 1);
        doTestSuccessfulContentDistribution(topicUrl);
        assertEquals(TEST_ON_BEHALF_COUNT, dataStore.getTotalSubscriberCount(new URI(topicUrl)));
    }

    protected void ensureSubscribed(final String topicUrl) throws InterruptedException, URISyntaxException
    {
        final Set<TopicSubscription> stored = ponderUntilSubscriptionStored(new URI(topicUrl));
        assertThat(stored.size(), is(1));
    }
}
