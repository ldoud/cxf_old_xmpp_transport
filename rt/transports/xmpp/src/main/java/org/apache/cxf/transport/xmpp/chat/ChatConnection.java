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

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.observer.Listener;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule.MessageEvent;
import tigase.jaxmpp.j2se.Jaxmpp;

public class ChatConnection {

    private Jaxmpp contact = new Jaxmpp();

    public ChatConnection(String username, String password) {
        contact.getProperties().setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(username));
        contact.getProperties().setUserProperty(SessionObject.PASSWORD, password);

        // Listen to chat messages that maybe replies to SOAP requests.
        contact.addListener(MessageModule.MessageReceived, new Listener<MessageModule.MessageEvent>() {
            @Override
            public void handleEvent(MessageEvent mesgReceived) throws JaxmppException {
                handleResponseMesg(mesgReceived);
            }
        });
    }

    private void handleResponseMesg(MessageEvent mesgReceived) {
        //String receivedThreadId = mesgReceived.getChat().getThreadId();
        Message responseMsg = new MessageImpl();
        try {
            responseMsg.setContent(InputStream.class, 
                                   new ByteArrayInputStream(mesgReceived.getMessage().getBody().getBytes()));
        } catch (XMLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void connect() throws JaxmppException {
        contact.login();
    }

    public void sendMsg(String msg, String jidOfService) throws JaxmppException {
        Chat chat = contact.createChat(JID.jidInstance(jidOfService));
        chat.sendMessage(msg);

    }
}
