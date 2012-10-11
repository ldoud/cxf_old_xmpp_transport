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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.xmpp.common.AbstractConduit;
import org.apache.cxf.transport.xmpp.smackx.soap.SoapPacket;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;

public class IQClientConduit extends AbstractConduit implements PacketListener {

    private static final Logger LOGGER = LogUtils.getLogger(IQClientConduit.class);

    // Messages sent to the service are stored in this table based on
    // their PacketId so they can be retrieved when a response is received.
    private AbstractMap<String, Exchange> exchangeCorrelationTable = new HashMap<String, Exchange>();

    public IQClientConduit(EndpointReferenceType target) {
        super(target);
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
    public void close(Message msg) throws IOException {
        // Take the contents of the cached buffer
        // and write them to the service using XMPP.
        CachedOutputStream output = (CachedOutputStream)msg.getContent(OutputStream.class);

        // Null indicates this message represents the reply from the service.
        // This means that the request was already sent to the service and a response was received.
        if (output != null) {
            StringBuilder soapEnvelope = new StringBuilder();
            output.writeCacheTo(soapEnvelope);

            SoapPacket soapOverXmpp = new SoapPacket();
            soapOverXmpp.setEnvelope(soapEnvelope.toString());

            // TODO Target JID will have to become dynamic.
            // Pass "getTarget().getAddress().getValue()" to the
            // discovery implementation class and have it return
            // the dynamic endpoint address as a full JID.
            String fullJid = getTarget().getAddress().getValue();
            LOGGER.info("Sending message to JID: " + fullJid);
            soapOverXmpp.setTo(fullJid);

            // Save the message so it can be used when the response is received.
            exchangeCorrelationTable.put(soapOverXmpp.getPacketID(), msg.getExchange());

            // Send the message to the service.
            getXmppConnection().sendPacket(soapOverXmpp);
        }
    }

    /**
     * Triggered when a response is received. Note this conduit can call many services. Responses will need to
     * be correlated with their requests.
     */
    @Override
    public void processPacket(Packet xmppResponse) {
        Message responseMsg = new MessageImpl();
        SoapPacket soapMsg = (SoapPacket)xmppResponse;
        responseMsg.setContent(InputStream.class, new ByteArrayInputStream(soapMsg.getChildElementXML()
            .getBytes()));

        // TODO Fix this to handle error replies from XMPP server.
        // TODO Fix this to handle exchanges that don't exist.
        Exchange msgExchange = exchangeCorrelationTable.remove(xmppResponse.getPacketID());
        msgExchange.setInMessage(responseMsg);

        // TODO Fix this so the response is processed by a different thread.
        getMessageObserver().onMessage(responseMsg);
    }

}
