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

package org.apache.cxf.transport.xmpp.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule.MessageEvent;
import tigase.jaxmpp.j2se.Jaxmpp;

public class ChatConduit extends AbstractConduit {

    private static final Logger LOGGER = LogUtils.getLogger(ChatConduit.class);

    private Jaxmpp xmppConnection = new Jaxmpp();

    // Messages sent to the service are stored in this table based on
    // their the id of the chat so they can be retrieved when a response is received.
    private AbstractMap<Long, Exchange> exchangeCorrelationTable = new HashMap<Long, Exchange>();

    public ChatConduit(EndpointReferenceType refType, Jaxmpp connectionToXmppServer) {
        super(refType);
        xmppConnection = connectionToXmppServer;
    }

    @Override
    public void close(Message msg) throws IOException {
        // The prepare method in this conduit setup a CachedOutputStream in the message.
        CachedOutputStream output = (CachedOutputStream)msg.getContent(OutputStream.class);

        if (output != null) {
            // TODO Use another object to select JID of the service.
            // This other object should allow for any strategy to select the service to call.
            String jidOfService = getTarget().getAddress().getValue();

            try {
                // Each SOAP message is sent over its own chat so any response
                // can be correlated back to the request.
                Chat chat = xmppConnection.createChat(JID.jidInstance(jidOfService));

                // Save chat message for response if not one way message.
                if (!msg.getExchange().isOneWay()) {
                    exchangeCorrelationTable.put(chat.getId(), msg.getExchange());
                }

                chat.sendMessage(output.toString());
            } catch (JaxmppException e) {
                LOGGER.log(Level.SEVERE, "Failed to send chat message", e);
            }
        }

    }

    @Override
    public void prepare(Message msg) throws IOException {
        msg.setContent(OutputStream.class, new CachedOutputStream());
    }

    public void handleResponseMesg(MessageEvent mesgReceived) {
        Exchange msgExchange = exchangeCorrelationTable.remove(mesgReceived.getChat().getId());

        if (msgExchange != null) {
            Message responseMsg = new MessageImpl();
            try {
                String soapResponse = mesgReceived.getMessage().getBody();
                responseMsg.setContent(InputStream.class, new ByteArrayInputStream(soapResponse.getBytes()));
                msgExchange.setInMessage(responseMsg);
                getMessageObserver().onMessage(responseMsg);
            } catch (XMLException e) {
                LOGGER.log(Level.SEVERE, "Invalid XML received in response message", e);
            }

        } else {
            LOGGER.severe("Unexpected response");
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected void activate() {
        super.activate();
        if (!xmppConnection.isConnected()) {
            try {
                xmppConnection.login();
            } catch (JaxmppException e) {
                LOGGER.log(Level.SEVERE, "Could not log into XMPP server", e);
            }
        }
        // TODO any discovery of the service
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        // TODO disconnect if last client using this connection
    }
}
