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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractTransportFactory;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

/**
 * After receiving a Bus reference this class registers itself as an XMPPDestination. Web service providers
 * that use one of the XMPP URI prefixes will trigger the use of this factory for creation of XMPPDestination.
 * 
 * @author Leon Doud
 */
public class XMPPTransportFactory extends AbstractTransportFactory implements DestinationFactory {

    public static final List<String> DEFAULT_NAMESPACES = Arrays
    .asList("http://cxf.apache.org/transports/xmpp");
    
    private static final Logger LOGGER = LogUtils.getLogger(XMPPTransportFactory.class);

    // TODO Make these configurable.
    private String serviceName = "localhost.localdomain";
    private String username = "service1";
    private String password = "service1";

    public XMPPTransportFactory() throws XMPPException {
        super();
        setTransportIds(DEFAULT_NAMESPACES);
    }

    @Resource(name = "cxf")
    public void setBus(Bus bus) {
        super.setBus(bus);
    }

    /**
     * {@inheritDoc}
     */
    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        XMPPDestination dest = new XMPPDestination(endpointInfo);

        try {
            String resource = endpointInfo.getService().getName().toString();

            XMPPConnection xmppConnection = new XMPPConnection(serviceName);
            xmppConnection.connect();
            xmppConnection.login(username, password, resource);
            LOGGER.info("Connected using jid: " + xmppConnection.getUser());

            dest.setConnection(xmppConnection);
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Unable to connect");
        }

        return dest;
    }
}
