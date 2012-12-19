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

package org.apache.cxf.binding.soap.interceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPPart;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.Soap12;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.Soap11FaultOutInterceptor.Soap11FaultOutInterceptorInternal;
import org.apache.cxf.binding.soap.interceptor.Soap12FaultOutInterceptor.Soap12FaultOutInterceptorInternal;
import org.apache.cxf.binding.soap.saaj.SAAJInInterceptor;
import org.apache.cxf.binding.soap.saaj.SAAJInInterceptor.SAAJPreInInterceptor;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.test.AbstractCXFTest;
import org.junit.Test;

public class SoapFaultSerializerTest extends AbstractCXFTest {
    
    @Test
    public void testSoap11Out() throws Exception {
        String faultString = "Hadrian caused this Fault!";
        SoapFault fault = new SoapFault(faultString, Soap11.getInstance().getSender());

        SoapMessage m = new SoapMessage(new MessageImpl());
        m.setExchange(new ExchangeImpl());
        m.setContent(Exception.class, fault);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLStreamWriter writer = StaxUtils.createXMLStreamWriter(out);
        writer.writeStartDocument();
        writer.writeStartElement("Body");

        m.setContent(XMLStreamWriter.class, writer);

        Soap11FaultOutInterceptorInternal.INSTANCE.handleMessage(m);

        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        Document faultDoc = DOMUtils.readXml(new ByteArrayInputStream(out.toByteArray()));
        assertValid("//s:Fault/faultcode[text()='ns1:Client']", faultDoc);
        assertValid("//s:Fault/faultstring[text()='" + faultString + "']", faultDoc);

        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new ByteArrayInputStream(out.toByteArray()));
        m.setContent(XMLStreamReader.class, reader);

        reader.nextTag();

        Soap11FaultInInterceptor inInterceptor = new Soap11FaultInInterceptor();
        inInterceptor.handleMessage(m);

