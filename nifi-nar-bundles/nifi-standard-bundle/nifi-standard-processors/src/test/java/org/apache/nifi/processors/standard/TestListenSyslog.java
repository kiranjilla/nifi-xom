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
package org.apache.nifi.processors.standard;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.exception.FlowFileAccessException;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processors.standard.syslog.SyslogAttributes;
import org.apache.nifi.processors.standard.syslog.SyslogEvent;
import org.apache.nifi.processors.standard.syslog.SyslogParser;
import org.apache.nifi.provenance.ProvenanceEventRecord;
import org.apache.nifi.provenance.ProvenanceEventType;
import org.apache.nifi.util.IntegerHolder;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestListenSyslog {

    static final Logger LOGGER = LoggerFactory.getLogger(TestListenSyslog.class);

    static final String PRI = "34";
    static final String SEV = "2";
    static final String FAC = "4";
    static final String TIME = "Oct 13 15:43:23";
    static final String HOST = "localhost.home";
    static final String BODY = "some message";

    static final String VALID_MESSAGE = "<" + PRI + ">" + TIME + " " + HOST + " " + BODY + "\n";
    static final String INVALID_MESSAGE = "this is not valid\n";

    @Test
    public void testUDP() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.UDP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        final int numMessages = 20;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // write some UDP messages to the port in the background
        final Thread sender = new Thread(new DatagramSender(port, numMessages, 10, VALID_MESSAGE));
        sender.setDaemon(true);
        sender.start();

        // call onTrigger until we read all datagrams, or 30 seconds passed
        try {
            int numTransfered = 0;
            long timeout = System.currentTimeMillis() + 30000;

            while (numTransfered < numMessages && System.currentTimeMillis() < timeout) {
                Thread.sleep(10);
                proc.onTrigger(context, processSessionFactory);
                numTransfered = runner.getFlowFilesForRelationship(ListenUDP.RELATIONSHIP_SUCCESS).size();
            }
            Assert.assertEquals("Did not process all the datagrams", numMessages, numTransfered);

            MockFlowFile flowFile = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0);
            checkFlowFile(flowFile, 0, ListenSyslog.UDP_VALUE.getValue());

            final List<ProvenanceEventRecord> events = runner.getProvenanceEvents();
            Assert.assertNotNull(events);
            Assert.assertEquals(numMessages, events.size());

            final ProvenanceEventRecord event = events.get(0);
            Assert.assertEquals(ProvenanceEventType.RECEIVE, event.getEventType());
            Assert.assertTrue("transit uri must be set and start with proper protocol", event.getTransitUri().toLowerCase().startsWith("udp"));

        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testTCPSingleConnection() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.TCP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        // Allow time for the processor to perform its scheduled start
        Thread.sleep(500);

        final int numMessages = 20;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // write some TCP messages to the port in the background
        final Thread sender = new Thread(new SingleConnectionSocketSender(port, numMessages, 10, VALID_MESSAGE));
        sender.setDaemon(true);
        sender.start();

        // call onTrigger until we read all messages, or 30 seconds passed
        try {
            int numTransfered = 0;
            long timeout = System.currentTimeMillis() + 30000;

            while (numTransfered < numMessages && System.currentTimeMillis() < timeout) {
                Thread.sleep(10);
                proc.onTrigger(context, processSessionFactory);
                numTransfered = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).size();
            }
            Assert.assertEquals("Did not process all the messages", numMessages, numTransfered);

            MockFlowFile flowFile = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0);
            checkFlowFile(flowFile, 0, ListenSyslog.TCP_VALUE.getValue());

            final List<ProvenanceEventRecord> events = runner.getProvenanceEvents();
            Assert.assertNotNull(events);
            Assert.assertEquals(numMessages, events.size());

            final ProvenanceEventRecord event = events.get(0);
            Assert.assertEquals(ProvenanceEventType.RECEIVE, event.getEventType());
            Assert.assertTrue("transit uri must be set and start with proper protocol", event.getTransitUri().toLowerCase().startsWith("tcp"));

        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testTCPSingleConnectionWithNewLines() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.TCP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        final int numMessages = 3;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // send 3 messages as 1
        final String multipleMessages = VALID_MESSAGE + "\n" + VALID_MESSAGE + "\n" + VALID_MESSAGE;
        final Thread sender = new Thread(new SingleConnectionSocketSender(port, 1, 10, multipleMessages));
        sender.setDaemon(true);
        sender.start();

        // call onTrigger until we read all messages, or 30 seconds passed
        try {
            int numTransfered = 0;
            long timeout = System.currentTimeMillis() + 30000;

            while (numTransfered < numMessages && System.currentTimeMillis() < timeout) {
                Thread.sleep(10);
                proc.onTrigger(context, processSessionFactory);
                numTransfered = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).size();
            }
            Assert.assertEquals("Did not process all the messages", numMessages, numTransfered);

            MockFlowFile flowFile = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0);
            checkFlowFile(flowFile, 0, ListenSyslog.TCP_VALUE.getValue());

            final List<ProvenanceEventRecord> events = runner.getProvenanceEvents();
            Assert.assertNotNull(events);
            Assert.assertEquals(numMessages, events.size());

            final ProvenanceEventRecord event = events.get(0);
            Assert.assertEquals(ProvenanceEventType.RECEIVE, event.getEventType());
            Assert.assertTrue("transit uri must be set and start with proper protocol", event.getTransitUri().toLowerCase().startsWith("tcp"));

        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testTCPMultipleConnection() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.TCP_VALUE.getValue());
        runner.setProperty(ListenSyslog.MAX_CONNECTIONS, "5");
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        final int numMessages = 20;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // write some TCP messages to the port in the background
        final Thread sender = new Thread(new MultiConnectionSocketSender(port, numMessages, 10, VALID_MESSAGE));
        sender.setDaemon(true);
        sender.start();

        // call onTrigger until we read all messages, or 30 seconds passed
        try {
            int numTransfered = 0;
            long timeout = System.currentTimeMillis() + 30000;

            while (numTransfered < numMessages && System.currentTimeMillis() < timeout) {
                Thread.sleep(10);
                proc.onTrigger(context, processSessionFactory);
                numTransfered = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).size();
            }
            Assert.assertEquals("Did not process all the messages", numMessages, numTransfered);

            MockFlowFile flowFile = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0);
            checkFlowFile(flowFile, 0, ListenSyslog.TCP_VALUE.getValue());

            final List<ProvenanceEventRecord> events = runner.getProvenanceEvents();
            Assert.assertNotNull(events);
            Assert.assertEquals(numMessages, events.size());

            final ProvenanceEventRecord event = events.get(0);
            Assert.assertEquals(ProvenanceEventType.RECEIVE, event.getEventType());
            Assert.assertTrue("transit uri must be set and start with proper protocol", event.getTransitUri().toLowerCase().startsWith("tcp"));

        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testBatching() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.UDP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");
        runner.setProperty(ListenSyslog.MAX_BATCH_SIZE, "25");
        runner.setProperty(ListenSyslog.MESSAGE_DELIMITER, "|");
        runner.setProperty(ListenSyslog.PARSE_MESSAGES, "false");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        // the processor has internal blocking queue with capacity 10 so we have to send
        // less than that since we are sending all messages before the processors ever runs
        final int numMessages = 5;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // write some UDP messages to the port in the background
        final Thread sender = new Thread(new DatagramSender(port, numMessages, 10, VALID_MESSAGE.replaceAll("\\n", "")));
        sender.setDaemon(true);
        sender.start();
        sender.join();

        try {
            proc.onTrigger(context, processSessionFactory);
            runner.assertAllFlowFilesTransferred(ListenSyslog.REL_SUCCESS, 1);

            final MockFlowFile flowFile = runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0);
            Assert.assertEquals("0", flowFile.getAttribute(SyslogAttributes.PORT.key()));
            Assert.assertEquals(ListenSyslog.UDP_VALUE.getValue(), flowFile.getAttribute(SyslogAttributes.PROTOCOL.key()));
            Assert.assertTrue(!StringUtils.isBlank(flowFile.getAttribute(SyslogAttributes.SENDER.key())));

            final String content = new String(flowFile.toByteArray(), StandardCharsets.UTF_8);
            final String[] splits = content.split("\\|");
            Assert.assertEquals(numMessages, splits.length);

            final List<ProvenanceEventRecord> events = runner.getProvenanceEvents();
            Assert.assertNotNull(events);
            Assert.assertEquals(1, events.size());

            final ProvenanceEventRecord event = events.get(0);
            Assert.assertEquals(ProvenanceEventType.RECEIVE, event.getEventType());
            Assert.assertTrue("transit uri must be set and start with proper protocol", event.getTransitUri().toLowerCase().startsWith("udp"));
        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testInvalid() throws IOException, InterruptedException {
        final ListenSyslog proc = new ListenSyslog();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.TCP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        final int numMessages = 10;
        final int port = proc.getPort();
        Assert.assertTrue(port > 0);

        // write some TCP messages to the port in the background
        final Thread sender = new Thread(new SingleConnectionSocketSender(port, numMessages, 100, INVALID_MESSAGE));
        sender.setDaemon(true);
        sender.start();

        // call onTrigger until we read all messages, or 30 seconds passed
        try {
            int numTransfered = 0;
            long timeout = System.currentTimeMillis() + 30000;

            while (numTransfered < numMessages && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
                proc.onTrigger(context, processSessionFactory);
                numTransfered = runner.getFlowFilesForRelationship(ListenSyslog.REL_INVALID).size();
            }

            // all messages should be transferred to invalid
            Assert.assertEquals("Did not process all the messages", numMessages, numTransfered);

        } finally {
            // unschedule to close connections
            proc.onUnscheduled();
        }
    }

    @Test
    public void testParsingError() throws IOException {
        final FailParseProcessor proc = new FailParseProcessor();
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.UDP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");

        // schedule to start listening on a random port
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();
        proc.onScheduled(context);

        try {
            final int port = proc.getPort();
            final DatagramSender sender = new DatagramSender(port, 1, 1, INVALID_MESSAGE);
            sender.run();

            // should keep re-processing event1 from the error queue
            proc.onTrigger(context, processSessionFactory);
            runner.assertTransferCount(ListenSyslog.REL_INVALID, 1);
            runner.assertTransferCount(ListenSyslog.REL_SUCCESS, 0);
        } finally {
            proc.onUnscheduled();
        }
    }

    @Test
    public void testErrorQueue() throws IOException {
        final List<ListenSyslog.RawSyslogEvent> msgs = new ArrayList<>();
        msgs.add(new ListenSyslog.RawSyslogEvent(VALID_MESSAGE.getBytes(), "sender-01"));
        msgs.add(new ListenSyslog.RawSyslogEvent(VALID_MESSAGE.getBytes(), "sender-01"));

        // Add message that will throw a FlowFileAccessException the first time that we attempt to read
        // the contents but will succeed the second time.
        final IntegerHolder getMessageAttempts = new IntegerHolder(0);
        msgs.add(new ListenSyslog.RawSyslogEvent(VALID_MESSAGE.getBytes(), "sender-01") {
            @Override
            public byte[] getData() {
                final int attempts = getMessageAttempts.incrementAndGet();
                if (attempts == 1) {
                    throw new FlowFileAccessException("Unit test failure");
                } else {
                    return VALID_MESSAGE.getBytes();
                }
            }
        });

        final CannedMessageProcessor proc = new CannedMessageProcessor(msgs);
        final TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(ListenSyslog.MAX_BATCH_SIZE, "5");
        runner.setProperty(ListenSyslog.PROTOCOL, ListenSyslog.UDP_VALUE.getValue());
        runner.setProperty(ListenSyslog.PORT, "0");
        runner.setProperty(ListenSyslog.PARSE_MESSAGES, "false");

        runner.run();
        assertEquals(1, proc.getErrorQueueSize());
        runner.assertAllFlowFilesTransferred(ListenSyslog.REL_SUCCESS, 1);
        runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0).assertContentEquals(VALID_MESSAGE + "\n" + VALID_MESSAGE);

        // running again should pull from the error queue
        runner.clearTransferState();
        runner.run();
        runner.assertAllFlowFilesTransferred(ListenSyslog.REL_SUCCESS, 1);
        runner.getFlowFilesForRelationship(ListenSyslog.REL_SUCCESS).get(0).assertContentEquals(VALID_MESSAGE);
    }


    private void checkFlowFile(final MockFlowFile flowFile, final int port, final String protocol) {
        flowFile.assertContentEquals(VALID_MESSAGE);
        Assert.assertEquals(PRI, flowFile.getAttribute(SyslogAttributes.PRIORITY.key()));
        Assert.assertEquals(SEV, flowFile.getAttribute(SyslogAttributes.SEVERITY.key()));
        Assert.assertEquals(FAC, flowFile.getAttribute(SyslogAttributes.FACILITY.key()));
        Assert.assertEquals(TIME, flowFile.getAttribute(SyslogAttributes.TIMESTAMP.key()));
        Assert.assertEquals(HOST, flowFile.getAttribute(SyslogAttributes.HOSTNAME.key()));
        Assert.assertEquals(BODY, flowFile.getAttribute(SyslogAttributes.BODY.key()));
        Assert.assertEquals("true", flowFile.getAttribute(SyslogAttributes.VALID.key()));
        Assert.assertEquals(String.valueOf(port), flowFile.getAttribute(SyslogAttributes.PORT.key()));
        Assert.assertEquals(protocol, flowFile.getAttribute(SyslogAttributes.PROTOCOL.key()));
        Assert.assertTrue(!StringUtils.isBlank(flowFile.getAttribute(SyslogAttributes.SENDER.key())));
    }

    /**
     * Sends a given number of datagrams to the given port.
     */
    public static final class DatagramSender implements Runnable {

        final int port;
        final int numMessages;
        final long delay;
        final String message;

        public DatagramSender(int port, int numMessages, long delay, String message) {
            this.port = port;
            this.numMessages = numMessages;
            this.delay = delay;
            this.message = message;
        }

        @Override
        public void run() {
            byte[] bytes = message.getBytes(Charset.forName("UTF-8"));
            final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            try (DatagramChannel channel = DatagramChannel.open()) {
                channel.connect(new InetSocketAddress("localhost", port));
                for (int i=0; i < numMessages; i++) {
                    buffer.clear();
                    buffer.put(bytes);
                    buffer.flip();

                    while(buffer.hasRemaining()) {
                        channel.write(buffer);
                    }

                    Thread.sleep(delay);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Sends a given number of datagrams to the given port.
     */
    public static final class SingleConnectionSocketSender implements Runnable {

        final int port;
        final int numMessages;
        final long delay;
        final String message;

        public SingleConnectionSocketSender(int port, int numMessages, long delay, String message) {
            this.port = port;
            this.numMessages = numMessages;
            this.delay = delay;
            this.message = message;
        }

        @Override
        public void run() {
            byte[] bytes = message.getBytes(Charset.forName("UTF-8"));
            final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            try (SocketChannel channel = SocketChannel.open()) {
                channel.connect(new InetSocketAddress("localhost", port));

                for (int i=0; i < numMessages; i++) {
                    buffer.clear();
                    buffer.put(bytes);
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    Thread.sleep(delay);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Sends a given number of datagrams to the given port.
     */
    public static final class MultiConnectionSocketSender implements Runnable {

        final int port;
        final int numMessages;
        final long delay;
        final String message;

        public MultiConnectionSocketSender(int port, int numMessages, long delay, String message) {
            this.port = port;
            this.numMessages = numMessages;
            this.delay = delay;
            this.message = message;
        }

        @Override
        public void run() {
            byte[] bytes = message.getBytes(Charset.forName("UTF-8"));
            final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            for (int i=0; i < numMessages; i++) {
                try (SocketChannel channel = SocketChannel.open()) {
                    channel.connect(new InetSocketAddress("localhost", port));

                    buffer.clear();
                    buffer.put(bytes);
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    Thread.sleep(delay);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    // A mock version of ListenSyslog that will queue the provided events
    private static class FailParseProcessor extends ListenSyslog {
        @Override
        protected SyslogParser getParser() {
            return new SyslogParser(StandardCharsets.UTF_8) {
                @Override
                public SyslogEvent parseEvent(byte[] bytes, String sender) {
                    throw new ProcessException("Unit test intentionally failing");
                }
            };
        }
    }

    private static class CannedMessageProcessor extends ListenSyslog {
        private final Iterator<RawSyslogEvent> eventItr;

        public CannedMessageProcessor(final List<RawSyslogEvent> events) {
            this.eventItr = events.iterator();
        }

        @Override
        public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
            final List<PropertyDescriptor> properties = new ArrayList<>(super.getSupportedPropertyDescriptors());
            properties.remove(PORT);
            properties.add(new PropertyDescriptor.Builder().name(PORT.getName()).addValidator(Validator.VALID).build());
            return properties;
        }

        @Override
        protected RawSyslogEvent getMessage(final boolean longPoll, final boolean pollErrorQueue, final ProcessSession session) {
            if (eventItr.hasNext()) {
                return eventItr.next();
            }
            return super.getMessage(longPoll, pollErrorQueue, session);
        }
    }
}
