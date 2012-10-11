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

package org.apache.cxf.transport.xmpp.pep;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.xmpp.common.AbstractDestination;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.pubsub.EventElement;
import org.jivesoftware.smackx.pubsub.ItemsExtension;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.SimplePayload;

public class PEPDestination extends AbstractDestination implements PacketListener{

    private String nodeName;
    
    public PEPDestination(EndpointInfo epInfo) {
        super(epInfo);
        nodeName = epInfo.getService().getName().toString();
    }

    @Override
    public void processPacket(Packet p) {
        
        Message msg = (Message)p;
        EventElement event = (EventElement)msg.getExtension(
            "event", "http://jabber.org/protocol/pubsub#event");

        if(nodeName.equals(event.getEvent().getNode())) {
            List<PacketExtension> extensions = event.getExtensions();
            for(PacketExtension pe : extensions) {
                if (pe instanceof ItemsExtension) {
                    ItemsExtension ie = (ItemsExtension)pe;
                    @SuppressWarnings("unchecked")
                    List<PayloadItem<SimplePayload>> items = (List<PayloadItem<SimplePayload>> )ie.getItems();
                    invokeSoapMessages(items);
                }
            }
        }
    }
    
    public void invokeSoapMessages(List<PayloadItem<SimplePayload>> items) {

        for (PayloadItem<SimplePayload> pi : items) {
            SimplePayload soapMsg = pi.getPayload();

            org.apache.cxf.message.Message cxfMsg = new MessageImpl();
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
