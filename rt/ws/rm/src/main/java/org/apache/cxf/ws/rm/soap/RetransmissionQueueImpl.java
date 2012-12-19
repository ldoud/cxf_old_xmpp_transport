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

package org.apache.cxf.ws.rm.soap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.DeferredConduitSelector;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.io.CachedOutputStreamCallback;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.workqueue.SynchronousExecutor;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.builder.jaxb.JaxbAssertion;
import org.apache.cxf.ws.rm.ProtocolVariation;
import org.apache.cxf.ws.rm.RMContextUtils;
import org.apache.cxf.ws.rm.RMException;
import org.apache.cxf.ws.rm.RMManager;
import org.apache.cxf.ws.rm.RMMessageConstants;
import org.apache.cxf.ws.rm.RMProperties;
import org.apache.cxf.ws.rm.RMUtils;
import org.apache.cxf.ws.rm.RetransmissionCallback;
import org.apache.cxf.ws.rm.RetransmissionQueue;
import org.apache.cxf.ws.rm.RetryStatus;
import org.apache.cxf.ws.rm.SourceSequence;
import org.apache.cxf.ws.rm.manager.RetryPolicyType;
import org.apache.cxf.ws.rm.persistence.RMStore;
import org.apache.cxf.ws.rm.policy.RM10PolicyUtils;
import org.apache.cxf.ws.rm.v200702.Identifier;
import org.apache.cxf.ws.rm.v200702.SequenceType;
import org.apache.cxf.ws.rmp.v200502.RMAssertion;

/**
 * 
 */
public class RetransmissionQueueImpl implements RetransmissionQueue {

    private static final Logger LOG = LogUtils.getL7dLogger(RetransmissionQueueImpl.class);

    private Map<String, List<ResendCandidate>> candidates = 
        new HashMap<String, List<ResendCandidate>>();
    private Map<String, List<ResendCandidate>> suspendedCandidates = 
        new HashMap<String, List<ResendCandidate>>();
    private Resender resender;
    private RMManager manager;

    private int unacknowledgedCount;
    
    public RetransmissionQueueImpl(RMManager m) {
        manager = m;
    }

    public RMManager getManager() {
        return manager;
    }

    public void setManager(RMManager m) {
        manager = m;
    }

    public void addUnacknowledged(Message message) {
        cacheUnacknowledged(message);
    }

    /**
     * @param seq the sequence under consideration
     * @return the number of unacknowledged messages for that sequence
     */
    public synchronized int countUnacknowledged(SourceSequence seq) {
        List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
        return sequenceCandidates == null ? 0 : sequenceCandidates.size();
    }

    public int countUnacknowledged() {
        return unacknowledgedCount;
    }

    /**
     * @return true if there are no unacknowledged messages in the queue
     */
    public boolean isEmpty() {
        return 0 == getUnacknowledged().size();
    }

    /**
     * Purge all candidates for the given sequence that have been acknowledged.
     * 
     * @param seq the sequence object.
     */
    public void purgeAcknowledged(SourceSequence seq) {
        purgeCandidates(seq, false);
    }

    /**
     * Purge all candidates for the given sequence. This method is used to 
     * terminate the sequence by force and release the resource associated
     * with the sequence.
     *  
     * @param seq the sequence object.
     */
    public void purgeAll(SourceSequence seq) {
        purgeCandidates(seq, true);
    }
    
    private void purgeCandidates(SourceSequence seq, boolean any) {
        Collection<Long> purged = new ArrayList<Long>();
        synchronized (this) {
            LOG.fine("Start purging resend candidates.");
            List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
            if (null != sequenceCandidates) {
                for (int i = sequenceCandidates.size() - 1; i >= 0; i--) {
                    ResendCandidate candidate = sequenceCandidates.get(i);
                    long m = candidate.getNumber();
                    if (any || seq.isAcknowledged(m)) {
                        sequenceCandidates.remove(i);
                        candidate.resolved();
                        unacknowledgedCount--;
                        purged.add(m);
                    }
                }
                if (sequenceCandidates.isEmpty()) {
                    candidates.remove(seq.getIdentifier().getValue());
                }
            }
            LOG.fine("Completed purging resend candidates.");
        }
        if (purged.size() > 0) {
            RMStore store = manager.getStore();
            if (null != store) {
                store.removeMessages(seq.getIdentifier(), purged, true);
            }
        }
    }

