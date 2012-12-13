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

package org.apache.cxf.systest.jaxrs;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.model.AbstractResourceInfo;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;

import org.junit.BeforeClass;
import org.junit.Test;

public class JAXRSClientServerProxySpringBookTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookServerProxySpring.PORT;

    @BeforeClass
    public static void startServers() throws Exception {
        AbstractResourceInfo.clearAllMaps();
        assertTrue("server did not launch correctly", 
                   launchServer(BookServerProxySpring.class, true));
        createStaticBus();
    }
    
    @Test
    public void testGetBookNotFound() throws Exception {
        
        String endpointAddress =
            "http://localhost:" + PORT + "/test/bookstore/books/12345"; 
        URL url = new URL(endpointAddress);
        HttpURLConnection connect = (HttpURLConnection)url.openConnection();
        connect.addRequestProperty("Accept", "text/plain,application/xml");
        assertEquals(500, connect.getResponseCode());
        InputStream in = connect.getErrorStream();
        assertNotNull(in);           

        InputStream expected = getClass()
            .getResourceAsStream("resources/expected_get_book_notfound_mapped.txt");

        assertEquals("Exception is not mapped correctly", 
                     getStringFromInputStream(expected).trim(),
                     getStringFromInputStream(in).trim());
    }
    
    @Test
    public void testGetThatBook123() throws Exception {
        getBook("http://localhost:" + PORT + "/test/bookstorestorage/thosebooks/123");
    }
    
    @Test
    public void testGetThatBookSingleton() throws Exception {
        getBook("http://localhost:" + PORT + "/test/4/bookstore/books/123");
        getBook("http://localhost:" + PORT + "/test/4/bookstore/books/123");
    }
    
    @Test
    public void testGetThatBookInterfaceSingleton() throws Exception {
        getBook("http://localhost:" + PORT + "/test/4/bookstorestorage/thosebooks/123");
    }
    
    @Test
    public void testGetThatBookPrototype() throws Exception {
        getBook("http://localhost:" + PORT + "/test/5/bookstore/books/123");
    }
    
    @Test
    public void testGetThatBookInterfacePrototype() throws Exception {
        getBook("http://localhost:" + PORT + "/test/5/bookstorestorage/thosebooks/123");
    }
    
    @Test
    public void testGetThatBookInterface2Prototype() throws Exception {
        getBook("http://localhost:" + PORT + "/test/6/bookstorestorage/thosebooks/123");
    }
    
    @Test
    public void testGetThatBook123UserResource() throws Exception {
        getBook("http://localhost:" + PORT + "/test/2/bookstore/books/123");
    }
    
    @Test
    public void testGetThatBook123UserResourceInterface() throws Exception {
        getBook("http://localhost:" + PORT + "/test/3/bookstore2/books/123");
    }
    
    private void getBook(String endpointAddress) throws Exception {
        URL url = new URL(endpointAddress);
        URLConnection connect = url.openConnection();
        connect.addRequestProperty("Content-Type", "*/*");
        connect.addRequestProperty("Accept", "application/xml");
        connect.addRequestProperty("SpringProxy", "true");
        InputStream in = connect.getInputStream();           

        InputStream expected = getClass()
            .getResourceAsStream("resources/expected_get_book123.txt");
        assertEquals(getStringFromInputStream(expected), getStringFromInputStream(in));
    }
    
    @Test
    public void testGetThatBookOverloaded() throws Exception {
        getBook("http://localhost:" + PORT + "/test/bookstorestorage/thosebooks/123/123");
    }
    
    @Test
    public void testGetThatBookOverloaded2() throws Exception {
        getBook("http://localhost:" + PORT + "/test/bookstorestorage/thosebooks");
    }
    
    @Test
    public void testGetBook123() throws Exception {
        String endpointAddress =
            "http://localhost:" + PORT + "/test/bookstore/books/123"; 
        URL url = new URL(endpointAddress);
        URLConnection connect = url.openConnection();
        connect.addRequestProperty("Accept", "application/json");
        InputStream in = connect.getInputStream();           

        InputStream expected = getClass()
            .getResourceAsStream("resources/expected_get_book123json.txt");

        //System.out.println("---" + getStringFromInputStream(in));
        assertEquals(getStringFromInputStream(expected), getStringFromInputStream(in)); 
    }

    @Test
    public void testGetBookWithRequestScope() {
        // the BookStore method which will handle this request depends on the injected HttpHeaders
        WebClient wc = WebClient.create("http://localhost:" + PORT + "/test/request/bookstore/booksecho2");
        wc.type("text/plain").accept("text/plain");
        wc.header("CustomHeader", "custom-header");
        String value = wc.post("CXF", String.class);
        assertEquals("CXF", value);
        assertEquals("custom-header", wc.getResponse().getMetadata().getFirst("CustomHeader"));
    }
    
    private String getStringFromInputStream(InputStream in) throws Exception {        
        CachedOutputStream bos = new CachedOutputStream();
        IOUtils.copy(in, bos);
        String str = new String(bos.getBytes()); 
        in.close();
        bos.close();
        return str;
    }

}
