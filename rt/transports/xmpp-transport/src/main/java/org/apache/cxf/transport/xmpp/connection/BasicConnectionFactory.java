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

package org.apache.cxf.transport.xmpp.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.buslifecycle.BusLifeCycleListener;
import org.apache.cxf.buslifecycle.BusLifeCycleManager;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

public class BasicConnectionFactory implements XMPPConnectionFactory, BusLifeCycleListener {
    private static final Logger LOGGER = LogUtils.getLogger(BasicConnectionFactory.class);

    private Map<String, XMPPConnection> connectionsKeyByResources = new HashMap<String, XMPPConnection>();

    // Configuration options used to connect to XMPP server.
    private String xmppServiceName;
    private String xmppUsername;
    private String xmppPassword;

    // Listen to this to shutdown connections.
    private Bus bus;

    /**
     * Required configuration option for connecting to the XMPP server.
     * 
     * @param xmppServiceName The full name of the XMPP server.
     */
    public void setXmppServiceName(String xmppServiceName) {
        this.xmppServiceName = xmppServiceName;
    }

    /**
     * Required configuration option for connecting to the XMPP server.
     * 
     * @param xmppUsername The username for the XMPP connection.
     */
    public void setXmppUsername(String xmppUsername) {
        this.xmppUsername = xmppUsername;
    }

    /**
     * Required configuration option for connecting to the XMPP server.
     * 
     * @param xmppPassword The password for the XMPP connection.
     */
    public void setXmppPassword(String xmppPassword) {
        this.xmppPassword = xmppPassword;
    }

    public void setBus(Bus listenForShutdown) {
        bus = listenForShutdown;
        BusLifeCycleManager mgr = bus.getExtension(BusLifeCycleManager.class);
        mgr.registerLifeCycleListener(this);
    }

    @Override
    public synchronized XMPPConnection login(EndpointInfo epi) throws XMPPException {
        String resourceName = createResourceName(epi, bus);
        XMPPConnection xmppConnection = connectionsKeyByResources.get(resourceName);

        if (xmppConnection == null) {
            xmppConnection = new XMPPConnection(xmppServiceName);
            xmppConnection.connect();
            xmppConnection.login(xmppUsername, xmppPassword, resourceName);
            connectionsKeyByResources.put(resourceName, xmppConnection);
            LOGGER.info("Logged in with JID: " + xmppConnection.getUser());
        }

        return xmppConnection;
    }

    protected String createResourceName(EndpointInfo epi, Bus theBus) {
        return epi.getService().getName().toString();
    }

    @Override
    public void initComplete() {
        // Nothing
    }

    @Override
    public void postShutdown() {
        for (XMPPConnection conn : connectionsKeyByResources.values()) {
            conn.disconnect();
        }
    }

    @Override
    public void preShutdown() {
        // Nothing
    }

}