    public List<Long> getUnacknowledgedMessageNumbers(SourceSequence seq) {
        List<Long> unacknowledged = new ArrayList<Long>();
        List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
        if (null != sequenceCandidates) {
            for (int i = 0; i < sequenceCandidates.size(); i++) {
                ResendCandidate candidate = sequenceCandidates.get(i);
                unacknowledged.add(candidate.getNumber());
            }
        }
        return unacknowledged;
    }
    
    public RetryStatus getRetransmissionStatus(SourceSequence seq, long num) {
        List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
        if (null != sequenceCandidates) {
            for (int i = 0; i < sequenceCandidates.size(); i++) {
                ResendCandidate candidate = sequenceCandidates.get(i);
                if (num == candidate.getNumber()) {
                    return candidate;
                }
            }
        }
        return null;
    }
    
    public Map<Long, RetryStatus> getRetransmissionStatuses(SourceSequence seq) {
        Map<Long, RetryStatus> cp = new HashMap<Long, RetryStatus>();
        List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
        if (null != sequenceCandidates) {
            for (int i = 0; i < sequenceCandidates.size(); i++) {
                ResendCandidate candidate = sequenceCandidates.get(i);
                cp.put(candidate.getNumber(), candidate);
            }
        }
        return cp;
    }

    /**
     * Initiate resends.
     */
    public void start() {
        if (null != resender) {
            return;
        }
        LOG.fine("Starting retransmission queue");

        // setup resender

        resender = getDefaultResender();
    }

    /**
     * Stops resending messages for the specified source sequence.
     */
    public void stop(SourceSequence seq) {
        synchronized (this) {
            List<ResendCandidate> sequenceCandidates = getSequenceCandidates(seq);
            if (null != sequenceCandidates) {
                for (int i = sequenceCandidates.size() - 1; i >= 0; i--) {
                    ResendCandidate candidate = sequenceCandidates.get(i);
                    candidate.cancel();
                }
                LOG.log(Level.FINE, "Cancelled resends for sequence {0}.", seq.getIdentifier().getValue());
            }           
        }
    }
    
    void stop() {
        
    }
    
    public void suspend(SourceSequence seq) {
        synchronized (this) {
            String key = seq.getIdentifier().getValue();
            List<ResendCandidate> sequenceCandidates = candidates.remove(key);
            if (null != sequenceCandidates) {
                for (int i = sequenceCandidates.size() - 1; i >= 0; i--) {
                    ResendCandidate candidate = sequenceCandidates.get(i);
                    candidate.suspend();
                }
                suspendedCandidates.put(key, sequenceCandidates);
                LOG.log(Level.FINE, "Suspended resends for sequence {0}.", key);
            }
        }
    }
    
    public void resume(SourceSequence seq) {
        synchronized (this) {
            String key = seq.getIdentifier().getValue();
            List<ResendCandidate> sequenceCandidates = suspendedCandidates.remove(key);
            if (null != sequenceCandidates) {
                for (int i = 0; i < sequenceCandidates.size(); i++) {
                    ResendCandidate candidate = sequenceCandidates.get(i);
                    candidate.resume();
                }
                candidates.put(key, sequenceCandidates);
                LOG.log(Level.FINE, "Resumed resends for sequence {0}.", key);
            }           
        }
    }
    
    /**
     * @return the exponential backoff
     */
    protected int getExponentialBackoff() {
        return DEFAULT_EXPONENTIAL_BACKOFF;
    }

    /**
     * @param message the message context
     * @return a ResendCandidate
     */
    protected ResendCandidate createResendCandidate(Message message) {
        return new ResendCandidate(message);
    }

    /**
     * Accepts a new resend candidate.
     * 
     * @param ctx the message context.
     * @return ResendCandidate
     */    
    protected ResendCandidate cacheUnacknowledged(Message message) {
        RMProperties rmps = RMContextUtils.retrieveRMProperties(message, true);
        SequenceType st = rmps.getSequence();
        Identifier sid = st.getIdentifier();
        String key = sid.getValue();
        
        ResendCandidate candidate = null;
        
        synchronized (this) {
            List<ResendCandidate> sequenceCandidates = getSequenceCandidates(key);
            if (null == sequenceCandidates) {
                sequenceCandidates = new ArrayList<ResendCandidate>();
                candidates.put(key, sequenceCandidates);
            }
            candidate = new ResendCandidate(message);
            if (isSequenceSuspended(key)) {
                candidate.suspend();
            }
            sequenceCandidates.add(candidate);
            unacknowledgedCount++;
        }
        LOG.fine("Cached unacknowledged message.");
        return candidate;
    }

