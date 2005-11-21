package org.objectweb.celtix.bus.transports.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.naming.NamingException;
import javax.wsdl.WSDLException;
import javax.xml.ws.handler.MessageContext;

import org.objectweb.celtix.Bus;
import org.objectweb.celtix.addressing.EndpointReferenceType;
import org.objectweb.celtix.common.logging.LogUtils;
import org.objectweb.celtix.context.InputStreamMessageContext;
import org.objectweb.celtix.context.OutputStreamMessageContext;
import org.objectweb.celtix.transports.ClientTransport;
import org.objectweb.celtix.transports.jms.context.JMSClientHeadersType;



public class JMSClientTransport extends JMSTransportBase implements ClientTransport {
    
    private static final Logger LOG = LogUtils.getL7dLogger(JMSClientTransport.class);

    public JMSClientTransport(Bus bus, EndpointReferenceType address) throws WSDLException, IOException  {
        super(bus, address);
        entry("JMSClientTransport Constructor");
    }

    
    /**
     * Called by the native ClientTransport::disconnect(), giving notice that
     * the connection maintained the underlying middleware is no longer
     * required by this transport instance.
     */
    public void disconnect() {
        entry("JMSClientTransport disconnect()");

        // ensure resources held by session factory are released
        //
        if (sessionFactory != null) {
            sessionFactory.shutdown();
        }
    }
    
    //TODO: Revisit for proper implementation and changes if any.
    
    public void shutdown() {
        this.disconnect();
    }
    
    public OutputStreamMessageContext createOutputStreamContext(MessageContext context) throws IOException {
        return new JMSOutputStreamContext(context);
    }

    
    public void finalPrepareOutputStreamContext(OutputStreamMessageContext context) throws IOException {
    }
    
    /**
     * The ClientTransport::send()/receive() call sequence is unified via
     * invoke() to facilitate maintaining the context of the reply destination.
     *
     * @param request the buffer to send
     * @return the response buffer
     */
    public InputStreamMessageContext invoke(OutputStreamMessageContext context) 
        throws IOException {
        //Use the destination style to determine Destination type
        //as checking the instance of Destination is not reliable.
        //DestinationImpl might implement both Queue and Topic as in
        //the case of Weblogic.
        if (!queueDestinationStyle) {
            LOG.log(Level.WARNING, "Non-oneway invocations not supported for JMS Topics");
            throw new IOException("Non-oneway invocations not supported for JMS Topics");
        //    throw new Exception("Non-oneway invocations not supported for JMS Topics");
        }

        try {
            if (textPayload) {
                String responseString = (String) invoke(context, true);
                return new JMSInputStreamContext(new ByteArrayInputStream(responseString.getBytes()));
            } else {
                return new JMSInputStreamContext(new ByteArrayInputStream((byte[])invoke(context, true)));
            }
        } catch (Exception ex) {
            //TODO: decide what to do with the exception.
            throw new IOException(ex.getMessage());
        }  
    }


    /**
     * Variant on invoke used for oneways.
     *
     * @param request the buffer to send
     */
    public void invokeOneway(OutputStreamMessageContext context) throws IOException {
        try {
            invoke(context, true);   
        } catch (Exception ex) {
            throw new IOException(ex.getMessage());
        }
    }
    
    public Future<InputStreamMessageContext> invokeAsync(OutputStreamMessageContext context) 
        throws IOException {
        return null;
    }


    /**
     * Internal invoke mechanics.
     *
     * @param request the buffer to send
     * @param responseExpected true iff a response is expected
     * @return the response buffer if expected
     */
    private Object invoke(OutputStreamMessageContext context, boolean responseExpected)
        throws JMSException, NamingException {
        entry("JMSClientTransport invoke()");
       
        try {
            JMSProviderHub.connect(this);
        } catch (JMSException ex) {
            LOG.log(Level.FINE, "JMS connect failed with JMSException : ", ex);
            throw ex;
        } catch (NamingException e) {
            LOG.log(Level.FINE, "JMS connect failed with NamingException : ", e);
            throw e;
        }    
       
        if (sessionFactory == null) {
            throw new java.lang.IllegalStateException("JMSClientTransport not connected");
        }

        PooledSession pooledSession = sessionFactory.get(responseExpected);
        send(pooledSession, context);

        Object response = null;

        if (responseExpected) {
            response = receive(pooledSession, context);
        }

        sessionFactory.recycle(pooledSession);

        return response;
    }


    /**
     * Send mechanics.
     *
     * @param request the request buffer
     * @param pooledSession the shared JMS resources
     */
    private void send(PooledSession pooledSession, 
                              OutputStreamMessageContext context) 
        throws JMSException {
        Object request;
        
        if (textPayload) {
            request = context.getOutputStream().toString();
        } else {
            request = ((ByteArrayOutputStream) context.getOutputStream()).toByteArray();
        }
        
        Message message = marshal(request, pooledSession.session(), pooledSession.destination());

        JMSClientHeadersType headers = 
            (JMSClientHeadersType) context.get(JMSConstants.JMS_REQUEST_HEADERS);


        int deliveryMode = getJMSDeliveryMode(headers);
        int priority = getJMSPriority(headers);
        long ttl = getTimeToLive(headers);

        setMessageProperties(headers, message);

        LOG.log(Level.FINE, "client sending request: ",  message);

        if (queueDestinationStyle) {
            QueueSender sender = (QueueSender) pooledSession.producer();
            sender.send((Queue) targetDestination, message, deliveryMode, priority, ttl);
        } else {
            TopicPublisher publisher = (TopicPublisher) pooledSession.producer();
            publisher.publish((Topic) targetDestination, message, deliveryMode, priority, ttl);
        }
    }


    /**
     * Receive mechanics.
     *
     * @param pooledSession the shared JMS resources
     * @retrun the response buffer
     */
    private Object receive(PooledSession pooledSession, 
                           OutputStreamMessageContext context) 
        throws JMSException {
        Object response = null;

        JMSClientHeadersType headers =
            (JMSClientHeadersType) context.get(JMSConstants.JMS_REQUEST_HEADERS);

        long timeout = 0;

        if (headers.getTimeOut() != null) {
            timeout = headers.getTimeOut().longValue();
        }

        Message message = pooledSession.consumer().receive(timeout);
        LOG.log(Level.FINE, "client received reply: " , message);

        if (message != null) {
            populateIncomingContext(message, context, false);
            response = unmarshal(message);
            return response;
        } else {
            String error = "JMSClientTransport::receive() timed out. No message available.";
            LOG.log(Level.SEVERE, error);
            //TODO: Review what exception should we throw.
            throw new JMSException(error);
        }
    }
}
