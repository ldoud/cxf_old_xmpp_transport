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

package org.apache.cxf.jaxws.support;

import java.lang.reflect.Method;
import java.net.URL;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.cxf.Bus;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.binding.BindingFactoryManagerImpl;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.invoker.BeanInvoker;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.WSDLManagerImpl;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.easymock.IMocksControl;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createNiceControl;

public class JaxWsServiceFactoryBeanTest extends TestCase {
    public void testEndpoint() throws Exception {
        JaxWsServiceFactoryBean bean = new JaxWsServiceFactoryBean();

        URL resource = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(resource);
        bean.setWsdlURL(resource);
        Bus bus = createBus();
        bean.setBus(bus);
        bean.setServiceClass(GreeterImpl.class);

        BeanInvoker invoker = new BeanInvoker(new GreeterImpl());
        bean.setInvoker(invoker);
        
        Service service = bean.create();

        assertEquals("SOAPService", service.getName().getLocalPart());
        assertEquals("http://apache.org/hello_world_soap_http", service.getName().getNamespaceURI());
        
        InterfaceInfo intf = service.getServiceInfo().getInterface();
        
        OperationInfo op = intf.getOperation(
            new QName("http://apache.org/hello_world_soap_http", "sayHi"));
        
        Method m = (Method) op.getProperty(Method.class.getName());
        assertNotNull(m);
        assertEquals("sayHi", m.getName());
        
        assertEquals(invoker, service.getInvoker());
        
    }

    Bus createBus() throws Exception {
        IMocksControl control = createNiceControl();
        Bus bus = control.createMock(Bus.class);

        SoapBindingFactory bindingFactory = new SoapBindingFactory();
        BindingFactoryManager bfm = new BindingFactoryManagerImpl();
        bfm.registerBindingFactory("http://schemas.xmlsoap.org/wsdl/soap/", bindingFactory);

        expect(bus.getExtension(BindingFactoryManager.class)).andReturn(bfm).anyTimes();

        WSDLManagerImpl wsdlMan = new WSDLManagerImpl();
        expect(bus.getExtension(WSDLManager.class)).andReturn(wsdlMan);

        control.replay();

        return bus;
    }
}
