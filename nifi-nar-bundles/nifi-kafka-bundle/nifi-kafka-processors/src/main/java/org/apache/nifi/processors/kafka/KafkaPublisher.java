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
package org.apache.nifi.processors.kafka;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.Partitioner;
import kafka.producer.ProducerConfig;

/**
 * Wrapper over {@link KafkaProducer} to assist {@link PutKafka} processor with
 * sending content of {@link FlowFile}s to Kafka.
 */
public class KafkaPublisher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisher.class);

    private final Producer<byte[], byte[]> producer;

    private ProcessorLog processLog;

    /**
     * Creates an instance of this class as well as the instance of the
     * corresponding Kafka {@link KafkaProducer} using provided Kafka
     * configuration properties.
     */
    KafkaPublisher(Properties kafkaProperties) {
        ProducerConfig producerConfig = new ProducerConfig(kafkaProperties);
        this.producer = new Producer<>(producerConfig);
    }

    /**
     *
     */
    void setProcessLog(ProcessorLog processLog) {
        this.processLog = processLog;
    }

    /**
     * Publishes messages to Kafka topic. It supports three publishing
     * mechanisms.
     * <ul>
     * <li>Sending the entire content stream as a single Kafka message.</li>
     * <li>Splitting the incoming content stream into chunks and sending
     * individual chunks as separate Kafka messages.</li>
     * <li>Splitting the incoming content stream into chunks and sending only
     * the chunks that have failed previously @see
     * {@link SplittableMessageContext#getFailedSegments()}.</li>
     * </ul>
     * This method assumes content stream affinity where it is expected that the
     * content stream that represents the same Kafka message(s) will remain the
     * same across possible retries. This is required specifically for cases
     * where delimiter is used and a single content stream may represent
     * multiple Kafka messages. The failed segment list will keep the index of
     * of each content stream segment that had failed to be sent to Kafka, so
     * upon retry only the failed segments are sent.
     *
     * @param messageContext
     *            instance of {@link SplittableMessageContext} which hold
     *            context information about the message to be sent
     * @param contentStream
     *            instance of open {@link InputStream} carrying the content of
     *            the message(s) to be send to Kafka
     * @param partitionKey
     *            the value of the partition key. Only relevant is user wishes
     *            to provide a custom partition key instead of relying on
     *            variety of provided {@link Partitioner}(s)
     * @return The list containing the failed segment indexes for messages that
     *         failed to be sent to Kafka.
     */
    List<Integer> publish(SplittableMessageContext messageContext, InputStream contentStream, Object partitionKey) {
        List<Integer> prevFailedSegmentIndexes = messageContext.getFailedSegments();
        List<Integer> failedSegments = new ArrayList<>();
        int segmentCounter = 0;
        try (Scanner scanner = new Scanner(contentStream)) {
            scanner.useDelimiter(messageContext.getDelimiterPattern());
            while (scanner.hasNext()) {
                //TODO Improve content InputStream so it's skip supported so one can zoom straight to the correct segment
                byte[] content = scanner.next().getBytes();
                if (content.length > 0){
                    byte[] key = messageContext.getKeyBytes();
                    partitionKey = partitionKey == null ? key : partitionKey;// the whole thing may still be null
                    String topicName = messageContext.getTopicName();
                    if (prevFailedSegmentIndexes != null) {
                        // send only what has failed
                        if (prevFailedSegmentIndexes.contains(segmentCounter)) {
                            KeyedMessage<byte[], byte[]> message = new KeyedMessage<byte[], byte[]>(topicName, key, partitionKey, content);
                            if (!this.toKafka(message)) {
                                failedSegments.add(segmentCounter);
                            }
                        }
                    } else {
                        KeyedMessage<byte[], byte[]> message = new KeyedMessage<byte[], byte[]>(topicName, key, partitionKey, content);
                        if (!this.toKafka(message)) {
                            failedSegments.add(segmentCounter);
                        }
                    }
                }
                segmentCounter++;
            }
        }
        return failedSegments;
    }

    /**
     * Closes {@link KafkaProducer}
     */
    @Override
    public void close() throws Exception {
        this.producer.close();
    }

    /**
     * Sends the provided {@link KeyedMessage} to Kafka returning true if
     * message was sent successfully and false otherwise.
     */
    private boolean toKafka(KeyedMessage<byte[], byte[]> message) {
        boolean sent = false;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Publishing message to '" + message.topic() + "' topic.");
            }
            this.producer.send(message);
            sent = true;
        } catch (Exception e) {
            logger.error("Failed to send message to Kafka", e);
            if (processLog != null) {
                processLog.error("Failed to send message to Kafka", e);
            }
        }
        return sent;
    }
}
