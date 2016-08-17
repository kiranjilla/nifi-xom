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
package org.apache.nifi.processors.kafka.pubsub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Test;
import org.mockito.Mockito;

// The test is valid and should be ran when working on this module. @Ignore is
// to speed up the overall build
public class PublishKafkaTest {

    @Test
    public void validateCustomSerilaizerDeserializerSettings() throws Exception {
        PublishKafka publishKafka = new PublishKafka();
        TestRunner runner = TestRunners.newTestRunner(publishKafka);
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "okeydokey:1234");
        runner.setProperty(PublishKafka.TOPIC, "foo");
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "3 sec");
        runner.setProperty("key.serializer", ByteArraySerializer.class.getName());
        runner.assertValid();
        runner.setProperty("key.serializer", "Foo");
        runner.assertNotValid();
    }

    @Test
    public void validatePropertiesValidation() throws Exception {
        PublishKafka publishKafka = new PublishKafka();
        TestRunner runner = TestRunners.newTestRunner(publishKafka);
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "okeydokey:1234");
        runner.setProperty(PublishKafka.TOPIC, "foo");
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "foo");

        try {
            runner.assertValid();
            fail();
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("'max.block.ms' validated against 'foo' is invalid"));
        }
    }

    @Test
    public void validateCustomValidation() {
        String topicName = "validateCustomValidation";
        PublishKafka publishKafka = new PublishKafka();

        /*
         * Validates that Kerberos principle is required if one of SASL set for
         * secirity protocol
         */
        TestRunner runner = TestRunners.newTestRunner(publishKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.SECURITY_PROTOCOL, PublishKafka.SEC_SASL_PLAINTEXT);
        try {
            runner.run();
            fail();
        } catch (Throwable e) {
            assertTrue(e.getMessage().contains("'Kerberos Service Name' is invalid because"));
        }
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateSingleCharacterDemarcatedMessages() {
        String topicName = "validateSingleCharacterDemarcatedMessages";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");

        runner.enqueue("Hello World\nGoodbye\n1\n2\n3\n4\n5".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(7)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateMultiCharacterDemarcatedMessagesAndCustomPartitionerA() {
        String topicName = "validateMultiCharacterDemarcatedMessagesAndCustomPartitioner";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.PARTITION_CLASS, Partitioners.RoundRobinPartitioner.class.getName());
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "foo");

        runner.enqueue("Hello WorldfooGoodbyefoo1foo2foo3foo4foo5".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(7)).send(Mockito.any(ProducerRecord.class));

        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateMultiCharacterDemarcatedMessagesAndCustomPartitionerB() {
        String topicName = "validateMultiCharacterDemarcatedMessagesAndCustomPartitioner";
        StubPublishKafka putKafka = new StubPublishKafka(1);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.PARTITION_CLASS, Partitioners.RoundRobinPartitioner.class.getName());
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "foo");

        runner.enqueue("Hello WorldfooGoodbyefoo1foo2foo3foo4foo5".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(7)).send(Mockito.any(ProducerRecord.class));

        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateOnSendFailureAndThenResendSuccessA() throws Exception {
        String topicName = "validateSendFailureAndThenResendSuccess";
        StubPublishKafka putKafka = new StubPublishKafka(100);

        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "3000 millis");

        final String text = "Hello World\nGoodbye\nfail\n2";
        runner.enqueue(text.getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        assertEquals(1, runner.getQueueSize().getObjectCount()); // due to failure
        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(4)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
        putKafka.destroy();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateOnSendFailureAndThenResendSuccessB() throws Exception {
        String topicName = "validateSendFailureAndThenResendSuccess";
        StubPublishKafka putKafka = new StubPublishKafka(1);

        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "500 millis");

        final String text = "Hello World\nGoodbye\nfail\n2";
        runner.enqueue(text.getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        assertEquals(1, runner.getQueueSize().getObjectCount()); // due to failure
        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(4)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateOnFutureGetFailureAndThenResendSuccessFirstMessageFail() throws Exception {
        String topicName = "validateSendFailureAndThenResendSuccess";
        StubPublishKafka putKafka = new StubPublishKafka(100);

        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "500 millis");

        final String text = "futurefail\nHello World\nGoodbye\n2";
        runner.enqueue(text.getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        MockFlowFile ff = runner.getFlowFilesForRelationship(PublishKafka.REL_FAILURE).get(0);
        assertNotNull(ff);
        runner.enqueue(ff);

        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        // 6 sends due to duplication
        verify(producer, times(5)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateOnFutureGetFailureAndThenResendSuccess() throws Exception {
        String topicName = "validateSendFailureAndThenResendSuccess";
        StubPublishKafka putKafka = new StubPublishKafka(100);

        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");
        runner.setProperty(PublishKafka.META_WAIT_TIME, "500 millis");

        final String text = "Hello World\nGoodbye\nfuturefail\n2";
        runner.enqueue(text.getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);
        MockFlowFile ff = runner.getFlowFilesForRelationship(PublishKafka.REL_FAILURE).get(0);
        assertNotNull(ff);
        runner.enqueue(ff);

        runner.run(1, false);
        assertEquals(0, runner.getQueueSize().getObjectCount());
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        // 6 sends due to duplication
        verify(producer, times(6)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateDemarcationIntoEmptyMessages() {
        String topicName = "validateDemarcationIntoEmptyMessages";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        final TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.KEY, "key1");
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "\n");

        final byte[] bytes = "\n\n\n1\n2\n\n\n\n3\n4\n\n\n".getBytes(StandardCharsets.UTF_8);
        runner.enqueue(bytes);
        runner.run(1);
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(4)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateComplexRightPartialDemarcatedMessages() {
        String topicName = "validateComplexRightPartialDemarcatedMessages";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "僠<僠WILDSTUFF僠>僠");

        runner.enqueue("Hello World僠<僠WILDSTUFF僠>僠Goodbye僠<僠WILDSTUFF僠>僠I Mean IT!僠<僠WILDSTUFF僠>".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);

        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(3)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateComplexLeftPartialDemarcatedMessages() {
        String topicName = "validateComplexLeftPartialDemarcatedMessages";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "僠<僠WILDSTUFF僠>僠");

        runner.enqueue("Hello World僠<僠WILDSTUFF僠>僠Goodbye僠<僠WILDSTUFF僠>僠I Mean IT!僠<僠WILDSTUFF僠>僠<僠WILDSTUFF僠>僠".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);

        runner.assertAllFlowFilesTransferred(PublishKafka.REL_SUCCESS, 1);
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(4)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void validateComplexPartialMatchDemarcatedMessages() {
        String topicName = "validateComplexPartialMatchDemarcatedMessages";
        StubPublishKafka putKafka = new StubPublishKafka(100);
        TestRunner runner = TestRunners.newTestRunner(putKafka);
        runner.setProperty(PublishKafka.TOPIC, topicName);
        runner.setProperty(PublishKafka.CLIENT_ID, "foo");
        runner.setProperty(PublishKafka.BOOTSTRAP_SERVERS, "localhost:1234");
        runner.setProperty(PublishKafka.MESSAGE_DEMARCATOR, "僠<僠WILDSTUFF僠>僠");

        runner.enqueue("Hello World僠<僠WILDSTUFF僠>僠Goodbye僠<僠WILDBOOMSTUFF僠>僠".getBytes(StandardCharsets.UTF_8));
        runner.run(1, false);

        runner.assertAllFlowFilesTransferred(PublishKafka.REL_SUCCESS, 1);
        Producer<byte[], byte[]> producer = putKafka.getProducer();
        verify(producer, times(2)).send(Mockito.any(ProducerRecord.class));
        runner.shutdown();
    }
}
