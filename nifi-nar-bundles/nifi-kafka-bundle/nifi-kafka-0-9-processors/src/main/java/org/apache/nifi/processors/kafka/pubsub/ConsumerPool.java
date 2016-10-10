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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.nifi.logging.ComponentLog;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.common.KafkaException;
import org.apache.nifi.processor.ProcessSession;

/**
 * A pool of Kafka Consumers for a given topic. Consumers can be obtained by
 * calling 'obtainConsumer'. Once closed the pool is ready to be immediately
 * used again.
 */
public class ConsumerPool implements Closeable {

    private final BlockingQueue<SimpleConsumerLease> pooledLeases;
    private final List<String> topics;
    private final Map<String, Object> kafkaProperties;
    private final long maxWaitMillis;
    private final ComponentLog logger;
    private final byte[] demarcatorBytes;
    private final String keyEncoding;
    private final String securityProtocol;
    private final String bootstrapServers;
    private final AtomicLong consumerCreatedCountRef = new AtomicLong();
    private final AtomicLong consumerClosedCountRef = new AtomicLong();
    private final AtomicLong leasesObtainedCountRef = new AtomicLong();

    /**
     * Creates a pool of KafkaConsumer objects that will grow up to the maximum
     * indicated threads from the given context. Consumers are lazily
     * initialized. We may elect to not create up to the maximum number of
     * configured consumers if the broker reported lag time for all topics is
     * below a certain threshold.
     *
     * @param maxConcurrentLeases max allowable consumers at once
     * @param demarcator bytes to use as demarcator between messages; null or
     * empty means no demarcator
     * @param kafkaProperties properties to use to initialize kafka consumers
     * @param topics the topics to subscribe to
     * @param maxWaitMillis maximum time to wait for a given lease to acquire
     * data before committing
     * @param keyEncoding the encoding to use for the key of a kafka message if
     * found
     * @param securityProtocol the security protocol used
     * @param bootstrapServers the bootstrap servers
     * @param logger the logger to report any errors/warnings
     */
    public ConsumerPool(
            final int maxConcurrentLeases,
            final byte[] demarcator,
            final Map<String, String> kafkaProperties,
            final List<String> topics,
            final long maxWaitMillis,
            final String keyEncoding,
            final String securityProtocol,
            final String bootstrapServers,
            final ComponentLog logger) {
        this.pooledLeases = new ArrayBlockingQueue<>(maxConcurrentLeases);
        this.maxWaitMillis = maxWaitMillis;
        this.logger = logger;
        this.demarcatorBytes = demarcator;
        this.keyEncoding = keyEncoding;
        this.securityProtocol = securityProtocol;
        this.bootstrapServers = bootstrapServers;
        this.kafkaProperties = Collections.unmodifiableMap(kafkaProperties);
        this.topics = Collections.unmodifiableList(topics);
    }

    /**
     * Obtains a consumer from the pool if one is available or lazily
     * initializes a new one if deemed necessary.
     *
     * @param session the session for which the consumer lease will be
     * associated
     * @return consumer to use or null if not available or necessary
     */
    public ConsumerLease obtainConsumer(final ProcessSession session) {
        SimpleConsumerLease lease = pooledLeases.poll();
        if (lease == null) {
            final Consumer<byte[], byte[]> consumer = createKafkaConsumer();
            consumerCreatedCountRef.incrementAndGet();
            /**
             * For now return a new consumer lease. But we could later elect to
             * have this return null if we determine the broker indicates that
             * the lag time on all topics being monitored is sufficiently low.
             * For now we should encourage conservative use of threads because
             * having too many means we'll have at best useless threads sitting
             * around doing frequent network calls and at worst having consumers
             * sitting idle which could prompt excessive rebalances.
             */
            lease = new SimpleConsumerLease(consumer);
            /**
             * This subscription tightly couples the lease to the given
             * consumer. They cannot be separated from then on.
             */
            consumer.subscribe(topics, lease);
        }
        lease.setProcessSession(session);
        leasesObtainedCountRef.incrementAndGet();
        return lease;
    }

    /**
     * Exposed as protected method for easier unit testing
     *
     * @return consumer
     * @throws KafkaException if unable to subscribe to the given topics
     */
    protected Consumer<byte[], byte[]> createKafkaConsumer() {
        return new KafkaConsumer<>(kafkaProperties);
    }

    /**
     * Closes all consumers in the pool. Can be safely called repeatedly.
     */
    @Override
    public void close() {
        final List<SimpleConsumerLease> leases = new ArrayList<>();
        pooledLeases.drainTo(leases);
        leases.stream().forEach((lease) -> {
            lease.close(true);
        });
    }

    private void closeConsumer(final Consumer consumer) {
        consumerClosedCountRef.incrementAndGet();
        try {
            consumer.unsubscribe();
        } catch (Exception e) {
            logger.warn("Failed while unsubscribing " + consumer, e);
        }

        try {
            consumer.close();
        } catch (Exception e) {
            logger.warn("Failed while closing " + consumer, e);
        }
    }

    PoolStats getPoolStats() {
        return new PoolStats(consumerCreatedCountRef.get(), consumerClosedCountRef.get(), leasesObtainedCountRef.get());
    }

    private class SimpleConsumerLease extends ConsumerLease {

        private final Consumer<byte[], byte[]> consumer;
        private volatile ProcessSession session;
        private volatile boolean closedConsumer;

        private SimpleConsumerLease(final Consumer<byte[], byte[]> consumer) {
            super(maxWaitMillis, consumer, demarcatorBytes, keyEncoding, securityProtocol, bootstrapServers, logger);
            this.consumer = consumer;
        }

        void setProcessSession(final ProcessSession session) {
            this.session = session;
        }

        @Override
        public ProcessSession getProcessSession() {
            return session;
        }

        @Override
        public void close() {
            super.close();
            close(false);
        }

        public void close(final boolean forceClose) {
            if (closedConsumer) {
                return;
            }
            super.close();
            if (session != null) {
                session.rollback();
                setProcessSession(null);
            }
            if (forceClose || isPoisoned() || !pooledLeases.offer(this)) {
                closedConsumer = true;
                closeConsumer(consumer);
            }
        }
    }

    static final class PoolStats {

        final long consumerCreatedCount;
        final long consumerClosedCount;
        final long leasesObtainedCount;

        PoolStats(
                final long consumerCreatedCount,
                final long consumerClosedCount,
                final long leasesObtainedCount
        ) {
            this.consumerCreatedCount = consumerCreatedCount;
            this.consumerClosedCount = consumerClosedCount;
            this.leasesObtainedCount = leasesObtainedCount;
        }

        @Override
        public String toString() {
            return "Created Consumers [" + consumerCreatedCount + "]\n"
                    + "Closed Consumers  [" + consumerClosedCount + "]\n"
                    + "Leases Obtained   [" + leasesObtainedCount + "]\n";
        }

    }

}
