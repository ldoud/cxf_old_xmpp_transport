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

package org.apache.cxf.transport.xmpp.iq;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.xmpp.common.AbstractDestination;
import org.apache.cxf.transport.xmpp.smackx.soap.SoapPacket;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;

/**
 * Listens for XMPP IQ packets targeted for this service. Any IQ packets received are used to create CXF
 * messages. The CXF messages are then passed to a message observer for processing.
 * 
 * @author Leon Doud
 */
public class IQDestination extends AbstractDestination implements PacketListener {

    public IQDestination(EndpointInfo epInfo) {
        super(epInfo);
    }

    @Override
    public void setXmppConnection(XMPPConnection newConnection) {
        super.setXmppConnection(newConnection);

        newConnection.addPacketListener(this, new PacketFilter() {
            @Override
            public boolean accept(Packet anyPacket) {
                return true;
            }
        });
    }

    @Override
    public void processPacket(Packet msg) {
        SoapPacket soapMsg = (SoapPacket)msg;

        Message cxfMsg = new MessageImpl();
        cxfMsg.setContent(InputStream.class,
                          new ByteArrayInputStream(soapMsg.getChildElementXML().getBytes()));

        Exchange msgExchange = new ExchangeImpl();
        msgExchange.setConduit(new IQBackChannelConduit(soapMsg, getXmppConnection()));
        cxfMsg.setExchange(msgExchange);

        // TODO Fix this so a different thread is used.
        getMessageObserver().onMessage(cxfMsg);
    }

}
