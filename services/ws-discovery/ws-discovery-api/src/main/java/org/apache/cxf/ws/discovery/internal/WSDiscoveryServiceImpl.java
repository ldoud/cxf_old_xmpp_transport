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

package org.apache.cxf.ws.discovery.internal;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.util.JAXBSource;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.Endpoint;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.wsaddressing.W3CEndpointReferenceBuilder;

import org.w3c.dom.Document;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.jaxb.JAXBContextCache;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.staxutils.transform.InTransformReader;
import org.apache.cxf.ws.discovery.WSDiscoveryClient;
import org.apache.cxf.ws.discovery.WSDiscoveryService;
import org.apache.cxf.ws.discovery.wsdl.ByeType;
import org.apache.cxf.ws.discovery.wsdl.HelloType;
import org.apache.cxf.ws.discovery.wsdl.ObjectFactory;
import org.apache.cxf.ws.discovery.wsdl.ProbeMatchType;
import org.apache.cxf.ws.discovery.wsdl.ProbeMatchesType;
import org.apache.cxf.ws.discovery.wsdl.ProbeType;
import org.apache.cxf.ws.discovery.wsdl.ScopesType;

public class WSDiscoveryServiceImpl implements WSDiscoveryService {
    Bus bus;
    Endpoint udpEndpoint;
    WSDiscoveryClient client;
    List<HelloType> registered = new CopyOnWriteArrayList<HelloType>();
    ObjectFactory factory = new ObjectFactory();
    boolean started;
    
    public WSDiscoveryServiceImpl(Bus b) {
        bus = b;
        client = new WSDiscoveryClient();
        update(bus.getProperties());
    }
    public WSDiscoveryServiceImpl() {
        bus = BusFactory.newInstance().createBus();
        client = new WSDiscoveryClient();
        update(bus.getProperties());
    }
    public WSDiscoveryServiceImpl(Bus b, Map<String, Object> props) {
        bus = b;
        client = new WSDiscoveryClient();
        update(props);
    }
    
    public final synchronized void update(Map<String, Object> props) {
        String address = (String)props.get("org.apache.cxf.service.ws-discovery.address");
        if (address != null) {
            client.setAddress(address);
        }
        if (udpEndpoint != null && !client.isAdHoc()) {
            udpEndpoint.stop();
            udpEndpoint = null;
            started = false;
        }
    }
    public WSDiscoveryClient getClient() {
        return client;
    }
    
    public HelloType register(EndpointReference ref) {
        startup();
        HelloType ht = client.register(ref); 
        registered.add(ht);
        return ht;
    }
    public void register(HelloType ht) {
        startup();
        client.register(ht); 
        registered.add(ht);
    }
    
    public void serverStarted(Server server) {
        startup();
        HelloType ht = new HelloType();
        ht.setScopes(new ScopesType());
        ht.setMetadataVersion(1);
        ht.getTypes().add(server.getEndpoint().getEndpointInfo().getInterface().getName());
        Object o = server.getEndpoint().get("ws-discovery-scopes");
        if (o != null) {
            setScopes(ht, o);
        }
        setXAddrs(ht, server);
        String uuid = (String)server.getEndpoint().get("ws-discovery-uuid");
        if (uuid != null) {
            //persistent uuid
            W3CEndpointReferenceBuilder builder = new W3CEndpointReferenceBuilder();
            builder.address(uuid);
            ht.setEndpointReference(builder.build());
        }
        ht = client.register(ht);
        registered.add(ht);
        server.getEndpoint().put(HelloType.class.getName(), ht);
    }

    private void setXAddrs(HelloType ht, Server server) {
        String s = (String)server.getEndpoint().get("ws-discovery-published-url");
        if (s == null) {
            s = (String)server.getEndpoint().getEndpointInfo().getProperty("ws-discovery-published-url");
        } 
        if (s == null) {
            s = (String)server.getEndpoint().get("publishedEndpointUrl");
        }
        if (s == null) {
            s = (String)server.getEndpoint().getEndpointInfo().getProperty("publishedEndpointUrl");
        }
        if (s == null) {
            s = server.getEndpoint().getEndpointInfo().getAddress();
        }
        if (s != null) {
            ht.getXAddrs().add(s);
        }
    }

    private void setScopes(HelloType ht, Object o) {
        if (o instanceof List) {
            List<?> l = (List)o;
            for (Object o2 : l) {
                ht.getScopes().getValue().add(o2.toString());
            }
        } else {
            ht.getScopes().getValue().add(o.toString());
        }
    }

    public void serverStopped(Server server) {
        HelloType ht = (HelloType)server.getEndpoint().get(HelloType.class.getName());
        if (ht != null) {
            unregister(ht);
        }
    }    
    
    
    public void unregister(HelloType ht) {
        registered.remove(ht);
        client.unregister(ht); 
    }
    
    
    
