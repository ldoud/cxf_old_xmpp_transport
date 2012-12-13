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
package org.apache.cxf.binding.soap.saaj;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPPart;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;

public final class SAAJStreamWriter extends W3CDOMStreamWriter {
    private final SOAPPart part;

    public SAAJStreamWriter(SOAPPart part) {
        super(part);
        this.part = part;
    }
    public SAAJStreamWriter(SOAPPart part, Element current) {
        super(part, current);
        this.part = part;
    }

    private SOAPElement adjustPrefix(SOAPElement e, String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        try {
            String s = e.getPrefix();
            if (!prefix.equals(s)) {
                e.setPrefix(prefix);
                e.removeNamespaceDeclaration(s);
            }
        } catch (Throwable t) {
            //likely old old version of SAAJ, we'll just try our best
        }
        return e;
    }
    
    protected void createAndAddElement(String prefix, String local, String namespace) {
        try {
            if (namespace != null 
                && namespace.equals(part.getEnvelope().getElementName().getURI())) {
                if ("Envelope".equals(local)) {
                    setChild(adjustPrefix(part.getEnvelope(), prefix), false);
                    adjustPrefix(part.getEnvelope().getHeader(), prefix);
                    return;
                } else if ("Body".equals(local)) {
                    setChild(adjustPrefix(part.getEnvelope().getBody(), prefix), false);
                    return;
                } else if ("Header".equals(local)) {
                    setChild(adjustPrefix(part.getEnvelope().getHeader(), prefix), false);
                    return;
                } else if ("Fault".equals(local)) {
                    SOAPFault f = part.getEnvelope().getBody().getFault();
                    if (f == null) {
                        Element el = part.createElementNS(namespace, 
                                             StringUtils.isEmpty(prefix) ? local : prefix + ":" + local);
                        part.getEnvelope().getBody().appendChild(el);
                        f = part.getEnvelope().getBody().getFault();
                        if (f == null) {
                            f = part.getEnvelope().getBody().addFault();
                        }
                    }
                    setChild(adjustPrefix(f, prefix), false);
                    return;
                }
            } else if (getCurrentNode() instanceof SOAPFault) {
                SOAPFault f = (SOAPFault)getCurrentNode();
                Node nd = f.getFirstChild();
                while (nd != null) {
                    if (nd instanceof Element) {
                        Element el = (Element)nd;
                        if (local.equals(nd.getLocalName())) {
                            setChild(el, false);
                            nd = el.getFirstChild();
                            while (nd != null) {
                                el.removeChild(nd);
                                nd = el.getFirstChild();
                            }
                            return;
                        }
                    }
                    nd = nd.getNextSibling();
                }
            }
        } catch (SOAPException e) {
            //ignore, fallback
        }
        super.createAndAddElement(prefix, local, namespace);
    }
}
