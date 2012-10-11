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

package org.apache.cxf.transport.xmpp.pubsub;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.xmpp.connection.XMPPConnectionFactory;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.pubsub.Affiliation;
import org.jivesoftware.smackx.pubsub.ConfigureForm;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.Node;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.listener.ItemEventListener;

public class PubSubServiceNode extends AbstractFeature {

    private static final Logger LOGGER = LogUtils.getLogger(PubSubServiceNode.class);

    private XMPPConnectionFactory connectionFactory;

    public void setConnectionFactory(XMPPConnectionFactory factory) {
        connectionFactory = factory;
    }

    @Override
    public void initialize(Bus bus) {
        // Doesn't work on a bus
        LOGGER.log(Level.WARNING, "This feature isn't for a bus");
    }

    @Override
    public void initialize(Server server, Bus bus) {
        ProviderManager mgr = ProviderManager.getInstance();
        if (mgr.getIQProvider(NodeNotificationPacket.ROOT_ELEMENT, NodeNotificationPacket.NAMESPACE) == null) {
            mgr.addIQProvider(NodeNotificationPacket.ROOT_ELEMENT, NodeNotificationPacket.NAMESPACE, new NodeNotificationProvider());            
        }

        // The node name is the full name of the service.        
        final String serviceName = server.getEndpoint().getEndpointInfo().getService().getName().toString();
        
        Destination dest = server.getDestination();
        try {
            if (dest instanceof ItemEventListener<?>) {
                final ItemEventListener<?> listener = (ItemEventListener<?>)dest;
                final XMPPConnection connection = connectionFactory.login(server.getEndpoint().getEndpointInfo());
                
                // Listen for notification of new nodes.
                connection.addPacketListener(new PacketListener() {                    
                    @Override
                    public void processPacket(Packet p) {
                        LOGGER.info("Received node notification packet: "+p.toXML());
                        NodeNotificationPacket notification = (NodeNotificationPacket)p;
                        if (serviceName.equals(notification.getServiceName())) {
                            subscribeToNode(notification.getNodeName(), listener, connection);
                        }                        
                    }
                }, new PacketFilter() {
                    @Override
                    public boolean accept(Packet p) {
                        return p instanceof NodeNotificationPacket;
                    }
                });
              
            } else {
                LOGGER.log(Level.WARNING, "This feature is only for PubSubDestinations");
            }
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Failed to create listener for service: " + serviceName, e);
        }
    }

    @Override
    public void initialize(Client client, Bus bus) {                
        String serviceName = client.getEndpoint().getEndpointInfo().getInterface().getName().toString();
        
        Conduit conduit = client.getConduit();
        try {
            if (conduit instanceof PubSubClientConduit) {

                final XMPPConnection connection = connectionFactory.login(client.getEndpoint().getEndpointInfo());
                
                // Create a node name based on the service name.
                String fullJID = connection.getUser();
                String bareJID = fullJID.substring(0, fullJID.indexOf("/"));
                String nodeName = bareJID + "/" + serviceName;
                LOGGER.log(Level.INFO, "Node name for client conduit: " + serviceName);
                Node pubSubNode = findOrCreateNode(nodeName, connection);

                if (pubSubNode instanceof LeafNode) {
                    ((PubSubClientConduit)conduit).setNode((LeafNode)pubSubNode);
                    
                    final NodeNotificationPacket packet = new NodeNotificationPacket();
                    packet.setFrom(connection.getUser());
                    packet.setNodeName(nodeName);
                    packet.setServiceName(serviceName);
                    packet.setType(Type.GET);
                    
                    connection.getRoster().addRosterListener(new RosterListener() {
                        
                        @Override
                        public void presenceChanged(Presence p) {
                            if (p.isAvailable()) {
                                packet.setTo(p.getFrom());
                                connection.sendPacket(packet);
                                LOGGER.log(Level.INFO, "Sent node notification to presence: " + p.getFrom());
                            }
                        }
                        
                        @Override
                        public void entriesUpdated(Collection<String> arg0) {
                            // Nothing
                        }
                        
                        @Override
                        public void entriesDeleted(Collection<String> arg0) {
                            // Nothing
                        }
                        
                        @Override
                        public void entriesAdded(Collection<String> arg0) {
                            // Nothing
                        }
                    });
                    
                    Roster roster = connection.getRoster();
                    Collection<RosterEntry> entries = roster.getEntries();
                    for(RosterEntry entry : entries) {
                        packet.setTo(entry.getUser());
                        connection.sendPacket(packet);
                        LOGGER.log(Level.INFO, "Sent node notification to roster entry: " + entry.getUser());
                    }
                    
                } else {
                    LOGGER.log(Level.SEVERE, "Node cannot be used to published items");
                }
            } else {
                LOGGER.log(Level.WARNING, "This feature is only for PubSubDestinations");
            }
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Failed to create node for service: " + serviceName, e);
        }
    }
    
    private Node findOrCreateNode(String serviceName, XMPPConnection connection) {
        PubSubManager mgr = new PubSubManager(connection);
        Node pubSubNode = null;
        try {            
            pubSubNode = mgr.getNode(serviceName);
        } catch (XMPPException failedToFindNode) {
            try {
                String fieldName = "pubsub#send_item_subscribe";
                FormField lastValueQueue = new FormField(fieldName);
                lastValueQueue.setType(FormField.TYPE_BOOLEAN);
                                                  
                Form createForm = new Form(Form.TYPE_SUBMIT);
                createForm.addField(lastValueQueue);
                
                boolean answer = false;
                createForm.setAnswer(fieldName, answer);
                
                pubSubNode = mgr.createNode(serviceName, new ConfigureForm(createForm));
            } catch (XMPPException failedToCreateNode) {
                LOGGER.log(Level.SEVERE, "Failed to create node: " + serviceName, failedToCreateNode);
            }
        }

        return pubSubNode;
    }

        
    private void subscribeToNode(String nodeName, ItemEventListener<?> listener, XMPPConnection connection) {
        PubSubManager mgr = new PubSubManager(connection);
        try {
            boolean alreadySubscribed = false;
            try {
                List<Affiliation> affiliations = mgr.getAffiliations();
                for(Affiliation aff : affiliations) {
                    
                    if (nodeName.equals(aff.getNodeId())) {
                        alreadySubscribed = true;
                    }
                }
            } catch (XMPPException e) {
                // No affiliations causes an exception. :(
                // An empty list would have been nicer.
            }
            
            if (!alreadySubscribed) {                
                LeafNode node = (LeafNode)mgr.getNode(nodeName);
                node.addItemEventListener(listener);
                node.subscribe(connection.getUser());
                LOGGER.info("Subscribed to: "+nodeName+" as user: "+connection.getUser());
            }
            
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Unable to subscribe to node", e);
        }
    }
}