    public synchronized void startup() {
        if (!started && client.isAdHoc()) {
            Bus b = BusFactory.getAndSetThreadDefaultBus(bus);
            try {
                udpEndpoint = Endpoint.create(new WSDiscoveryProvider());
                Map<String, Object> props = new HashMap<String, Object>();
                props.put("jaxws.provider.interpretNullAsOneway", "true");
                udpEndpoint.setProperties(props);
                udpEndpoint.publish("soap.udp://239.255.255.250:3702");
                started = true;
            } finally {
                if (b != bus) {
                    BusFactory.setThreadDefaultBus(b);
                }
            }
        }
    }
    
    
    public ProbeMatchesType handleProbe(ProbeType pt) {
        List<HelloType> consider = new LinkedList<HelloType>(registered);
        //step one, consider the "types"
        //ALL types in the probe must be in the registered type
        if (pt.getTypes() != null && !pt.getTypes().isEmpty()) {
            ListIterator<HelloType> cit = consider.listIterator();
            while (cit.hasNext()) {
                HelloType ht = cit.next();
                boolean matches = true;
                for (QName qn : pt.getTypes()) {
                    if (!ht.getTypes().contains(qn)) {
                        matches = false;
                    }
                }
                if (!matches) {
                    cit.remove();
                }
            }
        }
        //next, consider the scopes
        matchScopes(pt, consider);

        if (consider.isEmpty()) {
            return null;
        }
        ProbeMatchesType pmt = new ProbeMatchesType();
        for (HelloType ht : consider) {
            ProbeMatchType m = new ProbeMatchType();
            m.setEndpointReference(ht.getEndpointReference());
            m.setScopes(ht.getScopes());
            m.setMetadataVersion(ht.getMetadataVersion());
            m.getTypes().addAll(ht.getTypes());
            m.getXAddrs().addAll(ht.getXAddrs());
            pmt.getProbeMatch().add(m);
        }
        return pmt;
    }
    
    
    private UUID toUUID(String scope) {
        URI uri = URI.create(scope);
        if (uri.getScheme() == null) {
            return UUID.fromString(scope);
        } else {
            if (uri.getScheme().equals("urn")) {
                uri = URI.create(uri.getSchemeSpecificPart()); 
            } 
            if (uri.getScheme().equals("uuid")) {
                return UUID.fromString(uri.getSchemeSpecificPart());
            }
        }
        return null;
    }

