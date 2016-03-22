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

import java.util.ArrayList;
import java.util.List;

import org.apache.nifi.flowfile.FlowFile;

/**
 * Context object that serves as a bridge between the content of a FlowFile and
 * Kafka message(s). It contains all necessary information to allow
 * {@link KafkaPublisher} to determine how a each content of the
 * {@link FlowFile} must be sent to Kafka.
 */
final class SplittableMessageContext {
    private final String topicName;

    private final String delimiterPattern;

    private final byte[] keyBytes;

    private volatile List<Integer> failedSegments;

    /**
     * @param topicName
     *            the name of the Kafka topic
     * @param keyBytes
     *            the instance of byte[] representing the key. Can be null.
     * @param delimiterPattern
     *            the string representing the delimiter regex pattern. Can be
     *            null. For cases where it is null the EOF pattern will be used
     *            - "(\\W)\\Z".
     */
    SplittableMessageContext(String topicName, byte[] keyBytes, String delimiterPattern) {
        this.topicName = topicName;
        this.keyBytes = keyBytes;
        this.delimiterPattern = delimiterPattern != null ? delimiterPattern : "(\\W)\\Z";
    }

    /**
     *
     */
    @Override
    public String toString() {
        return "topic: '" + topicName + "'; delimiter: '" + delimiterPattern + "'";
    }

    /**
     * Sets the comma delimited string of integers representing failed segment
     * indexes.
     */
    void setFailedSegmentsAsString(String failedSegmentsString) {
        if (failedSegmentsString != null) {
            List<Integer> failedSegments = new ArrayList<>();
            for (String segStrIndex : failedSegmentsString.split(",")) {
                failedSegments.add(Integer.parseInt(segStrIndex));
            }
            this.failedSegments = failedSegments;
        }
    }

    /**
     * Returns the list of integers representing the segments (chunks) of the
     * delimited content stream that had failed to be sent to Kafka topic.
     */
    List<Integer> getFailedSegments() {
        return failedSegments;
    }

    /**
     * Returns the name of the Kafka topic
     */
    String getTopicName() {
        return this.topicName;
    }

    /**
     * Returns the value of the delimiter regex pattern.
     */
    String getDelimiterPattern() {
        return this.delimiterPattern;
    }

    /**
     * Returns the key bytes as String
     */
    String getKeyBytesAsString() {
        return new String(this.keyBytes);
    }

    /**
     * Returns the key bytes
     */
    byte[] getKeyBytes() {
        return this.keyBytes;
    }
}
