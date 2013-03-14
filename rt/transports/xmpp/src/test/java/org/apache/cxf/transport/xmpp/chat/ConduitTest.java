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

import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.modules.roster.RosterItem;
import org.apache.vysper.xmpp.modules.roster.SubscriptionType;
import org.apache.vysper.xmpp.modules.roster.persistence.RosterManager;
import org.apache.vysper.xmpp.server.XMPPServer;

public class ConduitTest {

    private static final String CERT_FILENAME = "bogus_mina_tls.cert";
    private static final String CERT_PASSWORD = "boguspw";

    private static final String DOMAIN_NAME = "localhost.localdomain";
    private static final String USER2_NAME = "service1@" + DOMAIN_NAME;
    private static final String USER2_PASSWORD = "service1";

    private static final String USER1_NAME = "user1@" + DOMAIN_NAME;
    private static final String USER1_PASSWORD = "user1";

    public void someTest() {
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
        rosterManager.addContact(user2Entity, new RosterItem(user1Entity, SubscriptionType.BOTH));
        rosterManager.addContact(user1Entity, new RosterItem(user2Entity, SubscriptionType.BOTH));

        server.start();
        Thread.sleep(60000);
        server.stop();
    }
}
