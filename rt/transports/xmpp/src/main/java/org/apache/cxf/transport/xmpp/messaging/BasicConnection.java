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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.transport.Destination;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

public class BasicConnection extends AbstractFeature implements ConnectionStrategy {
    
    private static final Logger LOGGER = LogUtils.getLogger(BasicConnection.class);
    
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

    @Override
    public void initialize(Server server, Bus bus) {
        Destination dest = server.getDestination();
        if (dest instanceof XMPPDestination) {
            XMPPDestination xmppService = (XMPPDestination)dest;
            xmppService.setConnectionStrategy(this);
        } else {
            LOGGER.severe("This feature only supports XMPP services");
        }
    }
}
