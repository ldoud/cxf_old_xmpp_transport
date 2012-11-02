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
package org.apache.cxf.jaxrs.ext.search.jpa;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.metamodel.SingularAttribute;

import org.apache.cxf.jaxrs.ext.search.SearchCondition;
import org.apache.cxf.jaxrs.ext.search.SearchConditionVisitor;
import org.apache.cxf.jaxrs.ext.search.SearchUtils;
import org.apache.cxf.jaxrs.ext.search.fiql.FiqlParser;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JPATypedQueryVisitorTest extends Assert {

    private EntityManagerFactory emFactory;

    private EntityManager em;

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
            connection = DriverManager.getConnection("jdbc:hsqldb:mem:books-jpa", "sa", "");
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Exception during HSQL database init.");
        }
        try {
            emFactory = Persistence.createEntityManagerFactory("testUnitHibernate");
            em = emFactory.createEntityManager();
         
            em.getTransaction().begin();
            
            Library lib = new Library();
            lib.setId(1);
            lib.setAddress("town");
            em.persist(lib);
            assertTrue(em.contains(lib));
                        
            Book b1 = new Book();
            b1.setId(9);
            b1.setTitle("num9");
            b1.setAddress(new OwnerAddress("Street1"));
            OwnerInfo info1 = new OwnerInfo();
            info1.setName(new Name("Fred"));
            info1.setDateOfBirth(parseDate("2000-01-01"));
            b1.setOwnerInfo(info1);
            b1.setLibrary(lib);
            em.persist(b1);
            assertTrue(em.contains(b1));
            Book b2 = new Book();
            b2.setId(10);
            b2.setTitle("num10");
            b2.setAddress(new OwnerAddress("Street2"));
            OwnerInfo info2 = new OwnerInfo();
            info2.setName(new Name("Barry"));
            info2.setDateOfBirth(parseDate("2001-01-01"));
            b2.setOwnerInfo(info2);
            b2.setLibrary(lib);
            em.persist(b2);
            assertTrue(em.contains(b2));
            Book b3 = new Book();
            b3.setId(11);
            b3.setTitle("num11");
            b3.setAddress(new OwnerAddress("Street3"));
            OwnerInfo info3 = new OwnerInfo();
            info3.setName(new Name("Bill"));
            info3.setDateOfBirth(parseDate("2002-01-01"));
            b3.setOwnerInfo(info3);
            b3.setLibrary(lib);
            em.persist(b3);
            assertTrue(em.contains(b3));
            
            em.getTransaction().commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Exception during JPA EntityManager creation.");
        }
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (em != null) {
                em.close();
            }
            if (emFactory != null) {
                emFactory.close();
            }
        } catch (Throwable ex) {
            ex.printStackTrace();    
        } finally {    
            try {
                connection.createStatement().execute("SHUTDOWN");
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }
    
    @Test
    public void testOrQuery() throws Exception {
        List<Book> books = queryBooks("id=lt=10,id=gt=10");
        assertEquals(2, books.size());
        assertTrue(9 == books.get(0).getId() && 11 == books.get(1).getId()
            || 11 == books.get(0).getId() && 9 == books.get(1).getId());
    }
    
    @Test
    public void testOrQueryNoMatch() throws Exception {
        List<Book> books = queryBooks("id==7,id==5");
        assertEquals(0, books.size());
    }
    
    @Test
    public void testAndQuery() throws Exception {
        List<Book> books = queryBooks("id==10;title==num10");
        assertEquals(1, books.size());
        assertTrue(10 == books.get(0).getId() && "num10".equals(books.get(0).getTitle()));
    }
    
    @Test
    public void testAndQueryNoMatch() throws Exception {
        List<Book> books = queryBooks("id==10;title==num9");
        assertEquals(0, books.size());
    }
    
    @Test
    public void testEqualsQuery() throws Exception {
        List<Book> books = queryBooks("id==10");
        assertEquals(1, books.size());
        assertTrue(10 == books.get(0).getId());
    }
    
    @Test
    public void testEqualsCriteriaQueryTuple() throws Exception {
        List<Tuple> books = criteriaQueryBooks("id==10");
        assertEquals(1, books.size());
        Tuple tuple = books.get(0);
        int tupleId = tuple.get("id", Integer.class);
        assertEquals(10, tupleId);
    }
    
    @Test
    public void testEqualsAddressQuery() throws Exception {
        List<Book> books = queryBooks("address==Street1",
            Collections.singletonMap("address", "address.street"));
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertTrue(9 == book.getId());
        assertEquals("Street1", book.getAddress().getStreet());
    }
    
    @Test
    public void testIsMet() throws Exception {
        SearchCondition<Book> filter = 
            new FiqlParser<Book>(Book.class,
                null,                          
                Collections.singletonMap("address", "address.street")).parse("address==Street1");
        
        Book b = new Book();
        b.setAddress(new OwnerAddress("Street1"));
        assertTrue(filter.isMet(b));
        
        b.setAddress(new OwnerAddress("Street2"));
        assertFalse(filter.isMet(b));
    }
    
    @Test
    public void testEqualsAddressQuery2() throws Exception {
        List<Book> books = queryBooks("street==Street1",
            null,                          
            Collections.singletonMap("street", "address.street"));
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertTrue(9 == book.getId());
        assertEquals("Street1", book.getAddress().getStreet());
    }
    
    @Test
    public void testEqualsAddressQuery3() throws Exception {
        Map<String, String> beanPropertiesMap = new HashMap<String, String>();
        beanPropertiesMap.put("street", "address.street");
        beanPropertiesMap.put("housenum", "address.houseNumber");
        List<Book> books = 
            queryBooks("street==Street2;housenum=lt=5", null, beanPropertiesMap);
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertTrue(10 == book.getId());
        assertEquals("Street2", book.getAddress().getStreet());
        
    }

    @Test
    public void testEqualsOwnerNameQuery() throws Exception {
        List<Book> books = queryBooks("ownerInfo.name.name==Fred");
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Fred", book.getOwnerInfo().getName().getName());
    }
    
        
    @Test
    // "ownerInfo.name" maps to Name class and this 
    // does not work in OpenJPA, as opposed to Hibernate
    // "ownerInfo.name.name" will map to primitive type, see
    // testEqualsOwnerNameQuery3(), which also works in OpenJPA
    public void testEqualsOwnerNameQuery2() throws Exception {
        List<Book> books = queryBooks("ownerInfo.name==Fred");
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Fred", book.getOwnerInfo().getName().getName());
    }
    
    @Test
    public void testEqualsOwnerNameQuery3() throws Exception {
        List<Book> books = queryBooks("ownerName==Fred", null,
            Collections.singletonMap("ownerName", "ownerInfo.name.name"));
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Fred", book.getOwnerInfo().getName().getName());
    }
    
    @Test
    public void testFindBookInTownLibrary() throws Exception {
        List<Book> books = queryBooks("libAddress==town;title==num10", null,
            Collections.singletonMap("libAddress", "library.address"));
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Barry", book.getOwnerInfo().getName().getName());
    }
    
    @Test
    public void testEqualsOwnerBirthDate() throws Exception {
        List<Book> books = queryBooks("ownerbdate==2000-01-01", null,
            Collections.singletonMap("ownerbdate", "ownerInfo.dateOfBirth"));
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Fred", book.getOwnerInfo().getName().getName());
        
        Date d = parseDate("2000-01-01");
        
        assertEquals("Fred", book.getOwnerInfo().getName().getName());
        assertEquals(d, book.getOwnerInfo().getDateOfBirth());
    }
    
    private Date parseDate(String value) throws Exception {
        return new SimpleDateFormat(SearchUtils.DEFAULT_DATE_FORMAT).parse(value);
    }
    
    @Test
    public void testEqualsWildcard() throws Exception {
        List<Book> books = queryBooks("title==num1*");
        assertEquals(2, books.size());
        assertTrue(10 == books.get(0).getId() && 11 == books.get(1).getId()
            || 11 == books.get(0).getId() && 10 == books.get(1).getId());
    }
    
    @Test
    public void testGreaterQuery() throws Exception {
        List<Book> books = queryBooks("id=gt=10");
        assertEquals(1, books.size());
        assertTrue(11 == books.get(0).getId());
    }
    
    @Test
    public void testGreaterEqualQuery() throws Exception {
        List<Book> books = queryBooks("id=ge=10");
        assertEquals(2, books.size());
        assertTrue(10 == books.get(0).getId() && 11 == books.get(1).getId()
            || 11 == books.get(0).getId() && 10 == books.get(1).getId());
    }
    
    @Test
    public void testLessEqualQuery() throws Exception {
        List<Book> books = queryBooks("id=le=10");
        assertEquals(2, books.size());
        assertTrue(9 == books.get(0).getId() && 10 == books.get(1).getId()
            || 9 == books.get(0).getId() && 10 == books.get(1).getId());
    }
    
    @Test
    public void testNotEqualsQuery() throws Exception {
        List<Book> books = queryBooks("id!=10");
        assertEquals(2, books.size());
        assertTrue(9 == books.get(0).getId() && 11 == books.get(1).getId()
            || 11 == books.get(0).getId() && 9 == books.get(1).getId());
    }
    
    private List<Book> queryBooks(String expression) throws Exception {
        return queryBooks(expression, null);
    }
    
    private List<Book> queryBooks(String expression, 
                                  Map<String, String> visitorProps) throws Exception {
        return queryBooks(expression, visitorProps, null);
    }
    
    private List<Book> queryBooks(String expression, 
                                  Map<String, String> visitorProps,
                                  Map<String, String> parserBinProps) throws Exception {
        SearchCondition<Book> filter = 
            new FiqlParser<Book>(Book.class,
                                 visitorProps,
                                 parserBinProps).parse(expression);
        SearchConditionVisitor<Book, TypedQuery<Book>> jpa = 
            new JPATypedQueryVisitor<Book>(em, Book.class, visitorProps);
        filter.accept(jpa);
        TypedQuery<Book> query = jpa.getQuery();
        return query.getResultList();
    }
    
    private List<Tuple> criteriaQueryBooks(String expression) throws Exception {
        SearchCondition<Book> filter = 
            new FiqlParser<Book>(Book.class).parse(expression);
        JPACriteriaQueryVisitor<Book, Tuple> jpa = 
            new JPACriteriaQueryVisitor<Book, Tuple>(em, Book.class, Tuple.class);
        filter.accept(jpa);
        
        List<SingularAttribute<Book, ?>> selections = 
            new ArrayList<SingularAttribute<Book, ?>>();
        selections.add(Book_.id);
        
        jpa.selectTuple(selections);
        
        CriteriaQuery<Tuple> cquery = jpa.getQuery();
        return em.createQuery(cquery).getResultList();
    }
}
