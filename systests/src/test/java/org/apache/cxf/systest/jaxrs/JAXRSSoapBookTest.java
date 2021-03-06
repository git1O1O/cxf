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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.ResponseExceptionMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.form.Form;
import org.apache.cxf.jaxrs.ext.xml.XMLSource;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class JAXRSSoapBookTest extends AbstractBusClientServerTestBase {

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", 
                   launchServer(BookServerRestSoap.class));
    }
    
    @Test
    public void testGetAll() throws Exception {
        
        InputStream in = getHttpInputStream("http://localhost:9092/test/services/rest2/myRestService");
        assertEquals("0", getStringFromInputStream(in));
                
    }
    
    @Test
    public void testGetBook123ServletResponse() throws Exception {
        
        InputStream in = getHttpInputStream("http://localhost:9092/test/services/rest/bookstore/0");
        InputStream expected = getClass().getResourceAsStream("resources/expected_get_book123.txt");
        assertEquals(getStringFromInputStream(expected), getStringFromInputStream(in));
                
    }
    
    @Test
    public void testGetBook123() throws Exception {
        
        InputStream in = getHttpInputStream("http://localhost:9092/test/services/rest/bookstore/123");
        
        InputStream expected = getClass().getResourceAsStream("resources/expected_get_book123.txt");
        assertEquals(getStringFromInputStream(expected), getStringFromInputStream(in));
                
    }
    
    @Test
    public void testGetBook123Client() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                                  BookStoreJaxrsJaxws.class);
        Book b = proxy.getBook(new Long("123"));
        assertEquals(123, b.getId());
        assertEquals("CXF in Action", b.getName());
    }
    
    @Test
    public void testGetBook123WebClient() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        WebClient client = WebClient.create(baseAddress);
        client.path("/bookstore/123").accept(MediaType.APPLICATION_XML_TYPE);
        Book b = client.get(Book.class);
        assertEquals(123, b.getId());
        assertEquals("CXF in Action", b.getName());
    }
    
    @Test
    public void testGetBook123XMLSource() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        WebClient client = WebClient.create(baseAddress);
        client.path("/bookstore/123").accept(MediaType.APPLICATION_XML_TYPE);
        XMLSource source = client.get(XMLSource.class);
        source.setBuffering(true);
        Book b = source.getNode("/Book", Book.class);
        assertEquals(123, b.getId());
        assertEquals("CXF in Action", b.getName());
        b = source.getNode("/Book", Book.class);
        assertEquals(123, b.getId());
        assertEquals("CXF in Action", b.getName());
    }
    
    @Test
    public void testNoBookWebClient() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        WebClient client = WebClient.create(baseAddress);
        client.path("/bookstore/books/0/subresource").accept(MediaType.APPLICATION_XML_TYPE);
        Book b = client.get(Book.class);
        assertNull(b);
        assertEquals(204, client.getResponse().getStatus());
    }
    
    @Test
    public void testGetBook123WebClientResponse() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        WebClient client = WebClient.create(baseAddress);
        client.path("/bookstore/123").accept(MediaType.APPLICATION_XML_TYPE);
        Book b = readBook((InputStream)client.get().getEntity());
        assertEquals(123, b.getId());
        assertEquals("CXF in Action", b.getName());
    }
    
    @Test
    public void testGetBook356ClientException() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                          BookStoreJaxrsJaxws.class,
                                          Collections.singletonList(new TestResponseExceptionMapper()));
        
        try {
            proxy.getBook(356L);
            fail();
        } catch (BookNotFoundFault ex) {
            assertEquals("No Book with id 356 is available", ex.getMessage());
        }
    }
    
    
    @Test
    public void testGetBookSubresourceClient() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                                  BookStoreJaxrsJaxws.class);
        BookSubresource bs = proxy.getBookSubresource("125");
        Book b = bs.getTheBook();
        assertEquals(125, b.getId());
        assertEquals("CXF in Action", b.getName());
    }
    
    @Test
    public void testGetBookSubresourceParamExtensions() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                              BookStoreJaxrsJaxws.class);
        BookSubresource bs = proxy.getBookSubresource("139");
        Book bean = new Book("CXF Rocks", 139L);
        Book b = bs.getTheBook4(bean, bean, bean);
        assertEquals(139, b.getId());
        assertEquals("CXF Rocks", b.getName());
    }
    
    @Test
    public void testGetBookSubresourceWebClientParamExtensions() throws Exception {
        
        WebClient client = WebClient.create("http://localhost:9092/test/services/rest");
        client.type(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.APPLICATION_XML_TYPE);
        client.path("/bookstore/books/139/subresource4/139/CXF Rocks");
        Book bean = new Book("CXF Rocks", 139L);
        Book b = client.matrix("", bean).query("", bean).get(Book.class);
        assertEquals(139, b.getId());
        assertEquals("CXF Rocks", b.getName());
    }
    
    @Test
    public void testGetBookSubresourceClient2() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                                  BookStoreJaxrsJaxws.class);
        doTestSubresource(proxy);
        BookStoreJaxrsJaxws proxy2 = proxy.getBookStore("number1");
        doTestSubresource(proxy2);
        BookStoreJaxrsJaxws proxy3 = proxy2.getBookStore("number1");
        doTestSubresource(proxy3);
    }
    
    @Test
    public void testGetBookSubresourceWebClientProxyBean() throws Exception {
        
        WebClient client = WebClient.create("http://localhost:9092/test/services/rest");
        client.type(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_XML_TYPE, MediaType.TEXT_XML_TYPE);
        BookStoreJaxrsJaxws proxy = 
            JAXRSClientFactory.fromClient(client, BookStoreJaxrsJaxws.class, true);
        
        doTestSubresource(proxy);
        
        BookStoreJaxrsJaxws proxy2 = JAXRSClientFactory.fromClient(
            WebClient.client(proxy), BookStoreJaxrsJaxws.class);
        doTestSubresource(proxy2);
        
    }
    
    
    @Test
    public void testGetBookSubresourceWebClientProxy2() throws Exception {
        
        WebClient client = WebClient.create("http://localhost:9092/test/services/rest/bookstore")
            .path("/books/378");
        client.type(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.APPLICATION_XML_TYPE);
        BookSubresource proxy = JAXRSClientFactory.fromClient(client, BookSubresource.class);
        
        Book b = proxy.getTheBook2("CXF ", "in ", "Acti", "on ", "- 3", "7", "8");
        assertEquals(378, b.getId());
        assertEquals("CXF in Action - 378", b.getName());
        
    }
    
    private void doTestSubresource(BookStoreJaxrsJaxws proxy) throws Exception {
        BookSubresource bs = proxy.getBookSubresource("378");
        
        Book b = bs.getTheBook2("CXF ", "in ", "Acti", "on ", "- 3", "7", "8");
        assertEquals(378, b.getId());
        assertEquals("CXF in Action - 378", b.getName());
        
        WebClient.client(bs).reset().header("N4", "- 4");
        b = bs.getTheBook2("CXF ", "in ", "Acti", "on ", null, "7", "8");
        assertEquals(378, b.getId());
        assertEquals("CXF in Action - 478", b.getName());
        
        
        
    }
    
    @Test
    public void testGetBookWebClientForm() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest/bookstore/books/679/subresource3";
        WebClient wc = WebClient.create(baseAddress);
        MultivaluedMap<String, Object> map = new MetadataMap<String, Object>();
        map.putSingle("id", "679");
        map.putSingle("name", "CXF in Action - 679");
        Book b = readBook((InputStream)wc.accept("application/xml")
                          .form((Map<String, List<Object>>)map).getEntity());
        assertEquals(679, b.getId());
        assertEquals("CXF in Action - 679", b.getName());
    }
    
    @Test
    public void testGetBookWebClientForm2() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest/bookstore/books/679/subresource3";
        WebClient wc = WebClient.create(baseAddress);
        Form f = new Form();
        f.set("id", "679").set("name", "CXF in Action - 679");
        Book b = readBook((InputStream)wc.accept("application/xml")
                          .form(f).getEntity());
        assertEquals(679, b.getId());
        assertEquals("CXF in Action - 679", b.getName());
    }
    
    @Test
    public void testGetBookSubresourceClientFormParam() throws Exception {
        
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                                  BookStoreJaxrsJaxws.class);
        BookSubresource bs = proxy.getBookSubresource("679");
        Book b = bs.getTheBook3("679", "CXF in Action - 679");
        assertEquals(679, b.getId());
        assertEquals("CXF in Action - 679", b.getName());
    }
    
    @Test
    public void testAddGetBook123WebClient() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        WebClient client = WebClient.create(baseAddress);
        client.path("/bookstore/books").accept(MediaType.APPLICATION_XML_TYPE)
            .type(MediaType.APPLICATION_XML_TYPE);
        Book b = new Book();
        b.setId(124);
        b.setName("CXF in Action - 2");
        Book b2 = client.post(b, Book.class);
        assertNotSame(b, b2);
        assertEquals(124, b2.getId());
        assertEquals("CXF in Action - 2", b2.getName());
    }
    
    @Test
    public void testAddGetBook123Client() throws Exception {
        String baseAddress = "http://localhost:9092/test/services/rest";
        BookStoreJaxrsJaxws proxy = JAXRSClientFactory.create(baseAddress,
                                                                  BookStoreJaxrsJaxws.class);
        Book b = new Book();
        b.setId(124);
        b.setName("CXF in Action - 2");
        Book b2 = proxy.addBook(b);
        assertNotSame(b, b2);
        assertEquals(124, b2.getId());
        assertEquals("CXF in Action - 2", b2.getName());
    }
    
    @Test
    public void testAddGetBookRest() throws Exception {
        
        String endpointAddress =
            "http://localhost:9092/test/services/rest/bookstore/books";
        
        File input = new File(getClass().getResource("resources/add_book.txt").toURI());         
        PostMethod post = new PostMethod(endpointAddress);
        post.setRequestHeader("Content-Type", "application/xml");
        RequestEntity entity = new FileRequestEntity(input, "text/xml; charset=ISO-8859-1");
        post.setRequestEntity(entity);
        HttpClient httpclient = new HttpClient();
        
        try {
            int result = httpclient.executeMethod(post);
            assertEquals(200, result);
            
            InputStream expected = getClass().getResourceAsStream("resources/expected_add_book.txt");
            
            assertEquals(getStringFromInputStream(expected), post.getResponseBodyAsString());
        } finally {
            // Release current connection to the connection pool once you are done
            post.releaseConnection();
        }
                
    }
    
    @Test
    public void testGetBookSoap() throws Exception {
        String wsdlAddress =
            "http://localhost:9092/test/services/soap/bookservice?wsdl"; 
        URL wsdlUrl = new URL(wsdlAddress);
        BookSoapService service = 
            new BookSoapService(wsdlUrl,
                                new QName("http://books.com", "BookService"));
        BookStoreJaxrsJaxws store = service.getBookPort();
        Book book = store.getBook(new Long(123));
        assertEquals("id is wrong", book.getId(), 123);
        
        String listings = 
            getStringFromInputStream(getHttpInputStream("http://localhost:9092/test/services"));
        assertNotNull(listings);
    }

    private String getStringFromInputStream(InputStream in) throws Exception {        
        CachedOutputStream bos = new CachedOutputStream();
        IOUtils.copy(in, bos);
        in.close();
        bos.close();
        return bos.getOut().toString();        
    }

    private InputStream getHttpInputStream(String endpointAddress) throws Exception {
        URL url = new URL(endpointAddress);
        
        URLConnection connect = url.openConnection();
        connect.addRequestProperty("Accept", "application/xml,text/plain");
        return connect.getInputStream();
    }
    
    private Book readBook(InputStream is) throws Exception {
        JAXBContext c = JAXBContext.newInstance(new Class[]{Book.class});
        Unmarshaller u = c.createUnmarshaller();
        return (Book)u.unmarshal(is);
    }
    
    @Ignore
    public static class TestResponseExceptionMapper implements ResponseExceptionMapper<BookNotFoundFault> {
        
        public TestResponseExceptionMapper() {
        }
        
        public BookNotFoundFault fromResponse(Response r) {
            Object value = r.getMetadata().getFirst("BOOK-HEADER");
            if (value != null) {
                return new BookNotFoundFault(value.toString());
            }
            throw new WebApplicationException();
        }
        
    }
}
