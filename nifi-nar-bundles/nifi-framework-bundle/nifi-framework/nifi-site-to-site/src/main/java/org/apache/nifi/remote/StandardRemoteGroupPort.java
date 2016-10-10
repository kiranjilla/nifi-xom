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
package org.apache.nifi.remote;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.connectable.ConnectableType;
import org.apache.nifi.connectable.Connection;
import org.apache.nifi.controller.ProcessScheduler;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.groups.RemoteProcessGroup;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.remote.client.SiteToSiteClient;
import org.apache.nifi.remote.exception.PortNotRunningException;
import org.apache.nifi.remote.exception.ProtocolException;
import org.apache.nifi.remote.exception.UnknownPortException;
import org.apache.nifi.remote.protocol.DataPacket;
import org.apache.nifi.remote.protocol.http.HttpProxy;
import org.apache.nifi.remote.util.StandardDataPacket;
import org.apache.nifi.reporting.Severity;
import org.apache.nifi.scheduling.SchedulingStrategy;
import org.apache.nifi.util.FormatUtils;
import org.apache.nifi.util.NiFiProperties;
import org.apache.nifi.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardRemoteGroupPort extends RemoteGroupPort {

    private static final long BATCH_SEND_NANOS = TimeUnit.MILLISECONDS.toNanos(500L); // send batches of up to 500 millis
    public static final String USER_AGENT = "NiFi-Site-to-Site";
    public static final String CONTENT_TYPE = "application/octet-stream";

    public static final int GZIP_COMPRESSION_LEVEL = 1;

    private static final String CATEGORY = "Site to Site";

    private static final Logger logger = LoggerFactory.getLogger(StandardRemoteGroupPort.class);
    private final RemoteProcessGroup remoteGroup;
    private final AtomicBoolean useCompression = new AtomicBoolean(false);
    private final AtomicBoolean targetExists = new AtomicBoolean(true);
    private final AtomicBoolean targetRunning = new AtomicBoolean(true);
    private final SSLContext sslContext;
    private final TransferDirection transferDirection;
    private final NiFiProperties nifiProperties;

    private final AtomicReference<SiteToSiteClient> clientRef = new AtomicReference<>();

    SiteToSiteClient getSiteToSiteClient() {
        return clientRef.get();
    }

    public StandardRemoteGroupPort(final String id, final String name, final ProcessGroup processGroup, final RemoteProcessGroup remoteGroup,
            final TransferDirection direction, final ConnectableType type, final SSLContext sslContext, final ProcessScheduler scheduler,
            final NiFiProperties nifiProperties) {
        // remote group port id needs to be unique but cannot just be the id of the port
        // in the remote group instance. this supports referencing the same remote
        // instance more than once.
        super(id, name, processGroup, type, scheduler);

        this.remoteGroup = remoteGroup;
        this.transferDirection = direction;
        this.sslContext = sslContext;
        this.nifiProperties = nifiProperties;
        setScheduldingPeriod(MINIMUM_SCHEDULING_NANOS + " nanos");
    }

    private static File getPeerPersistenceFile(final String portId, final NiFiProperties nifiProperties) {
        final File stateDir = nifiProperties.getPersistentStateDirectory();
        return new File(stateDir, portId + ".peers");
    }

    @Override
    public boolean isTargetRunning() {
        return targetRunning.get();
    }

    public void setTargetRunning(final boolean targetRunning) {
        this.targetRunning.set(targetRunning);
    }

    @Override
    public boolean isTriggerWhenEmpty() {
        return getConnectableType() == ConnectableType.REMOTE_OUTPUT_PORT;
    }

    @Override
    public Authorizable getParentAuthorizable() {
        return getRemoteProcessGroup();
    }

    @Override
    public void shutdown() {
        super.shutdown();

        final SiteToSiteClient client = getSiteToSiteClient();
        if (client != null) {
            try {
                client.close();
            } catch (final IOException ioe) {
                logger.warn("Failed to properly shutdown Site-to-Site Client due to {}", ioe);
            }
        }
    }

    @Override
    public void onSchedulingStart() {
        super.onSchedulingStart();

        final long penalizationMillis = FormatUtils.getTimeDuration(remoteGroup.getYieldDuration(), TimeUnit.MILLISECONDS);

        final SiteToSiteClient client = new SiteToSiteClient.Builder()
                .url(remoteGroup.getTargetUri().toString())
                .portIdentifier(getIdentifier())
                .sslContext(sslContext)
                .useCompression(isUseCompression())
                .eventReporter(remoteGroup.getEventReporter())
                .peerPersistenceFile(getPeerPersistenceFile(getIdentifier(), nifiProperties))
                .nodePenalizationPeriod(penalizationMillis, TimeUnit.MILLISECONDS)
                .timeout(remoteGroup.getCommunicationsTimeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .transportProtocol(remoteGroup.getTransportProtocol())
                .httpProxy(new HttpProxy(remoteGroup.getProxyHost(), remoteGroup.getProxyPort(), remoteGroup.getProxyUser(), remoteGroup.getProxyPassword()))
                .build();
        clientRef.set(client);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        if (!remoteGroup.isTransmitting()) {
            logger.debug("{} {} is not transmitting; will not send/receive", this, remoteGroup);
            return;
        }

        if (getConnectableType() == ConnectableType.REMOTE_INPUT_PORT && session.getQueueSize().getObjectCount() == 0) {
            logger.debug("{} No data to send", this);
            return;
        }

        final String url = getRemoteProcessGroup().getTargetUri().toString();

        // If we are sending data, we need to ensure that we have at least 1 FlowFile to send. Otherwise,
        // we don't want to create a transaction at all.
        final FlowFile firstFlowFile;
        if (getConnectableType() == ConnectableType.REMOTE_INPUT_PORT) {
            firstFlowFile = session.get();
            if (firstFlowFile == null) {
                return;
            }
        } else {
            firstFlowFile = null;
        }

        final SiteToSiteClient client = getSiteToSiteClient();
        final Transaction transaction;
        try {
            transaction = client.createTransaction(transferDirection);
        } catch (final PortNotRunningException e) {
            context.yield();
            this.targetRunning.set(false);
            final String message = String.format("%s failed to communicate with %s because the remote instance indicates that the port is not in a valid state", this, url);
            logger.error(message);
            session.rollback();
            remoteGroup.getEventReporter().reportEvent(Severity.ERROR, CATEGORY, message);
            return;
        } catch (final UnknownPortException e) {
            context.yield();
            this.targetExists.set(false);
            final String message = String.format("%s failed to communicate with %s because the remote instance indicates that the port no longer exists", this, url);
            logger.error(message);
            session.rollback();
            remoteGroup.getEventReporter().reportEvent(Severity.ERROR, CATEGORY, message);
            return;
        } catch (final IOException e) {
            // we do not yield here because the 'peer' will be penalized, and we won't communicate with that particular nifi instance
            // for a while due to penalization, but we can continue to talk to other nifi instances
            final String message = String.format("%s failed to communicate with %s due to %s", this, url, e.toString());
            logger.error(message);
            if (logger.isDebugEnabled()) {
                logger.error("", e);
            }
            session.rollback();
            remoteGroup.getEventReporter().reportEvent(Severity.ERROR, CATEGORY, message);
            return;
        }

        if (transaction == null) {
            logger.debug("{} Unable to create transaction to communicate with; all peers must be penalized, so yielding context", this);
            session.rollback();
            context.yield();
            return;
        }

        try {
            if (getConnectableType() == ConnectableType.REMOTE_INPUT_PORT) {
                transferFlowFiles(transaction, context, session, firstFlowFile);
            } else {
                final int numReceived = receiveFlowFiles(transaction, context, session);
                if (numReceived == 0) {
                    context.yield();
                }
            }

            session.commit();
        } catch (final Throwable t) {
            final String message = String.format("%s failed to communicate with remote NiFi instance due to %s", this, t.toString());
            logger.error("{} failed to communicate with remote NiFi instance due to {}", this, t.toString());
            if (logger.isDebugEnabled()) {
                logger.error("", t);
            }

            remoteGroup.getEventReporter().reportEvent(Severity.ERROR, CATEGORY, message);
            transaction.error();
            session.rollback();
        }
    }

    @Override
    public String getYieldPeriod() {
        // delegate yield duration to remote process group
        return remoteGroup.getYieldDuration();
    }

    private int transferFlowFiles(final Transaction transaction, final ProcessContext context, final ProcessSession session, final FlowFile firstFlowFile) throws IOException, ProtocolException {
        FlowFile flowFile = firstFlowFile;

        try {
            final String userDn = transaction.getCommunicant().getDistinguishedName();
            final long startSendingNanos = System.nanoTime();
            final StopWatch stopWatch = new StopWatch(true);
            long bytesSent = 0L;

            final Set<FlowFile> flowFilesSent = new HashSet<>();
            boolean continueTransaction = true;
            while (continueTransaction) {
                final long startNanos = System.nanoTime();
                // call codec.encode within a session callback so that we have the InputStream to read the FlowFile
                final FlowFile toWrap = flowFile;
                session.read(flowFile, new InputStreamCallback() {
                    @Override
                    public void process(final InputStream in) throws IOException {
                        final DataPacket dataPacket = new StandardDataPacket(toWrap.getAttributes(), in, toWrap.getSize());
                        transaction.send(dataPacket);
                    }
                });

                final long transferNanos = System.nanoTime() - startNanos;
                final long transferMillis = TimeUnit.MILLISECONDS.convert(transferNanos, TimeUnit.NANOSECONDS);

                flowFilesSent.add(flowFile);
                bytesSent += flowFile.getSize();
                logger.debug("{} Sent {} to {}", this, flowFile, transaction.getCommunicant().getUrl());

                final String transitUri = transaction.getCommunicant().createTransitUri(flowFile.getAttribute(CoreAttributes.UUID.key()));
                session.getProvenanceReporter().send(flowFile, transitUri, "Remote DN=" + userDn, transferMillis, false);
                session.remove(flowFile);

                final long sendingNanos = System.nanoTime() - startSendingNanos;
                if (sendingNanos < BATCH_SEND_NANOS) {
                    flowFile = session.get();
                } else {
                    flowFile = null;
                }

                continueTransaction = (flowFile != null);
            }

            transaction.confirm();

            // consume input stream entirely, ignoring its contents. If we
            // don't do this, the Connection will not be returned to the pool
            stopWatch.stop();
            final String uploadDataRate = stopWatch.calculateDataRate(bytesSent);
            final long uploadMillis = stopWatch.getDuration(TimeUnit.MILLISECONDS);
            final String dataSize = FormatUtils.formatDataSize(bytesSent);

            transaction.complete();
            session.commit();

            final String flowFileDescription = (flowFilesSent.size() < 20) ? flowFilesSent.toString() : flowFilesSent.size() + " FlowFiles";
            logger.info("{} Successfully sent {} ({}) to {} in {} milliseconds at a rate of {}", new Object[]{
                this, flowFileDescription, dataSize, transaction.getCommunicant().getUrl(), uploadMillis, uploadDataRate});

            return flowFilesSent.size();
        } catch (final Exception e) {
            session.rollback();
            throw e;
        }

    }

    private int receiveFlowFiles(final Transaction transaction, final ProcessContext context, final ProcessSession session) throws IOException, ProtocolException {
        final String userDn = transaction.getCommunicant().getDistinguishedName();

        final StopWatch stopWatch = new StopWatch(true);
        final Set<FlowFile> flowFilesReceived = new HashSet<>();
        long bytesReceived = 0L;

        while (true) {
            final long start = System.nanoTime();
            final DataPacket dataPacket = transaction.receive();
            if (dataPacket == null) {
                break;
            }

            FlowFile flowFile = session.create();
            flowFile = session.putAllAttributes(flowFile, dataPacket.getAttributes());
            flowFile = session.importFrom(dataPacket.getData(), flowFile);
            final long receiveNanos = System.nanoTime() - start;
            flowFilesReceived.add(flowFile);

            String sourceFlowFileIdentifier = dataPacket.getAttributes().get(CoreAttributes.UUID.key());
            if (sourceFlowFileIdentifier == null) {
                sourceFlowFileIdentifier = "<Unknown Identifier>";
            }

            final String transitUri = transaction.getCommunicant().createTransitUri(sourceFlowFileIdentifier);
            session.getProvenanceReporter().receive(flowFile, transitUri, "urn:nifi:" + sourceFlowFileIdentifier,
                    "Remote DN=" + userDn, TimeUnit.NANOSECONDS.toMillis(receiveNanos));

            session.transfer(flowFile, Relationship.ANONYMOUS);
            bytesReceived += dataPacket.getSize();
        }

        // Confirm that what we received was the correct data.
        transaction.confirm();

        // Commit the session so that we have persisted the data
        session.commit();

        transaction.complete();

        if (!flowFilesReceived.isEmpty()) {
            stopWatch.stop();
            final String flowFileDescription = flowFilesReceived.size() < 20 ? flowFilesReceived.toString() : flowFilesReceived.size() + " FlowFiles";
            final String uploadDataRate = stopWatch.calculateDataRate(bytesReceived);
            final long uploadMillis = stopWatch.getDuration(TimeUnit.MILLISECONDS);
            final String dataSize = FormatUtils.formatDataSize(bytesReceived);
            logger.info("{} Successfully receveied {} ({}) from {} in {} milliseconds at a rate of {}", new Object[]{
                this, flowFileDescription, dataSize, transaction.getCommunicant().getUrl(), uploadMillis, uploadDataRate});
        }

        return flowFilesReceived.size();
    }

    @Override
    public boolean getTargetExists() {
        return targetExists.get();
    }

    @Override
    public boolean isValid() {
        return getValidationErrors().isEmpty();
    }

    @Override
    public Collection<ValidationResult> getValidationErrors() {
        final Collection<ValidationResult> validationErrors = new ArrayList<>();
        ValidationResult error = null;
        if (!targetExists.get()) {
            error = new ValidationResult.Builder()
                    .explanation(String.format("Remote instance indicates that port '%s' no longer exists.", getName()))
                    .subject(String.format("Remote port '%s'", getName()))
                    .valid(false)
                    .build();
        } else if (getConnectableType() == ConnectableType.REMOTE_OUTPUT_PORT && getConnections(Relationship.ANONYMOUS).isEmpty()) {
            error = new ValidationResult.Builder()
                    .explanation(String.format("Port '%s' has no outbound connections", getName()))
                    .subject(String.format("Remote port '%s'", getName()))
                    .valid(false)
                    .build();
        }

        if (error != null) {
            validationErrors.add(error);
        }

        return validationErrors;
    }

    @Override
    public void verifyCanStart() {
        super.verifyCanStart();

        if (getConnectableType() == ConnectableType.REMOTE_INPUT_PORT && getIncomingConnections().isEmpty()) {
            throw new IllegalStateException("Port " + getName() + " has no incoming connections");
        }
    }

    @Override
    public void setUseCompression(final boolean useCompression) {
        this.useCompression.set(useCompression);
    }

    @Override
    public boolean isUseCompression() {
        return useCompression.get();
    }

    @Override
    public String toString() {
        return "RemoteGroupPort[name=" + getName() + ",target=" + remoteGroup.getTargetUri().toString() + "]";
    }

    @Override
    public RemoteProcessGroup getRemoteProcessGroup() {
        return remoteGroup;
    }

    @Override
    public TransferDirection getTransferDirection() {
        return (getConnectableType() == ConnectableType.REMOTE_INPUT_PORT) ? TransferDirection.SEND : TransferDirection.RECEIVE;
    }

    public void setTargetExists(final boolean exists) {
        this.targetExists.set(exists);
    }

    @Override
    public void removeConnection(final Connection connection) throws IllegalArgumentException, IllegalStateException {
        super.removeConnection(connection);

        // If the Port no longer exists on the remote instance and this is the last Connection, tell
        // RemoteProcessGroup to remove me
        if (!getTargetExists() && !hasIncomingConnection() && getConnections().isEmpty()) {
            remoteGroup.removeNonExistentPort(this);
        }
    }

    @Override
    public SchedulingStrategy getSchedulingStrategy() {
        return SchedulingStrategy.TIMER_DRIVEN;
    }

    @Override
    public boolean isSideEffectFree() {
        return false;
    }

    @Override
    public String getComponentType() {
        return "RemoteGroupPort";
    }
}
