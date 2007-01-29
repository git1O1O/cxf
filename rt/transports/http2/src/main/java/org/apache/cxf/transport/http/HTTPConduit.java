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

package org.apache.cxf.transport.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.io.AbstractWrappedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.http.conduit.HTTPConduitConfigBean;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl.EndpointReferenceUtils;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.handler.AbstractHandler;

import static org.apache.cxf.message.Message.DECOUPLED_CHANNEL_MESSAGE;
/**
 * HTTP Conduit implementation.
 */
public class HTTPConduit extends AbstractConduit {

    static {
        log = LogUtils.getL7dLogger(HTTPConduit.class);
    }

    public static final String HTTP_CONNECTION = "http.connection";
    
    private final Bus bus;
    private HTTPConduitConfigBean config;
    private final URLConnectionFactory alternateConnectionFactory;
    private URLConnectionFactory connectionFactory;
    private URL url;
    
    private ServerEngine decoupledEngine;
    private URL decoupledURL;
    private DecoupledDestination decoupledDestination;
    private EndpointInfo endpointInfo;
    

    /**
     * Constructor
     * 
     * @param b the associated Bus
     * @param ei the endpoint info of the initiator
     * @throws IOException
     */
    public HTTPConduit(Bus b, EndpointInfo ei) throws IOException {
        this(b,
             ei,
             null);
    }