        SoapFault fault2 = (SoapFault)m.getContent(Exception.class);
        assertNotNull(fault2);
        assertEquals(fault.getMessage(), fault2.getMessage());
        assertEquals(Soap11.getInstance().getSender(), fault2.getFaultCode());
    }
    
    @Test
    public void testSoap12Out() throws Exception {
        String faultString = "Hadrian caused this Fault!";
        SoapFault fault = new SoapFault(faultString, Soap12.getInstance().getSender());
        
        QName qname = new QName("http://cxf.apache.org/soap/fault", "invalidsoap", "cxffaultcode");
        fault.setSubCode(qname);

        SoapMessage m = new SoapMessage(new MessageImpl());
        m.setVersion(Soap12.getInstance());
        
        m.setContent(Exception.class, fault);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLStreamWriter writer = StaxUtils.createXMLStreamWriter(out);
        writer.writeStartDocument();
        writer.writeStartElement("Body");

        m.setContent(XMLStreamWriter.class, writer);

        Soap12FaultOutInterceptorInternal.INSTANCE.handleMessage(m);

        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        Document faultDoc = DOMUtils.readXml(new ByteArrayInputStream(out.toByteArray()));
        
        assertValid("//soap12env:Fault/soap12env:Code/soap12env:Value[text()='ns1:Sender']", 
                    faultDoc);
        assertValid("//soap12env:Fault/soap12env:Code/soap12env:Subcode/"
                    + "soap12env:Value[text()='ns2:invalidsoap']", 
                    faultDoc);
        assertValid("//soap12env:Fault/soap12env:Reason/soap12env:Text[@xml:lang='en']", 
                    faultDoc);
        assertValid("//soap12env:Fault/soap12env:Reason/soap12env:Text[text()='" + faultString + "']", 
                    faultDoc);

        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new ByteArrayInputStream(out.toByteArray()));
        m.setContent(XMLStreamReader.class, reader);

        reader.nextTag();

        Soap12FaultInInterceptor inInterceptor = new Soap12FaultInInterceptor();
        inInterceptor.handleMessage(m);

        SoapFault fault2 = (SoapFault)m.getContent(Exception.class);
        assertNotNull(fault2);
        assertEquals(Soap12.getInstance().getSender(), fault2.getFaultCode());
        assertEquals(fault.getMessage(), fault2.getMessage());        
    }
    
    @Test
    public void testFaultToSoapFault() throws Exception {
        Exception ex = new Exception();
        Fault fault = new Fault(ex, Fault.FAULT_CODE_CLIENT);
        verifyFaultToSoapFault(fault, ex.toString(), true, Soap11.getInstance());
        verifyFaultToSoapFault(fault, ex.toString(), true, Soap12.getInstance());
        
        fault = new Fault(ex, Fault.FAULT_CODE_SERVER);
        verifyFaultToSoapFault(fault, ex.toString(), false, Soap11.getInstance());
        verifyFaultToSoapFault(fault, ex.toString(), false, Soap12.getInstance());
        
        fault.setMessage("fault-one");
        verifyFaultToSoapFault(fault, "fault-one", false, Soap11.getInstance());

        ex = new Exception("fault-two");
        fault = new Fault(ex, Fault.FAULT_CODE_CLIENT);
        verifyFaultToSoapFault(fault, "fault-two", true, Soap11.getInstance());
    }
    
    private void verifyFaultToSoapFault(Fault fault, String msg, boolean sender, SoapVersion v) {
        SoapFault sf = SoapFault.createFault(fault, v);
        assertEquals(sender ? v.getSender() : v.getReceiver(), sf.getFaultCode());
        assertEquals(msg, sf.getMessage());
    }

    @Test
    public void testCXF1864() throws Exception {

        SoapMessage m = new SoapMessage(new MessageImpl());
        m.setVersion(Soap12.getInstance());        


        XMLStreamReader reader = StaxUtils.createXMLStreamReader(this.getClass()
                                                                 .getResourceAsStream("cxf1864.xml"));
        m.setContent(XMLStreamReader.class, reader);

        reader.nextTag();

        Soap12FaultInInterceptor inInterceptor = new Soap12FaultInInterceptor();
        inInterceptor.handleMessage(m);

        SoapFault fault2 = (SoapFault)m.getContent(Exception.class);
        assertNotNull(fault2);
        assertEquals(Soap12.getInstance().getReceiver(), fault2.getFaultCode());
    }

    @Test
    public void testCXF4181() throws Exception {
        //Try WITH SAAJ
        SoapMessage m = new SoapMessage(new MessageImpl());
        m.setVersion(Soap12.getInstance());        
        XMLStreamReader reader = StaxUtils.createXMLStreamReader(this.getClass()
                                                                 .getResourceAsStream("cxf4181.xml"));

        m.setContent(XMLStreamReader.class, reader);

        new SAAJPreInInterceptor().handleMessage(m);
        new ReadHeadersInterceptor(null).handleMessage(m);
        new StartBodyInterceptor().handleMessage(m);
        new SAAJInInterceptor().handleMessage(m);
        new Soap12FaultInInterceptor().handleMessage(m);

        Node nd = m.getContent(Node.class);
        
        SOAPPart part = (SOAPPart)nd;
        assertEquals("S", part.getEnvelope().getPrefix());
        assertEquals("S2", part.getEnvelope().getHeader().getPrefix());
        assertEquals("S3", part.getEnvelope().getBody().getPrefix());
        SOAPFault fault = part.getEnvelope().getBody().getFault();
        assertEquals("S", fault.getPrefix());
        
        assertEquals("Authentication Failure", fault.getFaultString());
        
        SoapFault fault2 = (SoapFault)m.getContent(Exception.class);
        assertNotNull(fault2);
        
        assertEquals(Soap12.getInstance().getSender(), fault2.getFaultCode());
        assertEquals(new QName("http://schemas.xmlsoap.org/ws/2005/02/trust", "FailedAuthentication"), 
                     fault2.getSubCode());
        
        Element el = part.getEnvelope().getBody();
        nd = el.getFirstChild();
        int count = 0;
        while (nd != null) {
            if (nd instanceof Element) {
                count++;
            }
            nd = nd.getNextSibling();
        }
        assertEquals(1, count);
        
        
        //Try WITHOUT SAAJ
        m = new SoapMessage(new MessageImpl());
        m.setVersion(Soap12.getInstance());  
        reader = StaxUtils.createXMLStreamReader(this.getClass()
                                                 .getResourceAsStream("cxf4181.xml"));

        m.setContent(XMLStreamReader.class, reader);

        new ReadHeadersInterceptor(null).handleMessage(m);
        new StartBodyInterceptor().handleMessage(m);
        new Soap12FaultInInterceptor().handleMessage(m);

        nd = m.getContent(Node.class);

        fault2 = (SoapFault)m.getContent(Exception.class);
        assertNotNull(fault2);

        assertEquals(Soap12.getInstance().getSender(), fault2.getFaultCode());
        assertEquals(new QName("http://schemas.xmlsoap.org/ws/2005/02/trust", "FailedAuthentication"), 
             fault2.getSubCode());
    }
}
