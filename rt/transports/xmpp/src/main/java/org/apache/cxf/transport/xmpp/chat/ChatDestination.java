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

import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.xmpp.strategy.ConnectionStrategy;
import org.apache.cxf.transport.xmpp.strategy.MessageReceiptStrategy;
import org.apache.cxf.transport.xmpp.strategy.XMPPService;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * Listens for XMPP IQ packets targeted for this service. Any IQ packets received are used to create CXF
 * messages. The CXF messages are then passed to a message observer for processing.
 * 
 * @author Leon Doud
 */
public class ChatDestination extends AbstractDestination implements XMPPService {
    
    private static final Logger LOGGER = LogUtils.getLogger(ChatDestination.class);

    private ConnectionStrategy xmppConnection;
    private MessageReceiptStrategy messageReceiver;
    
    public ChatDestination(EndpointReferenceType ref, EndpointInfo epInfo) {
        super(ref, epInfo);
    }
    
    @Override
    public synchronized void setMessageObserver(MessageObserver observer) {
        super.setMessageObserver(observer);
        
        // Just in case the message observer changes 
        // pass it along to the XMPP message listener.
        if (messageReceiver != null && observer != null) {
            messageReceiver.setMessageObserver(observer);
        }
    }
    
    @Override
    protected void activate() {
        super.activate();
        LOGGER.info("Destination activation");
        
        // Setup to process messages before connecting.
        messageReceiver.setMessageObserver(getMessageObserver());
        xmppConnection.activate();
    }
    
    @Override
    protected void deactivate() {
        super.deactivate();
        LOGGER.info("Destination deactivation");
        
        xmppConnection.deactivate();
    }

    @Override
    protected Conduit getInbuiltBackChannel(Message msg) {
        return msg.getExchange().getConduit(msg);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    public void setConnectionStrategy(ConnectionStrategy strat) {
        xmppConnection = strat;
    }

    @Override
    public void setMessageReceiptStrategy(MessageReceiptStrategy strat) {
        messageReceiver = strat;
    }

}
