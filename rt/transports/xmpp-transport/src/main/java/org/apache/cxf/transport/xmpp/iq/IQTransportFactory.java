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

package org.apache.cxf.transport.xmpp.iq;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Resource;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractTransportFactory;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.xmpp.connection.XMPPConnectionFactory;
import org.apache.cxf.transport.xmpp.connection.XMPPTransportFactory;
import org.apache.cxf.transport.xmpp.smackx.soap.SoapProvider;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.provider.ProviderManager;

/**
 * Creates both XMPP destinations for servers and conduits for clients. Web service providers or web service
 * clients that use the XMPP transport namespace of "http://cxf.apache.org/transports/xmpp" as their transport
 * ID will trigger the use of this factory for the creation of XMPPDestination (provider) or XMPPClientConduit
 * (client).
 * 
 * @author Leon Doud
 */
public class IQTransportFactory extends AbstractTransportFactory implements DestinationFactory,
    ConduitInitiator, XMPPTransportFactory {

    public static final List<String> DEFAULT_NAMESPACES = Arrays
        .asList("http://cxf.apache.org/transports/xmpp");

    private XMPPConnectionFactory destinationConnectionFactory;
    private XMPPConnectionFactory conduitConnectionFactory;

    public IQTransportFactory() throws XMPPException {
        super();
        setTransportIds(DEFAULT_NAMESPACES);

        SoapProvider xmppSoapFeature = new SoapProvider();

        ProviderManager.getInstance().addIQProvider("Envelope", "http://www.w3.org/2003/05/soap-envelope",
                                                    xmppSoapFeature);
        ProviderManager.getInstance().addIQProvider("Envelope", "http://schemas.xmlsoap.org/soap/envelope/",
                                                    xmppSoapFeature);
    }

    /**
     * Set the bus used via Spring configuration.
     */
    @Resource(name = "cxf")
    public void setBus(Bus bus) {
        super.setBus(bus);
    }

    /**
     * Creates a destination for a service that receives XMPP messages.
     */
    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        IQDestination dest = new IQDestination(endpointInfo);

        try {
            dest.setXmppConnection(destinationConnectionFactory.login(endpointInfo));
        } catch (XMPPException e) {
            throw new IOException(e);
        }

        return dest;
    }

    /**
     * Creates a conduit for a client that all share a single XMPP connection. The connection is shared via
     * the bus.
     */
    @Override
    public Conduit getConduit(EndpointInfo endpointInfo) throws IOException {
        return getConduit(endpointInfo, endpointInfo.getTarget());
    }

    /**
     * Creates a conduit for a client that all share a single XMPP connection. The connection is shared via
     * the bus.
     */
    @Override
    public Conduit getConduit(EndpointInfo endpointInfo, EndpointReferenceType endpointType)
        throws IOException {
        IQClientConduit conduit = new IQClientConduit(endpointType);

        try {
            conduit.setXmppConnection(conduitConnectionFactory.login(endpointInfo));
        } catch (XMPPException e) {
            throw new IOException(e);
        }

        return conduit;
    }

    @Override
    public void setDestinationConnectionFactory(XMPPConnectionFactory factory) {
        destinationConnectionFactory = factory;
    }

    @Override
    public void setConduitConnectionFactory(XMPPConnectionFactory factory) {
        conduitConnectionFactory = factory;
    }
}