    private boolean compare(String s, String s2) {
        if (s != null) {
            return s.equalsIgnoreCase(s2);
        }
        return false;
    }
    private boolean matchURIs(URI probe, URI target) {
        if (compare(target.getScheme(), probe.getScheme()) 
            && compare(target.getAuthority(), probe.getAuthority())) {
            String[] ppath = probe.getPath().split("/");
            String[] tpath = target.getPath().split("/");
                    
            if (ppath.length <= tpath.length) {
                for (int i = 0; i < ppath.length; i++) {
                    if (!ppath[i].equals(tpath[i])) { 
                        return false;
                    }
                }
                return true;
            }
        }                    
        return false;
    }

    
    private void matchScopes(ProbeType pt, List<HelloType> consider) {
        if (pt.getScopes() == null || pt.getScopes().getValue().isEmpty()) {
            return;
        }
        String mb = pt.getScopes().getMatchBy();
        if (mb == null) {
            mb = "http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/rfc3986";
        }
        
        ListIterator<HelloType> cit = consider.listIterator();
        while (cit.hasNext()) {
            HelloType ht = cit.next();
            boolean matches = false;
            
            if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/rfc3986".equals(mb)) {
                matches = true;
                if (!pt.getScopes().getValue().isEmpty()) {
                    for (String ps : pt.getScopes().getValue()) {
                        boolean foundOne = false; 
                        URI psuri = URI.create(ps);
                        for (String hts : ht.getScopes().getValue()) {
                            URI hturi = URI.create(hts);
                            if (matchURIs(psuri, hturi)) {
                                foundOne = true;
                            }
                        }
                        matches &= foundOne;
                    }
                }
            } else if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/uuid".equals(mb)) {
                matches = true;
                if (!pt.getScopes().getValue().isEmpty()) {
                    for (String ps : pt.getScopes().getValue()) {
                        boolean foundOne = false; 
                        UUID psuuid = toUUID(ps);
                        for (String hts : ht.getScopes().getValue()) {
                            UUID htuuid = toUUID(hts);
                            if (!htuuid.equals(psuuid)) {
                                foundOne = true;
                            }
                        }
                        matches &= foundOne;
                    }
                }
            } else if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ldap".equals(mb)) {
                //LDAP not supported
                if (!pt.getScopes().getValue().isEmpty()) {
                    matches = false;
                }
            } else if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/strcmp0".equals(mb)) {
                matches = true;
                if (!pt.getScopes().getValue().isEmpty()) {
                    for (String s : pt.getScopes().getValue()) {
                        if (!ht.getScopes().getValue().contains(s)) {
                            matches = false;
                        }
                    }
                }
            } else if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/none".equals(mb)
                && (ht.getScopes() == null || ht.getScopes().getValue().isEmpty())) {
                matches = true;
            }
            if (!matches) {
                cit.remove();
            }
        }
    }
    

    @WebServiceProvider(wsdlLocation = "classpath:/org/apache/cxf/ws/discovery/wsdl/wsdd-discovery-1.1-wsdl-os.wsdl",
        targetNamespace = "http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01",
        serviceName = "Discovery",
        portName = "DiscoveryUDP")
    @XmlSeeAlso(ObjectFactory.class)
    @Addressing(required = true)
    class WSDiscoveryProvider implements Provider<Source> {
        
        JAXBContext context;
        public WSDiscoveryProvider() {
            try {
                context = JAXBContextCache.getCachedContextAndSchemas(ObjectFactory.class).getContext();
            } catch (JAXBException e) {
                e.printStackTrace();
            }
        }
        

        public Source invoke(Source request) {
            try {
                //Bug in JAXB - if you pass the StaxSource or SaxSource into unmarshall,
                //the namespaces for the QNames for the Types elements are lost.
                //Since WS-Discovery messages are small (UDP datagram size), parsing to DOM
                //is not a HUGE deal
                Document doc = StaxUtils.read(request);
                boolean mapToOld = false;
                if ("http://schemas.xmlsoap.org/ws/2005/04/discovery"
                    .equals(doc.getDocumentElement().getNamespaceURI())) {
                    //old version of ws-discovery, we'll transform this to newer version
                    
                    XMLStreamReader domReader = StaxUtils.createXMLStreamReader(doc);
                    Map<String, String> inMap = new HashMap<String, String>();
                    inMap.put("{http://schemas.xmlsoap.org/ws/2005/04/discovery}*",
                              "{http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01}*");
                    InTransformReader reader = new InTransformReader(domReader, inMap , null, false);
                    doc = StaxUtils.read(reader);
                    mapToOld = true;
                }
                
                
                if (!"http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01"
                    .equals(doc.getDocumentElement().getNamespaceURI())) {
                    //not a proper ws-discovery message, ignore it
                    return null;
                }
                
                Object obj = context.createUnmarshaller().unmarshal(doc.getDocumentElement());
                if (obj instanceof JAXBElement) {
                    obj = ((JAXBElement)obj).getValue();
                }
                if (obj instanceof ProbeType) {
                    ProbeMatchesType pmt = handleProbe((ProbeType)obj);
                    if (pmt == null) {
                        return null;
                    }
                    if (mapToOld) {
                        doc.removeChild(doc.getDocumentElement());
                        DOMResult result = new DOMResult(doc);
                        XMLStreamWriter r = StaxUtils.createXMLStreamWriter(result);
                        context.createMarshaller().marshal(factory.createProbeMatches(pmt), r);
                        
                        XMLStreamReader domReader = StaxUtils.createXMLStreamReader(doc);
                        Map<String, String> inMap = new HashMap<String, String>();
                        inMap.put("{http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01}*",
                                  "{http://schemas.xmlsoap.org/ws/2005/04/discovery}*");
                        InTransformReader reader = new InTransformReader(domReader, inMap , null, false);
                        doc = StaxUtils.read(reader);
                        return new DOMSource(doc);
                    }
                    return new JAXBSource(context, factory.createProbeMatches(pmt));
                } else if (obj instanceof HelloType) {
                    //check if it's a DiscoveryProxy
                    HelloType h = (HelloType)obj;
                    if (h.getTypes().contains(WSDiscoveryClient.SERVICE_QNAME)
                        || h.getTypes().contains(new QName("", WSDiscoveryClient.SERVICE_QNAME.getLocalPart()))) {
                        // A DiscoveryProxy wants us to flip to managed mode
                        try {
                            client.close();
                            client = new WSDiscoveryClient(h.getXAddrs().get(0));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (obj instanceof ByeType) {
                    ByeType h = (ByeType)obj;
                    if (h.getTypes().contains(WSDiscoveryClient.SERVICE_QNAME)
                        || h.getTypes().contains(new QName("", WSDiscoveryClient.SERVICE_QNAME.getLocalPart()))) {
                        // Our proxy has gone away, flip to ad-hoc
                        try {
                            if (client.getAddress().equals(h.getXAddrs().get(0))) {
                                client.close();
                                client = new WSDiscoveryClient();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (JAXBException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (XMLStreamException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            return null;
        }
    }

}