    /**
     * @return a map relating sequence ID to a lists of un-acknowledged messages
     *         for that sequence
     */
    protected Map<String, List<ResendCandidate>> getUnacknowledged() {
        return candidates;
    }

    /**
     * @param seq the sequence under consideration
     * @return the list of resend candidates for that sequence
     * @pre called with mutex held
     */
    protected List<ResendCandidate> getSequenceCandidates(SourceSequence seq) {
        return getSequenceCandidates(seq.getIdentifier().getValue());
    }

    /**
     * @param key the sequence identifier under consideration
     * @return the list of resend candidates for that sequence
     * @pre called with mutex held
     */
    protected List<ResendCandidate> getSequenceCandidates(String key) {
        List<ResendCandidate> sc = candidates.get(key);
        if (null == sc) {
            sc = suspendedCandidates.get(key);
        }
        return sc;
    }
    
    /**
     * @param key the sequence identifier under consideration
     * @return true if the sequence is currently suspended; false otherwise
     * @pre called with mutex held
     */
    protected boolean isSequenceSuspended(String key) {
        return suspendedCandidates.containsKey(key);
    }

    private void clientResend(Message message) {
        Conduit c = message.getExchange().getConduit(message);
        resend(c, message);
    }

    private void serverResend(Message message) throws RMException {
        
        // get the message's to address
        
        AddressingProperties maps = RMContextUtils.retrieveMAPs(message, false, true);
        AttributedURIType to = null;
        if (null != maps) {
            to = maps.getTo();
        }
        if (null == to) {
            LOG.log(Level.SEVERE, "NO_ADDRESS_FOR_RESEND_MSG");
            return;
        }
        if (RMUtils.getAddressingConstants().getAnonymousURI().equals(to.getValue())) {
            LOG.log(Level.FINE, "Cannot resend to anonymous target");
            return;
        }
        
        final String address = to.getValue();
        LOG.fine("Resending to address: " + address);
        final ProtocolVariation protocol = RMContextUtils.getProtocolVariation(message);
        final Endpoint reliableEndpoint = manager.getReliableEndpoint(message).getEndpoint(protocol);

        ConduitSelector cs = new DeferredConduitSelector() {
            @Override
            public synchronized Conduit selectConduit(Message message) {
                Conduit conduit = null;
                EndpointInfo endpointInfo = reliableEndpoint.getEndpointInfo();
                EndpointReferenceType original =  endpointInfo.getTarget();
                try {
                    if (null != address) {
                        endpointInfo.setAddress(address);
                    }
                    conduit = super.selectConduit(message);
                } finally {
                    endpointInfo.setAddress(original);
                }
                return conduit;
            }
        };
        
        cs.setEndpoint(reliableEndpoint);
        Conduit c = cs.selectConduit(message);   
        // REVISIT
        // use application endpoint message observer instead?
        c.setMessageObserver(new MessageObserver() {
            public void onMessage(Message message) {
                LOG.fine("Ignoring response to resent message.");
            }
            
        });
        resend(c, message);
    }
    
