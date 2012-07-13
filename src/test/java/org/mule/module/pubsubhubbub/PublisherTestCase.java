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

import org.junit.Test;

public class PublisherTestCase extends AbstractPuSHTestCase
{
    @Override
    protected String getConfigResources()
    {
        return "push-publisher-tests-config.xml,publisher-stub-config.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        setupSubscriberFTC("subscriber", 0);
        super.doSetUp();
    }

    @Test
    public void testContentDistribution() throws Exception
    {
        final String topicUrl = getMouthTestTopic();
        ensureSubscribed(topicUrl);
        setupPublisherFTC(1);
        setupSubscriberFTC("subscriber", 1);
        doTestSuccessfulContentDistribution(topicUrl);
    }

    @Override
    protected void doNotifyHubOfNewContent(final String topicUrl) throws Exception
    {
        // instead of doing a direct HTTP call, we call a flow that uses the
        // publisher message processor
        muleContext.getClient().send("vm://notifyNewContent", topicUrl, null);
    }
}
