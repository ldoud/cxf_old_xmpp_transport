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
import java.io.InputStream;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.xmpp.messaging.MessageReceiptStrategy;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;

public class ChatServerChannel implements MessageReceiptStrategy, MessageListener, ChatManagerListener {
    
    // This object triggers the Apache CXF processing of a SOAP message.
    private MessageObserver cxfMsgObserver;
    
    /**
     * Called when another XMPP client starts a session with the service.
     * Start listening to the chat session in order to receive SOAP messages.
     * Required the the XMPP API (Smack) interface ChatManagerListener
     * {@inheritDoc}
     */
    @Override
    public void chatCreated(Chat chatSession, boolean createdLocally) {
        chatSession.addMessageListener(this);
    }

    /**
     * Called when a XMPP chat message is received.
     * Required the the XMPP API (Smack) interface MessageListener
     * {@inheritDoc}
     */
    @Override
    public void processMessage(Chat chatSession, org.jivesoftware.smack.packet.Message xmppChatMsg) {
        // The contents of the XMPP message is a SOAP message.
        // Put the SOAP message into a CXF message.
        org.apache.cxf.message.Message cxfMsg = new MessageImpl();
        cxfMsg.setContent(InputStream.class, 
                          new ByteArrayInputStream(xmppChatMsg.getBody().getBytes()));

        // The back channel is how the return value is sent to the client.
        // So its necessary to use the same chat session that received the message.
        Exchange msgExchange = new ExchangeImpl();
        msgExchange.setConduit(new ChatServerReplyChannel(chatSession));
        cxfMsg.setExchange(msgExchange);

        getMessageObserver().onMessage(cxfMsg);
    }
    
    @Override
    public synchronized void setMessageObserver(MessageObserver observer) {
        cxfMsgObserver = observer;
    }
    
    /**
     * The observer maybe changed on the fly.
     * Use this method to make sure the current reference is used.
     * @return The observer that pushes the SOAP message through the CXF stack.
     */
    protected synchronized MessageObserver getMessageObserver() {
        return cxfMsgObserver;
    }
}