    private void resend(Conduit c, Message message) {
        try {

            // get registered callbacks, create new output stream and
            // re-register
            // all callbacks except the retransmission callback

            OutputStream os = message.getContent(OutputStream.class);
            List<CachedOutputStreamCallback> callbacks = null;
            
            if (os instanceof CachedOutputStream) {
                callbacks = ((CachedOutputStream)os).getCallbacks();
            }
            message.removeContent(OutputStream.class);
            c.prepare(message);

            os = message.getContent(OutputStream.class);
            
            if (null != callbacks && callbacks.size() > 1) {
                if (!(os instanceof CachedOutputStream)) {
                    os = RMUtils.createCachedStream(message, os);
                }
                for (CachedOutputStreamCallback cb : callbacks) {
                    if (!(cb instanceof RetransmissionCallback)) {
                        ((CachedOutputStream)os).registerCallback(cb);
                    }
                }
            }
            CachedOutputStream content = (CachedOutputStream)message
                .get(RMMessageConstants.SAVED_CONTENT);
            InputStream bis = null;
            if (null == content) {
                byte[] savedbytes = message.getContent(byte[].class);
                bis = new ByteArrayInputStream(savedbytes); 
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Using saved byte array: " + Arrays.toString(savedbytes));
                }
            } else {
                bis = content.getInputStream();
                if (LOG.isLoggable(Level.FINE)) {
                    if (content.size() < 65536) {
                        LOG.fine("Using saved output stream: " 
                                 + IOUtils.newStringFromBytes(content.getBytes()));                        
                    } else {                        
                        LOG.fine("Using saved output stream: ...");                        
                    }
                }
            }

            // copy saved output stream to new output stream in chunks of 1024
            IOUtils.copyAndCloseInput(bis, os);
            os.flush();
            os.close();
        } catch (ConnectException ex) {
            //ignore, we'll just resent again later
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "RESEND_FAILED_MSG", ex);
        }
    }

    /**
     * Represents a candidate for resend, i.e. an unacked outgoing message.
     */
    protected class ResendCandidate implements Runnable, RetryStatus {
        private Message message;
        private long number;
        private OutputStream out;
        private Date next;
        private TimerTask nextTask;
        private int retries;
        private int maxRetries;
        private long nextInterval;
        private long backoff;
        private boolean pending;
        private boolean suspended;
        private boolean includeAckRequested;

        /**
         * @param ctx message context for the unacked message
         */
        protected ResendCandidate(Message m) {
            message = m;
            retries = 0;
            out = m.getContent(OutputStream.class);
            org.apache.cxf.ws.rmp.v200502.RMAssertion rma = 
                RM10PolicyUtils.getRMAssertion(manager.getRMAssertion(), message);
            long baseRetransmissionInterval = 
                rma.getBaseRetransmissionInterval().getMilliseconds().longValue();
            backoff = null != rma.getExponentialBackoff() 
                ? RetransmissionQueue.DEFAULT_EXPONENTIAL_BACKOFF : 1;
            next = new Date(System.currentTimeMillis() + baseRetransmissionInterval);
            nextInterval = baseRetransmissionInterval * backoff;
            RetryPolicyType rmrp = null != manager.getSourcePolicy() 
                ? manager.getSourcePolicy().getRetryPolicy() : null; 
            maxRetries = null != rmrp ? rmrp.getMaxRetries() : 0;
            
            AddressingProperties maps = RMContextUtils.retrieveMAPs(message, false, true);
            AttributedURIType to = null;
            if (null != maps) {
                to = maps.getTo();
            }
            if (to != null 
                && RMUtils.getAddressingConstants().getAnonymousURI().equals(to.getValue())) {
                LOG.log(Level.INFO, "Cannot resend to anonymous target.  Not scheduling a resend.");
                return;
            }
            RMProperties rmprops = RMContextUtils.retrieveRMProperties(message, true);
            if (null != rmprops) {
                number = rmprops.getSequence().getMessageNumber();
            }
            if (null != manager.getTimer()) {
                schedule();
            }
        }

        /**
         * Initiate resend asynchronsly.
         * 
         * @param requestAcknowledge true if a AckRequest header is to be sent
         *            with resend
         */
        protected void initiate(boolean requestAcknowledge) {
            includeAckRequested = requestAcknowledge;
            pending = true;
            Endpoint ep = message.getExchange().get(Endpoint.class);
            Executor executor = ep.getExecutor();
            if (null == executor) {
                executor = ep.getService().getExecutor();
                if (executor == null) {
                    executor = SynchronousExecutor.getInstance();
                } else {
                    LOG.log(Level.FINE, "Using service executor {0}", executor.getClass().getName());
                }
            } else {
                LOG.log(Level.FINE, "Using endpoint executor {0}", executor.getClass().getName());
            }
            
            try {
                executor.execute(this);
            } catch (RejectedExecutionException ex) {
                LOG.log(Level.SEVERE, "RESEND_INITIATION_FAILED_MSG", ex);
            }
        }

        public void run() {
            try {
                // ensure ACK wasn't received while this task was enqueued
                // on executor
                if (isPending()) {
                    message.setContent(OutputStream.class, out);
                    resender.resend(message, includeAckRequested);
                    includeAckRequested = false;
                }
            } finally {
                attempted();
            }
        }

        public long getNumber() {
            return number;
        }
        
        /**
         * @return number of resend attempts
         */
        public int getRetries() {
            return retries;
        }

        /**
         * @return number of max resend attempts
         */
        public int getMaxRetries() {
            return maxRetries;
        }
        
        /**
         * @return date of next resend
         */
        public Date getNext() {
            return next;
        }

        /**
         * @return date of previous resend or null if no attempt is yet taken 
         */
        public Date getPrevious() {
            if (retries > 0) {
                return new Date(next.getTime() - nextInterval / backoff);
            }
            return null;
        }

        public long getNextInterval() {
            return nextInterval;
        }

        public long getBackoff() {
            return backoff;
        }

        public boolean isSuspended() {
            return suspended;
        }

        /**
         * @return if resend attempt is pending
         */
        public synchronized boolean isPending() {
            return pending;
        }

        /**
         * ACK has been received for this candidate.
         */
        protected synchronized void resolved() {
            pending = false;
            next = null;
            if (null != nextTask) {
                nextTask.cancel();
                releaseSavedMessage();
            }
        }
        
        /**
         * Cancel further resend (although no ACK has been received).
         */
        protected synchronized void cancel() {
            if (null != nextTask) {
                nextTask.cancel();
                releaseSavedMessage();
            }
        }

        protected synchronized void suspend() {
            suspended = true;
            pending = false;
            //TODO release the message and later reload it upon resume
            //cancel();
            if (null != nextTask) {
                nextTask.cancel();
            }
        }

        protected synchronized void resume() {
            suspended = false;
            next = new Date(System.currentTimeMillis());
            attempted();
        }

        private void releaseSavedMessage() {
            CachedOutputStream saved = (CachedOutputStream)message.remove(RMMessageConstants.SAVED_CONTENT);
            if (saved != null) {
                saved.releaseTempFileHold();
                // call close to dispose
                try {
                    saved.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        /**
         * @return associated message context
         */
        protected Message getMessage() {
            return message;
        }

        /**
         * A resend has been attempted. Schedule the next attempt.
         */
        protected synchronized void attempted() {
            pending = false;
            retries++;
            if (null != next && maxRetries != retries) {
                next = new Date(next.getTime() + nextInterval);
                nextInterval *= backoff;
                schedule();
            }
        }

        protected final synchronized void schedule() {
            if (null == manager.getTimer()) {
                return;
            }
            class ResendTask extends TimerTask {
                ResendCandidate candidate;

                ResendTask(ResendCandidate c) {
                    candidate = c;
                }

                @Override
                public void run() {
                    if (!candidate.isPending()) {
                        candidate.initiate(includeAckRequested);
                    }
                }
            }
            nextTask = new ResendTask(this);
            try {
                manager.getTimer().schedule(nextTask, next);
            } catch (IllegalStateException ex) {
                LOG.log(Level.WARNING, "SCHEDULE_RESEND_FAILED_MSG", ex);
            }
        }
    }

    /**
     * Encapsulates actual resend logic (pluggable to facilitate unit testing)
     */
    public interface Resender {
        /**
         * Resend mechanics.
         * 
         * @param context the cloned message context.
         * @param if a AckRequest should be included
         */
        void resend(Message message, boolean requestAcknowledge);
    }

    /**
     * Create default Resender logic.
     * 
     * @return default Resender
     */
    protected final Resender getDefaultResender() {
        return new Resender() {
            public void resend(Message message, boolean requestAcknowledge) {
                RMProperties properties = RMContextUtils.retrieveRMProperties(message, true);
                SequenceType st = properties.getSequence();
                if (st != null) {
                    LOG.log(Level.INFO, "RESEND_MSG", st.getMessageNumber());
                }
                try {
                    // TODO: remove previously added acknowledgments and update
                    // message id (to avoid duplicates)

                    if (MessageUtils.isRequestor(message)) {
                        clientResend(message);
                    } else {
                        serverResend(message);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "RESEND_FAILED_MSG", e);
                }
            }
        };
    }

    /**
     * Plug in replacement resend logic (facilitates unit testing).
     * 
     * @param replacement resend logic
     */
    protected void replaceResender(Resender replacement) {
        resender = replacement;
    }

    @SuppressWarnings("unchecked")
    protected JaxbAssertion<RMAssertion> getAssertion(AssertionInfo ai) {
        return (JaxbAssertion<RMAssertion>)ai.getAssertion();
    }

}
