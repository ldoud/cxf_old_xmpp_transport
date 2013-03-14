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

import java.io.IOException;

import junit.framework.Assert;

import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.xmpp.messaging.ConnectionStrategy;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.modules.roster.AskSubscriptionType;
import org.apache.vysper.xmpp.modules.roster.RosterItem;
import org.apache.vysper.xmpp.modules.roster.SubscriptionType;
import org.apache.vysper.xmpp.modules.roster.persistence.RosterManager;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.XMPPException.ErrorCondition;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.observer.BaseEvent;
import tigase.jaxmpp.core.client.observer.Listener;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule.MessageEvent;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

public class ChatConduitTest {

    private static final String CERT_FILENAME = "bogus_mina_tls.cert";
    private static final String CERT_PASSWORD = "boguspw";

    private static final String DOMAIN_NAME = "localhost.localdomain";
    private static final String USER2_NAME = "service1@" + DOMAIN_NAME;
    private static final String USER2_PASSWORD = "service1";

    private static final String USER1_NAME = "user1@" + DOMAIN_NAME;
    private static final String USER1_PASSWORD = "user1";

    private XMPPServer server = new XMPPServer(DOMAIN_NAME);

    @Before
    public void setupXmppServer() throws Exception {
        server.addEndpoint(new TCPEndpoint());
        server.setTLSCertificateInfo(ClassLoader.getSystemResourceAsStream(CERT_FILENAME), CERT_PASSWORD);

        // Store data in memory.
        StorageProviderRegistry providerRegistry = new MemoryStorageProviderRegistry();
        server.setStorageProviderRegistry(providerRegistry);

        // Create test users.
        Entity userEntity = EntityImpl.parse(USER1_NAME);
        Entity serviceEntity = EntityImpl.parse(USER2_NAME);

        AccountManagement accountManagement = (AccountManagement)providerRegistry.retrieve(AccountManagement.class);
        accountManagement.addUser(userEntity, USER1_PASSWORD);
        accountManagement.addUser(serviceEntity, USER2_PASSWORD);

        RosterManager rosterManager = (RosterManager)providerRegistry.retrieve(RosterManager.class);
        rosterManager.addContact(serviceEntity, new RosterItem(userEntity, SubscriptionType.BOTH,
                                                               AskSubscriptionType.ASK_SUBSCRIBED));
        rosterManager.addContact(userEntity, new RosterItem(serviceEntity, SubscriptionType.BOTH,
                                                            AskSubscriptionType.ASK_SUBSCRIBED));

        server.start();
        server.stop();
    }

    @After
    public void cleanupXmppServer() {
        server.stop();
    }

    @Test
    public void chatMessageRegistration() throws JaxmppException {
        Jaxmpp conduitConnection = new Jaxmpp();
        Jaxmpp clientConnection = new Jaxmpp();

        try {
            conduitConnection.getConnectionConfiguration().setServer(DOMAIN_NAME);
            conduitConnection.getConnectionConfiguration().setUserJID(USER2_NAME);
            conduitConnection.getConnectionConfiguration().setUserPassword(USER2_PASSWORD);
            conduitConnection.login();

            clientConnection.getConnectionConfiguration().setServer(DOMAIN_NAME);
            clientConnection.getConnectionConfiguration().setUserJID(USER1_NAME);
            clientConnection.getConnectionConfiguration().setUserPassword(USER1_PASSWORD);
            clientConnection.login();

            // RosterCallback conduitRosterCallback = new RosterCallback();
            // conduitConnection.getRoster().add(BareJID.bareJIDInstance(USER_NAME), "user1", conduitRosterCallback);
            // if (!conduitRosterCallback.isCallSuccessfull()) {
            // Assert.fail("Couldn't setup the server's roster");
            // }

            conduitConnection.addListener(new Listener<BaseEvent>() {

                @Override
                public void handleEvent(BaseEvent be) throws JaxmppException {
                    System.out.println("EventType: " + be.toString());
                }

            });

            conduitConnection.getModule(MessageModule.class).addListener(new Listener<MessageModule.MessageEvent>() {
                @Override
                public void handleEvent(MessageEvent be) throws JaxmppException {
                    System.out.println("MessageEvent: " + be.toString());
                }
            });

            // RosterCallback clientRosterCallback = new RosterCallback();
            // clientConnection.getRoster().add(BareJID.bareJIDInstance(SERVICE_NAME), "service1",
            // clientRosterCallback);
            // if (!clientRosterCallback.isCallSuccessfull()) {
            // Assert.fail("Couldn't setup the client's roster");
            // }

            // Chat testChatSession = clientConnection.createChat(JID.jidInstance(USER2_NAME));
            // testChatSession.sendMessage("hello world");

        } finally {
            if (conduitConnection.isConnected()) {
                conduitConnection.disconnect();
            }
            if (clientConnection.isConnected()) {
                clientConnection.disconnect();
            }
        }

        // ChatConduit conduit = new ChatConduit(null, conduitConnection);
    }

