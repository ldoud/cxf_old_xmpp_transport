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

import java.util.Iterator;
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
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.pubsub.ConfigureForm;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.Node;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.Subscription;
import org.jivesoftware.smackx.pubsub.listener.ItemEventListener;

public class PubSubFixedNameNode extends AbstractFeature {

    private static final Logger LOGGER = LogUtils.getLogger(PubSubFixedNameNode.class);

    private boolean createIfMissing = true;
    private String nodeName;
    private XMPPConnectionFactory connectionFactory;

    public void setCreateIfMissing(boolean create) {
        createIfMissing = create;
    }

    public void setConnectionFactory(XMPPConnectionFactory factory) {
        connectionFactory = factory;
    }

    public void setNodeName(String name) {
        nodeName = name;
    }

    @Override
    public void initialize(Bus bus) {
        // Doesn't work on a bus
        LOGGER.log(Level.WARNING, "This feature isn't for a bus");
    }

    @Override
    public void initialize(Server server, Bus bus) {
        Destination dest = server.getDestination();
        try {
            if (dest instanceof ItemEventListener<?>) {
                LOGGER.log(Level.INFO, "Node name for server destination: " + nodeName);

                XMPPConnection connection = connectionFactory.login(server.getEndpoint().getEndpointInfo());
                Node pubSubNode = findOrCreateNode(nodeName, connection);

                if (pubSubNode != null) {
                    findOrCreateSubscription((ItemEventListener<?>)dest, connection, pubSubNode);
                    
//                    String fieldName = "pubsub#send_item_subscribe";
//                    ConfigureForm form = pubSubNode.getNodeConfiguration();
//                    LOGGER.info(form.toString());
//                    LOGGER.info(fieldName+" "+form.getField(fieldName).getValues().next());
                }
            } else {
                LOGGER.log(Level.WARNING, "This feature is only for PubSubDestinations");
            }
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Failed to create node: " + nodeName, e);
        }
    }

    @Override
    public void initialize(Client client, Bus bus) {
        Conduit conduit = client.getConduit();
        try {
            if (conduit instanceof PubSubClientConduit) {
                LOGGER.log(Level.INFO, "Node name for client conduit: " + nodeName);

                XMPPConnection connection = connectionFactory.login(client.getEndpoint().getEndpointInfo());
                Node pubSubNode = findOrCreateNode(nodeName, connection);

                if (pubSubNode instanceof LeafNode) {
                    ((PubSubClientConduit)conduit).setNode((LeafNode)pubSubNode);
                } else {
                    LOGGER.log(Level.SEVERE, "Node cannot be used to published items");
                }
            } else {
                LOGGER.log(Level.WARNING, "This feature is only for PubSubDestinations");
            }
        } catch (XMPPException e) {
            LOGGER.log(Level.SEVERE, "Failed to create node: " + nodeName, e);
        }
    }

    private void findOrCreateSubscription(ItemEventListener<?> listener, XMPPConnection connection,
                                          Node pubSubNode) {
        try {
            String userName = connection.getUser();
            List<Subscription> subscriptions = pubSubNode.getSubscriptions();
            boolean alreadySubscribed = false;
            for (Iterator<Subscription> i = subscriptions.iterator(); i.hasNext() && !alreadySubscribed;) {
                Subscription sub = i.next();
                alreadySubscribed = sub.getJid().equals(userName);
            }

            pubSubNode.addItemEventListener(listener);

            if (!alreadySubscribed) {
                try {
                    LOGGER.log(Level.INFO, "Creating subscription for destination");
                    pubSubNode.subscribe(userName);
                } catch (XMPPException failedToSub) {
                    LOGGER.log(Level.SEVERE, "JID: " + userName + " to node: " + pubSubNode.getId());
                }
            } else {
                LOGGER.log(Level.INFO, "Found existing subscription for destination");
            }

        } catch (XMPPException failedToGetSubscriptions) {
            LOGGER.log(Level.SEVERE, "Failed to find subscriptions for node: " + pubSubNode.getId());
        }
    }

    private Node findOrCreateNode(String serviceName, XMPPConnection connection) {
        PubSubManager mgr = new PubSubManager(connection);
        Node pubSubNode = null;
        try {            
            pubSubNode = mgr.getNode(serviceName);
        } catch (XMPPException failedToFindNode) {
            if (createIfMissing) {
                try {
//                    String fieldName = "pubsub#send_last_published_item";
                    String fieldName = "pubsub#send_item_subscribe";
                    FormField lastValueQueue = new FormField(fieldName);
//                    lastValueQueue.setType(FormField.TYPE_LIST_SINGLE);
                    lastValueQueue.setType(FormField.TYPE_BOOLEAN);
                                                      
                    Form createForm = new Form(Form.TYPE_SUBMIT);
                    createForm.addField(lastValueQueue);
                    
//                    List<String> answer = new ArrayList<String>(1);
//                    answer.add("never");
                    boolean answer = false;
                    createForm.setAnswer(fieldName, answer);
                    
                    pubSubNode = mgr.createNode(serviceName, new ConfigureForm(createForm));
                } catch (XMPPException failedToCreateNode) {
                    LOGGER.log(Level.SEVERE, "Failed to create node: " + serviceName, failedToCreateNode);
                }
            } else {
                LOGGER.log(Level.WARNING, "Not creating node that wasn't found: " + serviceName);
            }
        }
        return pubSubNode;
    }

}