    /**
     * Constructor
     * 
     * @param b the associated Bus
     * @param ei the endpoint info of the initiator
     * @param t the endpoint reference of the target
     * @throws IOException
     */
    public HTTPConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        this(b,
             ei,
             t,
             null,
             null);
    }    

    /**
     * Constructor, allowing subsititution of
     * connnection factory and decoupled engine.
     * 
     * @param b the associated Bus
     * @param ei the endpoint info of the initiator
     * @param t the endpoint reference of the target
     * @param factory the URL connection factory
     * @param eng the decoupled engine
     * @throws IOException
     */
    public HTTPConduit(Bus b,
                       EndpointInfo ei,
                       EndpointReferenceType t,
                       URLConnectionFactory factory,
                       ServerEngine eng) throws IOException {
        super(getTargetReference(ei, t));
        bus = b;
        endpointInfo = ei;
        alternateConnectionFactory = factory;

        initConfig();
        
        decoupledEngine = eng;
        url = t == null
              ? new URL(endpointInfo.getAddress())
              : new URL(t.getAddress().getValue());
    }

    protected void retrieveConnectionFactory() {
        connectionFactory = alternateConnectionFactory != null
                            ? alternateConnectionFactory
                            : HTTPTransportFactory.getConnectionFactory(
                                  config.getSslClient());
    }

    /**
     * @return the encapsulated config bean
     */
    protected HTTPConduitConfigBean getConfig() {
        return config;
    }

    /**
     * Send an outbound message.
     * 
     * @param message the message to be sent.
     */
    public void send(Message message) throws IOException {
        Map<String, List<String>> headers = setHeaders(message);
        URL currentURL = setupURL(message);        
        URLConnection connection = 
            connectionFactory.createConnection(getProxy(), currentURL);
        connection.setDoOutput(true);        
        //TODO using Message context to deceided HTTP send properties        
        connection.setConnectTimeout((int)config.getClient().getConnectionTimeout());
        connection.setReadTimeout((int)config.getClient().getReceiveTimeout());
        connection.setUseCaches(false);
        
        if (connection instanceof HttpURLConnection) {
            String httpRequestMethod = (String)message.get(Message.HTTP_REQUEST_METHOD);
            HttpURLConnection hc = (HttpURLConnection)connection;           
            if (null != httpRequestMethod) {
                hc.setRequestMethod(httpRequestMethod);                 
            } else {
                hc.setRequestMethod("POST");
            }
            if (config.getClient().isAutoRedirect()) {
                //cannot use chunking if autoredirect as the request will need to be
                //completely cached locally and resent to the redirect target
                hc.setInstanceFollowRedirects(true);
            } else {
                hc.setInstanceFollowRedirects(false);
                if (!hc.getRequestMethod().equals("GET")
                    && config.getClient().isAllowChunking()) {
                    hc.setChunkedStreamingMode(2048);
                }
            }
        }
        message.put(HTTP_CONNECTION, connection);
        setPolicies(message, headers);
     
        message.setContent(OutputStream.class,
                           new WrappedOutputStream(message, connection));
    }
    
    
    private URL setupURL(Message message) throws MalformedURLException {
        String value = (String)message.get(Message.ENDPOINT_ADDRESS);
        String pathInfo = (String)message.get(Message.PATH_INFO);
        String queryString = (String)message.get(Message.QUERY_STRING);
        
        String result = value != null ? value : url.toString();
        if (null != pathInfo && !result.endsWith(pathInfo)) { 
            result = result + pathInfo;
        }
        if (queryString != null) {
            result = result + "?" + queryString;
        }        
        return new URL(result);    
    }
    
    /**
     * Retreive the back-channel Destination.
     * 
     * @return the backchannel Destination (or null if the backchannel is
     * built-in)
     */
    public synchronized Destination getBackChannel() {
        if (decoupledDestination == null
            &&  config.getClient().getDecoupledEndpoint() != null) {
            decoupledDestination = setUpDecoupledDestination(); 
        }
        return decoupledDestination;
    }

    /**
     * Close the conduit
     */
    public void close() {
        if (url != null) {
            try {
                URLConnection connect = url.openConnection();
                if (connect instanceof HttpURLConnection) {
                    ((HttpURLConnection)connect).disconnect();
                }
            } catch (IOException ex) {
                //ignore
            }
            url = null;
        }
    
        // in decoupled case, close response Destination if reference count
        // hits zero
        //
        if (decoupledURL != null && decoupledEngine != null) {
            DecoupledHandler decoupledHandler = 
                (DecoupledHandler)decoupledEngine.getServant(decoupledURL);
            if (decoupledHandler != null) {
                decoupledHandler.release();
            }
        }
    }

    /**
     * @return the encapsulated URL
     */
    protected URL getURL() {
        return url;
    }
    
    /**
     * Get the target reference.
     * 
     * @param ei the corresponding EndpointInfo
     * @param t the constructor-provider target
     * @return the actual target
     */
    private static EndpointReferenceType getTargetReference(EndpointInfo ei,
                                                            EndpointReferenceType t) {
        EndpointReferenceType ref = null;
        if (null == t) {
            ref = new EndpointReferenceType();
            AttributedURIType address = new AttributedURIType();
            address.setValue(ei.getAddress());
            ref.setAddress(address);
        } else {
            ref = t;
        }
        return ref;
    }

    /**
     * Ensure an initial set of header is availbale on the outbound message.
     * 
     * @param message the outbound message
     * @return the headers
     */
    private Map<String, List<String>> setHeaders(Message message) {
        Map<String, List<String>> headers =
            CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));        
        if (null == headers) {
            headers = new HashMap<String, List<String>>();
            message.put(Message.PROTOCOL_HEADERS, headers);
        }
        return headers;
    }
    
    /**
     * Flush the headers onto the output stream.
     * 
     * @param message the outbound message
     * @throws IOException
     */
    protected void flushHeaders(Message message) throws IOException {
        URLConnection connection = (URLConnection)message.get(HTTP_CONNECTION);
        String ct = (String) message.get(Message.CONTENT_TYPE);
        if (null != ct) {
            connection.setRequestProperty(HttpHeaderHelper.CONTENT_TYPE, ct);
        } else {
            connection.setRequestProperty(HttpHeaderHelper.CONTENT_TYPE, "text/xml");
        }
        
        Map<String, List<String>> headers = 
            CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));
        if (null != headers) {
            for (String header : headers.keySet()) {
                List<String> headerList = headers.get(header);
                for (String value : headerList) {
                    connection.addRequestProperty(header, value);
                }
            }
        }
    }
   
    /**
     * Retrieve the respons code.
     * 
     * @param connection the URLConnection
     * @return the response code
     * @throws IOException
     */
    private int getResponseCode(URLConnection connection) throws IOException {
        int responseCode = HttpURLConnection.HTTP_OK;
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection hc = (HttpURLConnection)connection;
            responseCode = hc.getResponseCode();
        } else {
            if (connection.getHeaderField(Message.RESPONSE_CODE) != null) {
                responseCode =
                    Integer.parseInt(connection.getHeaderField(Message.RESPONSE_CODE));
            }
        }
        return responseCode;
    }

    /**
     * Set up the decoupled Destination if necessary.
     * 
     * @return an appropriate decoupled Destination
     */
    private DecoupledDestination setUpDecoupledDestination() {        
        EndpointReferenceType reference =
            EndpointReferenceUtils.getEndpointReference(
                config.getClient().getDecoupledEndpoint());
        if (reference != null) {
            String decoupledAddress = reference.getAddress().getValue();
            log.info("creating decoupled endpoint: " + decoupledAddress);
            try {
                decoupledURL = new URL(decoupledAddress);
                if (decoupledEngine == null) {
                    decoupledEngine = 
                        JettyHTTPServerEngine.getForPort(bus, 
                                                         decoupledURL.getProtocol(),
                                                         decoupledURL.getPort());
                }
                DecoupledHandler decoupledHandler =
                    (DecoupledHandler)decoupledEngine.getServant(decoupledURL);
                if (decoupledHandler == null) {
                    decoupledHandler = new DecoupledHandler();
                    decoupledEngine.addServant(decoupledURL, decoupledHandler);
                } 
                decoupledHandler.duplicate();
            } catch (Exception e) {
                // REVISIT move message to localizable Messages.properties
                log.log(Level.WARNING, "decoupled endpoint creation failed: ", e);
            }
        }
        return new DecoupledDestination(reference, incomingObserver);
    }
    
 

    /**
     * Wrapper output stream responsible for flushing headers and handling
     * the incoming HTTP-level response (not necessarily the MEP response).
     */
    private class WrappedOutputStream extends AbstractWrappedOutputStream {
        protected URLConnection connection;
        
        WrappedOutputStream(Message m, URLConnection c) {
            super(m);
            connection = c;
        }

        /**
         * Perform any actions required on stream flush (freeze headers,
         * reset output stream ... etc.)
         */
        protected void doFlush() throws IOException {
            if (!alreadyFlushed()) {                
                flushHeaders(outMessage);
                if (connection instanceof HttpURLConnection) {            
                    HttpURLConnection hc = (HttpURLConnection)connection;                    
                    if (hc.getRequestMethod().equals("GET")) {
                        return;
                    }
                }
                resetOut(connection.getOutputStream(), true);
            }
        }

        /**
         * Perform any actions required on stream closure (handle response etc.)
         */
        protected void doClose() throws IOException {
            handleResponse();
        }
        
        protected void onWrite() throws IOException {
            
        }

        private void handleResponse() throws IOException {
            Exchange exchange = outMessage.getExchange();
            int responseCode = getResponseCode(connection);
            if (isOneway(exchange)
                && !isPartialResponse(connection, responseCode)) {
                // oneway operation without partial response
                connection.getInputStream().close();
                return;
            }
            
            Message inMessage = new MessageImpl();
            inMessage.setExchange(exchange);
            InputStream in = null;
            Map<String, List<String>> headers = new HashMap<String, List<String>>();
            for (String key : connection.getHeaderFields().keySet()) {
                headers.put(HttpHeaderHelper.getHeaderKey(key), connection.getHeaderFields().get(key));
            }
            inMessage.put(Message.PROTOCOL_HEADERS, headers);
            inMessage.put(Message.RESPONSE_CODE, responseCode);
            inMessage.put(Message.CONTENT_TYPE, connection.getHeaderField(HttpHeaderHelper.CONTENT_TYPE));

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection hc = (HttpURLConnection)connection;
                in = hc.getErrorStream();
                if (null == in) {
                    in = connection.getInputStream();
                }
            } else {
                in = connection.getInputStream();
            }
            
            inMessage.setContent(InputStream.class, in);
            
            incomingObserver.onMessage(inMessage);
        }
    }
    
    /**
     * @param exchange the exchange in question
     * @return true iff the exchange indicates a oneway MEP
     */
    private boolean isOneway(Exchange exchange) {
        return exchange != null && exchange.isOneWay();
    }
    
    /**
     * @param connection the connection in question
     * @param responseCode the response code
     * @return true if a partial response is pending on the connection 
     */
    private boolean isPartialResponse(URLConnection connection,
                                      int responseCode) {
        return responseCode == HttpURLConnection.HTTP_ACCEPTED
               && connection.getContentLength() != 0;
    }

    /**
     * Wrapper output stream responsible for commiting incoming request 
     * containing a decoupled response.
     */
    private class WrapperInputStream extends FilterInputStream {
        HttpServletRequest request;
        HttpServletResponse response;
        boolean closed;
        
        WrapperInputStream(InputStream is,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
            super(is);
            request = req;
            response = resp;
        }
        
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                response.flushBuffer(); 
                Request baseRequest = (request instanceof Request) 
                    ? (Request)request : HttpConnection.getCurrentConnection().getRequest();
                baseRequest.setHandled(true);                
            }
        }
    }
    
    /**
     * Represented decoupled response endpoint.
     */
    protected class DecoupledDestination implements Destination {
        protected MessageObserver decoupledMessageObserver;
        private EndpointReferenceType address;
        
        DecoupledDestination(EndpointReferenceType ref,
                             MessageObserver incomingObserver) {
            address = ref;
            decoupledMessageObserver = incomingObserver;
        }

        public EndpointReferenceType getAddress() {
            return address;
        }

        public Conduit getBackChannel(Message inMessage,
                                      Message partialResponse,
                                      EndpointReferenceType addr)
            throws IOException {
            // shouldn't be called on decoupled endpoint
            return null;
        }

        public void shutdown() {
            // TODO Auto-generated method stub            
        }

        public synchronized void setMessageObserver(MessageObserver observer) {
            decoupledMessageObserver = observer;
        }
        
        protected synchronized MessageObserver getMessageObserver() {
            return decoupledMessageObserver;
        }
    }

    /**
     * Handles incoming decoupled responses.
     */
    private class DecoupledHandler extends AbstractHandler {
        private int refCount;
                
        synchronized void duplicate() {
            refCount++;
        }
        
        synchronized void release() {
            if (--refCount == 0) {
                decoupledEngine.removeServant(decoupledURL);
                JettyHTTPServerEngine.destroyForPort(decoupledURL.getPort());
            }
        }
        
        public void handle(String targetURI,
                           HttpServletRequest req,
                           HttpServletResponse resp,
                           int dispatch) throws IOException {
            InputStream responseStream = req.getInputStream();
            Message inMessage = new MessageImpl();
            // disposable exchange, swapped with real Exchange on correlation
            inMessage.setExchange(new ExchangeImpl());
            inMessage.put(DECOUPLED_CHANNEL_MESSAGE, Boolean.TRUE);
            // REVISIT: how to get response headers?
            //inMessage.put(Message.PROTOCOL_HEADERS, req.getXXX());
            setHeaders(inMessage);
            inMessage.put(Message.ENCODING, resp.getCharacterEncoding());
            inMessage.put(Message.CONTENT_TYPE, resp.getContentType());
            inMessage.put(Message.RESPONSE_CODE, HttpURLConnection.HTTP_OK);
            InputStream is = new WrapperInputStream(responseStream, req, resp);
            inMessage.setContent(InputStream.class, is);

            try {
                decoupledDestination.getMessageObserver().onMessage(inMessage);    
            } finally {
                is.close();
            }
        }
    }    

    private void initConfig() {
        config = new ConfigBean();
        // Initialize some default values for the configuration
        config.setClient(endpointInfo.getTraversedExtensor(new HTTPClientPolicy(), HTTPClientPolicy.class));
        config.setAuthorization(endpointInfo.getTraversedExtensor(new AuthorizationPolicy(), 
                                                                  AuthorizationPolicy.class));
        config.setProxyAuthorization(endpointInfo.getTraversedExtensor(new AuthorizationPolicy(), 
                                                                       AuthorizationPolicy.class));
    }

    private Proxy getProxy() {
        Proxy proxy = null;
        HTTPClientPolicy policy = config.getClient(); 
        if (policy.isSetProxyServer()) {
            proxy = new Proxy(Proxy.Type.valueOf(policy.getProxyServerType().toString()),
                              new InetSocketAddress(policy.getProxyServer(),
                                                    policy.getProxyServerPort()));
        }
        return proxy;
    }

    private void setPolicies(Message message, Map<String, List<String>> headers) {
        AuthorizationPolicy authPolicy = config.getAuthorization();
        AuthorizationPolicy newPolicy = message.get(AuthorizationPolicy.class);
        String userName = (String)message.get(Message.USERNAME);
        String passwd = (String)message.get(Message.PASSWORD);
        if (null != newPolicy && null != userName) {
            userName = newPolicy.getUserName();
            passwd = newPolicy.getPassword();
        }
        if (userName == null && authPolicy.isSetUserName()) {
            userName = authPolicy.getUserName();
        }
        if (userName != null) {
            if (passwd == null && authPolicy.isSetPassword()) {
                passwd = authPolicy.getPassword();
            }
            userName += ":";
            if (passwd != null) {
                userName += passwd;
            }
            userName = Base64Utility.encode(userName.getBytes());
            headers.put("Authorization",
                        Arrays.asList(new String[] {"Basic " + userName}));
        } else if (authPolicy.isSetAuthorizationType() && authPolicy.isSetAuthorization()) {
            String type = authPolicy.getAuthorizationType();
            type += " ";
            type += authPolicy.getAuthorization();
            headers.put("Authorization",
                        Arrays.asList(new String[] {type}));
        }
        AuthorizationPolicy proxyAuthPolicy = config.getProxyAuthorization();
        if (proxyAuthPolicy.isSetUserName()) {
            userName = proxyAuthPolicy.getUserName();
            if (userName != null) {
                passwd = "";
                if (proxyAuthPolicy.isSetPassword()) {
                    passwd = proxyAuthPolicy.getPassword();
                }
                userName += ":";
                if (passwd != null) {
                    userName += passwd;
                }
                userName = Base64Utility.encode(userName.getBytes());
                headers.put("Proxy-Authorization",
                            Arrays.asList(new String[] {"Basic " + userName}));
            } else if (proxyAuthPolicy.isSetAuthorizationType() && proxyAuthPolicy.isSetAuthorization()) {
                String type = proxyAuthPolicy.getAuthorizationType();
                type += " ";
                type += proxyAuthPolicy.getAuthorization();
                headers.put("Proxy-Authorization",
                            Arrays.asList(new String[] {type}));
            }
        }
        HTTPClientPolicy policy = config.getClient();
        if (policy.isSetCacheControl()) {
            headers.put("Cache-Control",
                        Arrays.asList(new String[] {policy.getCacheControl().value()}));
        }
        if (policy.isSetHost()) {
            headers.put("Host",
                        Arrays.asList(new String[] {policy.getHost()}));
        }
        if (policy.isSetConnection()) {
            headers.put("Connection",
                        Arrays.asList(new String[] {policy.getConnection().value()}));
        }
        if (policy.isSetAccept()) {
            headers.put("Accept",
                        Arrays.asList(new String[] {policy.getAccept()}));
        }
        if (policy.isSetAcceptEncoding()) {
            headers.put("Accept-Encoding",
                        Arrays.asList(new String[] {policy.getAcceptEncoding()}));
        }
        if (policy.isSetAcceptLanguage()) {
            headers.put("Accept-Language",
                        Arrays.asList(new String[] {policy.getAcceptLanguage()}));
        }
        if (policy.isSetContentType()) {
            headers.put(HttpHeaderHelper.CONTENT_TYPE,
                        Arrays.asList(new String[] {policy.getContentType()}));
        }
        if (policy.isSetCookie()) {
            headers.put("Cookie",
                        Arrays.asList(new String[] {policy.getCookie()}));
        }
        if (policy.isSetBrowserType()) {
            headers.put("BrowserType",
                        Arrays.asList(new String[] {policy.getBrowserType()}));
        }
        if (policy.isSetReferer()) {
            headers.put("Referer",
                        Arrays.asList(new String[] {policy.getReferer()}));
        }
    }

    private class ConfigBean extends HTTPConduitConfigBean implements Configurable {
        public String getBeanName() {
            if (endpointInfo.getName() != null) {
                return endpointInfo.getName().toString() + ".http-conduit";
            }
            return null;
        }        
    }
}
