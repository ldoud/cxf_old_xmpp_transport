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

package org.apache.cxf.transport.xmpp.messaging;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

public class XMPPService extends AbstractFeature implements ConnectionStrategy {
    
    private static final Logger LOGGER = LogUtils.getLogger(XMPPService.class);
    
    // Holds listeners until XMPP connection is made.
    private List<MessageReceiptStrategy> unregisteredListeners = new ArrayList<MessageReceiptStrategy>();
    
    private String xmppServer;
    private Credentials credentials;
    private XMPPConnection xmppConnection;
    
    @Override
    public void setServer(String server) {
        xmppServer = server;
    }

    @Override
    public void setAuthorizationMechanism(Credentials auth) {
        credentials = auth;
    }
    
    @Override
    public synchronized boolean activate() {
        if (xmppConnection == null) {
            xmppConnection = new XMPPConnection(xmppServer);
            
            // Add listeners that were added before activation.
            for (MessageReceiptStrategy xmppListener : unregisteredListeners) {
                registerListener(xmppListener);
            }
        }
        
        if (!xmppConnection.isConnected()) {
            try {
                // Need to connect before logging in.
                xmppConnection.connect();
                xmppConnection.login(credentials.getUsername(), credentials.getPassword(), null);
                
                LOGGER.info("Connection made as user: " + xmppConnection.getUser());
                
            } catch (XMPPException connectionError) {
                LOGGER.log(Level.SEVERE, 
                           "Unable to connect to XMPP server: " + xmppServer, 
                           connectionError);
            }
        }
        
        return xmppConnection.isConnected();
    }

    @Override
    public synchronized void deactivate() {
        if (xmppConnection != null && xmppConnection.isConnected()) {
            xmppConnection.disconnect();
        }
    }

    @Override
    public void registerListener(MessageReceiptStrategy xmppListener) {
        if (xmppConnection == null) {
            unregisteredListeners.add(xmppListener);
            
        } else if (xmppListener instanceof ChatManagerListener) {
            xmppConnection.getChatManager().addChatListener((ChatManagerListener)xmppListener);
            
        } else {
            LOGGER.severe("Received unsupported XMPP listener: " + xmppListener.getClass().getName());
        }
    }

    @Override
    public void unregisterListener(MessageReceiptStrategy xmppListener) {
        if (xmppListener instanceof ChatManagerListener) {
            xmppConnection.getChatManager().removeChatListener((ChatManagerListener)xmppListener);
        } else {
            LOGGER.severe("Received unsupported XMPP listener: " + xmppListener.getClass().getName());
        }
    }

    /**
     * Attaches this XMPP connection to a web service server.
     * {@inheritDoc}
     */
    @Override
    public void initialize(Server server, Bus bus) {
        Destination dest = server.getDestination();
        if (dest instanceof XMPPDestination) {
            XMPPDestination xmppDestination = (XMPPDestination)dest;
            xmppDestination.setConnectionStrategy(this);
        } else {
            LOGGER.severe("This feature only supports XMPP servers");
        }
    }
    
    /**
     * Attaches this XMPP connection to a web service client.
     * {@inheritDoc}
     */
    @Override
    public void initialize(Client client, Bus bus) {

//        client.getEndpoint().getService().put(XMPPService.class.toString(), this);
        
        // WTF client.getConduit() calls activate on the conduit
        Conduit con = client.getConduit();
        if (con instanceof XMPPConduit) {
            XMPPConduit xmppConduit = (XMPPConduit)con;
            xmppConduit.setConnectionStrategy(this);
        } else {
            LOGGER.severe("This feature only supports XMPP clients");
        }

//        LOGGER.severe("This feature cannot be set on clients (" 
//            + client.getEndpoint().getService().getName().toString() + ") ");
//        client.getEndpoint().getEndpointInfo().setProperty(ConnectionStrategy.class.toString(), this);
    }
    
    @Override
    public void sendChatMessage(final Message cxfMsg, String jid, final MessageObserver replyChannel) 
        throws IOException {
        
        if (xmppConnection != null && xmppConnection.isConnected()) {
            Chat chat = xmppConnection.getChatManager().createChat(jid, new MessageListener() {
                
                @Override
                public void processMessage(Chat chatSesion, org.jivesoftware.smack.packet.Message message) {
                    // Extract the SOAP message from the XMPP chat message.
                    Message responseMsg = new MessageImpl();
                    responseMsg.setContent(InputStream.class, new ByteArrayInputStream(message.getBody().getBytes()));
                    
                    // Put the reply message back into the exchange the sent the message.
                    Exchange replyExchange = cxfMsg.getExchange();
                    replyExchange.setInMessage(responseMsg);
                    
                    // Notify the observer that the reply was received.
                    replyChannel.onMessage(responseMsg);
                }
            });

            // Put the out bound message into a buffer.
            CachedOutputStream output = (CachedOutputStream)cxfMsg.getContent(OutputStream.class);
            StringBuilder soapEnvelope = new StringBuilder();
            output.writeCacheTo(soapEnvelope);
            
            try {
                // Send the message using XMPP chat.
                chat.sendMessage(soapEnvelope.toString());
            } catch (XMPPException e) {
                throw new IOException(e);
            }
        } else {
            LOGGER.severe("Connection is not currently available");
        }
    }
}
