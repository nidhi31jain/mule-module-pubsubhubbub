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
import java.util.Set;

import org.junit.Test;
import org.mule.module.pubsubhubbub.data.TopicSubscription;
import org.mule.tck.functional.CountdownCallback;
import org.mule.tck.functional.FunctionalTestComponent;

public class SubscriberTestCase extends AbstractPuSHTestCase
{
    private FunctionalTestComponent subscriberFTC;
    private CountdownCallback subscriberCC;

    @Override
    protected String getConfigResources()
    {
        return "push-subscriber-tests-config.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        setupSubscriberFTC(0);
        super.doSetUp();
    }

    private void setupSubscriberFTC(final int messagesExpected) throws Exception
    {
        subscriberFTC = getFunctionalTestComponent("subscriber");
        subscriberCC = new CountdownCallback(messagesExpected);
        subscriberFTC.setEventCallback(subscriberCC);
    }

    @Test
    public void testSubscribed() throws Exception
    {
        final Set<TopicSubscription> stored = ponderUntilSubscriptionStored(new URI(TEST_TOPIC));
        assertThat(stored.size(), is(1));
        assertThat(subscriberFTC.getReceivedMessagesCount(), is(0));
    }
}
