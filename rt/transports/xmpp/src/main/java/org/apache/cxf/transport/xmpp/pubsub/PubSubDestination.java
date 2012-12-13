/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.transport.xmpp.pubsub;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.xmpp.common.AbstractDestination;
import org.jivesoftware.smackx.pubsub.ItemPublishEvent;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.SimplePayload;
import org.jivesoftware.smackx.pubsub.listener.ItemEventListener;

public class PubSubDestination extends AbstractDestination implements
    ItemEventListener<PayloadItem<SimplePayload>> {

    public PubSubDestination(EndpointInfo epInfo) {
        super(epInfo);
    }

    @Override
    public void handlePublishedItems(ItemPublishEvent<PayloadItem<SimplePayload>> events) {

        for (PayloadItem<SimplePayload> pi : events.getItems()) {
            SimplePayload soapMsg = pi.getPayload();

            Message cxfMsg = new MessageImpl();
            cxfMsg.setContent(InputStream.class, new ByteArrayInputStream(soapMsg.toXML().getBytes()));

            Exchange msgExchange = new ExchangeImpl();
            msgExchange.setOneWay(true);
            msgExchange.setDestination(this);
            cxfMsg.setExchange(msgExchange);

            // TODO Fix this so a different thread is used.
            getMessageObserver().onMessage(cxfMsg);
        }
    }
}
