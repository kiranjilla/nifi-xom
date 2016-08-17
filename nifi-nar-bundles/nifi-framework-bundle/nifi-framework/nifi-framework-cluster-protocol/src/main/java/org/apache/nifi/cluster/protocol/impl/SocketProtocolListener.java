/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.cluster.protocol.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.cluster.protocol.ProtocolContext;
import org.apache.nifi.cluster.protocol.ProtocolException;
import org.apache.nifi.cluster.protocol.ProtocolHandler;
import org.apache.nifi.cluster.protocol.ProtocolListener;
import org.apache.nifi.cluster.protocol.ProtocolMessageMarshaller;
import org.apache.nifi.cluster.protocol.ProtocolMessageUnmarshaller;
import org.apache.nifi.cluster.protocol.message.ConnectionRequestMessage;
import org.apache.nifi.cluster.protocol.message.DisconnectMessage;
import org.apache.nifi.cluster.protocol.message.FlowRequestMessage;
import org.apache.nifi.cluster.protocol.message.HeartbeatMessage;
import org.apache.nifi.cluster.protocol.message.ProtocolMessage;
import org.apache.nifi.cluster.protocol.message.ReconnectionRequestMessage;
import org.apache.nifi.events.BulletinFactory;
import org.apache.nifi.io.socket.ServerSocketConfiguration;
import org.apache.nifi.io.socket.SocketListener;
import org.apache.nifi.reporting.Bulletin;
import org.apache.nifi.reporting.BulletinRepository;
import org.apache.nifi.security.util.CertificateUtils;
import org.apache.nifi.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a listener for protocol messages sent over unicast socket.
 *
 */
public class SocketProtocolListener extends SocketListener implements ProtocolListener {

    private static final Logger logger = LoggerFactory.getLogger(SocketProtocolListener.class);
    private final ProtocolContext<ProtocolMessage> protocolContext;
    private final Collection<ProtocolHandler> handlers = new CopyOnWriteArrayList<>();
    private volatile BulletinRepository bulletinRepository;

    public SocketProtocolListener(
            final int numThreads,
            final int port,
            final ServerSocketConfiguration configuration,
            final ProtocolContext<ProtocolMessage> protocolContext) {

        super(numThreads, port, configuration);

        if (protocolContext == null) {
            throw new IllegalArgumentException("Protocol Context may not be null.");
        }

        this.protocolContext = protocolContext;
    }

    @Override
    public void setBulletinRepository(final BulletinRepository bulletinRepository) {
        this.bulletinRepository = bulletinRepository;
    }

    @Override
    public void start() throws IOException {

        if (super.isRunning()) {
            throw new IllegalStateException("Instance is already started.");
        }

        super.start();
    }

    @Override
    public void stop() throws IOException {

        if (super.isRunning() == false) {
            throw new IOException("Instance is already stopped.");
        }

        super.stop();

    }

    @Override
    public Collection<ProtocolHandler> getHandlers() {
        return Collections.unmodifiableCollection(handlers);
    }

    @Override
    public void addHandler(final ProtocolHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Protocol handler may not be null.");
        }
        handlers.add(handler);
    }

    @Override
    public boolean removeHandler(final ProtocolHandler handler) {
        return handlers.remove(handler);
    }

    @Override
    public void dispatchRequest(final Socket socket) {
        byte[] receivedMessage = null;
        String hostname = null;
        final int maxMsgBuffer = 1024 * 1024;   // don't buffer more than 1 MB of the message
        try {
            final StopWatch stopWatch = new StopWatch(true);
            hostname = socket.getInetAddress().getHostName();
            final String requestId = UUID.randomUUID().toString();
            logger.debug("Received request {} from {}", requestId, hostname);

            String requestorDn = getRequestorDN(socket);

            // unmarshall message
            final ProtocolMessageUnmarshaller<ProtocolMessage> unmarshaller = protocolContext.createUnmarshaller();
            final InputStream inStream = socket.getInputStream();
            final CopyingInputStream copyingInputStream = new CopyingInputStream(inStream, maxMsgBuffer); // don't copy more than 1 MB

            final ProtocolMessage request;
            try {
                request = unmarshaller.unmarshal(copyingInputStream);
            } finally {
                receivedMessage = copyingInputStream.getBytesRead();
                if (logger.isDebugEnabled()) {
                    logger.debug("Received message: " + new String(receivedMessage));
                }
            }

            request.setRequestorDN(requestorDn);

            // dispatch message to handler
            ProtocolHandler desiredHandler = null;
            final Collection<ProtocolHandler> handlers = getHandlers();
            for (final ProtocolHandler handler : handlers) {
                if (handler.canHandle(request)) {
                    desiredHandler = handler;
                    break;
                }
            }

            // if no handler found, throw exception; otherwise handle request
            if (desiredHandler == null) {
                logger.error("Received request of type {} but none of the following Protocol Handlers were able to process the request: {}", request.getType(), handlers);
                throw new ProtocolException("No handler assigned to handle message type: " + request.getType());
            } else {
                final ProtocolMessage response = desiredHandler.handle(request);
                if (response != null) {
                    try {
                        logger.debug("Sending response for request {}", requestId);

                        // marshal message to output stream
                        final ProtocolMessageMarshaller<ProtocolMessage> marshaller = protocolContext.createMarshaller();
                        marshaller.marshal(response, socket.getOutputStream());
                    } catch (final IOException ioe) {
                        throw new ProtocolException("Failed marshalling protocol message in response to message type: " + request.getType() + " due to " + ioe, ioe);
                    }
                }
            }

            stopWatch.stop();
            final NodeIdentifier nodeId = getNodeIdentifier(request);
            final String from = nodeId == null ? hostname : nodeId.toString();
            logger.info("Finished processing request {} (type={}, length={} bytes) from {} in {} millis",
                requestId, request.getType(), receivedMessage.length, from, stopWatch.getDuration(TimeUnit.MILLISECONDS));
        } catch (final IOException | ProtocolException e) {
            logger.warn("Failed processing protocol message from " + hostname + " due to " + e, e);

            if (bulletinRepository != null) {
                final Bulletin bulletin = BulletinFactory.createBulletin("Clustering", "WARNING", String.format("Failed to process protocol message from %s due to: %s", hostname, e.toString()));
                bulletinRepository.addBulletin(bulletin);
            }
        }
    }

    private NodeIdentifier getNodeIdentifier(final ProtocolMessage message) {
        if (message == null) {
            return null;
        }

        switch (message.getType()) {
            case CONNECTION_REQUEST:
                return ((ConnectionRequestMessage) message).getConnectionRequest().getProposedNodeIdentifier();
            case HEARTBEAT:
                return ((HeartbeatMessage) message).getHeartbeat().getNodeIdentifier();
            case DISCONNECTION_REQUEST:
                return ((DisconnectMessage) message).getNodeId();
            case FLOW_REQUEST:
                return ((FlowRequestMessage) message).getNodeId();
            case RECONNECTION_REQUEST:
                return ((ReconnectionRequestMessage) message).getNodeId();
            default:
                return null;
        }
    }

    private String getRequestorDN(Socket socket) {
        try {
            return CertificateUtils.extractPeerDNFromSSLSocket(socket);
        } catch (CertificateException e) {
            throw new ProtocolException(e);
        }
    }
}