    public void sendMessage() {
        String testJid = "service1@localhost.localdomain";
        AttributedURIType address = new AttributedURIType();
        address.setValue(testJid);

        EndpointReferenceType endPoint = new EndpointReferenceType();
        endPoint.setAddress(address);

        // Create a service client that points at service1@localhost.localdomain
        ChatConduit conduit = new ChatConduit(endPoint, new Jaxmpp());

        // Give it a connection that can be tested.
        ConnectionStrategy xmppConnection = Mockito.mock(ConnectionStrategy.class);
        // conduit.setConnectionStrategy(xmppConnection);

        // See if the reply is passed along.
        MessageObserver observer = Mockito.mock(MessageObserver.class);
        conduit.setMessageObserver(observer);

        Message msg = Mockito.mock(Message.class);
        try {
            conduit.close(msg);

            // Verify the right parameters were used to send the chat message
            Mockito.verify(xmppConnection).sendChatMessage(msg, testJid, observer);
        } catch (IOException e) {
            Assert.fail("Exception while sending the message");
        }

    }

    private class RosterCallback implements AsyncCallback {

        Boolean success;

        @Override
        public synchronized void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
            success = false;
            notifyAll();
        }

        @Override
        public synchronized void onSuccess(Stanza responseStanza) throws JaxmppException {
            success = true;
            notifyAll();
        }

        @Override
        public synchronized void onTimeout() throws JaxmppException {
            success = false;
            notifyAll();
        }

        public synchronized Boolean isCallSuccessfull() {
            if (success == null) {
                try {
                    wait(1000000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } //
            }

            return success;
        }
    }

    public static void main(String[] args) throws Exception {
        XMPPServer server = new XMPPServer(DOMAIN_NAME);
        server.addEndpoint(new TCPEndpoint());
        server.setTLSCertificateInfo(ClassLoader.getSystemResourceAsStream(CERT_FILENAME), CERT_PASSWORD);

        // Store data in memory.
        StorageProviderRegistry providerRegistry = new MemoryStorageProviderRegistry();
        server.setStorageProviderRegistry(providerRegistry);

        // Create test users.
        Entity user1Entity = EntityImpl.parse(USER1_NAME);
        Entity user2Entity = EntityImpl.parse(USER2_NAME);

        AccountManagement accountManagement = (AccountManagement)providerRegistry.retrieve(AccountManagement.class);
        accountManagement.addUser(user1Entity, USER1_PASSWORD);
        accountManagement.addUser(user2Entity, USER2_PASSWORD);

        RosterManager rosterManager = (RosterManager)providerRegistry.retrieve(RosterManager.class);
        rosterManager.addContact(user2Entity, new RosterItem(user1Entity, 
                                                             SubscriptionType.BOTH));
        rosterManager.addContact(user1Entity, new RosterItem(user2Entity, 
                                                             SubscriptionType.BOTH));

        server.start();
        Thread.sleep(60000);
        server.stop();
    }
}
