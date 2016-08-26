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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.kafka.clients.CommonClientConfigs;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.ssl.SSLContextService;
import org.apache.nifi.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaProcessorUtils {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String SINGLE_BROKER_REGEX = ".*?\\:\\d{3,5}";

    private static final String BROKER_REGEX = SINGLE_BROKER_REGEX + "(?:,\\s*" + SINGLE_BROKER_REGEX + ")*";

    static final Pattern HEX_KEY_PATTERN = Pattern.compile("(?:[0123456789abcdefABCDEF]{2})+");

    static final String KAFKA_KEY = "kafka.key";
    static final String KAFKA_TOPIC = "kafka.topic";
    static final String KAFKA_PARTITION = "kafka.partition";
    static final String KAFKA_OFFSET = "kafka.offset";
    static final String KAFKA_COUNT = "kafka.count";
    static final AllowableValue SEC_PLAINTEXT = new AllowableValue("PLAINTEXT", "PLAINTEXT", "PLAINTEXT");
    static final AllowableValue SEC_SSL = new AllowableValue("SSL", "SSL", "SSL");
    static final AllowableValue SEC_SASL_PLAINTEXT = new AllowableValue("SASL_PLAINTEXT", "SASL_PLAINTEXT", "SASL_PLAINTEXT");
    static final AllowableValue SEC_SASL_SSL = new AllowableValue("SASL_SSL", "SASL_SSL", "SASL_SSL");

    static final PropertyDescriptor BOOTSTRAP_SERVERS = new PropertyDescriptor.Builder()
            .name(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
            .displayName("Kafka Brokers")
            .description("A comma-separated list of known Kafka Brokers in the format <host>:<port>")
            .required(true)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile(BROKER_REGEX)))
            .expressionLanguageSupported(true)
            .defaultValue("localhost:9092")
            .build();
    static final PropertyDescriptor SECURITY_PROTOCOL = new PropertyDescriptor.Builder()
            .name("security.protocol")
            .displayName("Security Protocol")
            .description("Protocol used to communicate with brokers. Corresponds to Kafka's 'security.protocol' property.")
            .required(true)
            .expressionLanguageSupported(false)
            .allowableValues(SEC_PLAINTEXT, SEC_SSL, SEC_SASL_PLAINTEXT, SEC_SASL_SSL)
            .defaultValue(SEC_PLAINTEXT.getValue())
            .build();
    static final PropertyDescriptor KERBEROS_PRINCIPLE = new PropertyDescriptor.Builder()
            .name("sasl.kerberos.service.name")
            .displayName("Kerberos Service Name")
            .description("The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's JAAS config or in Kafka's config. "
                    + "Corresponds to Kafka's 'security.protocol' property."
                    + "It is ignored unless one of the SASL options of the <Security Protocol> are selected.")
            .required(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .expressionLanguageSupported(false)
            .build();
    static final PropertyDescriptor SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
            .name("ssl.context.service")
            .displayName("SSL Context Service")
            .description("Specifies the SSL Context Service to use for communicating with Kafka.")
            .required(false)
            .identifiesControllerService(SSLContextService.class)
            .build();

    static List<PropertyDescriptor> getCommonPropertyDescriptors() {
        return Arrays.asList(
                BOOTSTRAP_SERVERS,
                SECURITY_PROTOCOL,
                KERBEROS_PRINCIPLE,
                SSL_CONTEXT_SERVICE
        );
    }

    static Collection<ValidationResult> validateCommonProperties(final ValidationContext validationContext) {
        List<ValidationResult> results = new ArrayList<>();

        String securityProtocol = validationContext.getProperty(SECURITY_PROTOCOL).getValue();

        /*
         * validates that if one of SASL (Kerberos) option is selected for
         * security protocol, then Kerberos principal is provided as well
         */
        if (SEC_SASL_PLAINTEXT.getValue().equals(securityProtocol) || SEC_SASL_SSL.getValue().equals(securityProtocol)) {
            String kerberosPrincipal = validationContext.getProperty(KERBEROS_PRINCIPLE).getValue();
            if (kerberosPrincipal == null || kerberosPrincipal.trim().length() == 0) {
                results.add(new ValidationResult.Builder().subject(KERBEROS_PRINCIPLE.getDisplayName()).valid(false)
                        .explanation("The <" + KERBEROS_PRINCIPLE.getDisplayName() + "> property must be set when <"
                                + SECURITY_PROTOCOL.getDisplayName() + "> is configured as '"
                                + SEC_SASL_PLAINTEXT.getValue() + "' or '" + SEC_SASL_SSL.getValue() + "'.")
                        .build());
            }
        }

        //If SSL or SASL_SSL then CS must be set.
        final boolean sslProtocol = SEC_SSL.getValue().equals(securityProtocol) || SEC_SASL_SSL.getValue().equals(securityProtocol);
        final boolean csSet = validationContext.getProperty(SSL_CONTEXT_SERVICE).isSet();
        if (csSet && !sslProtocol) {
            results.add(new ValidationResult.Builder().subject(SECURITY_PROTOCOL.getDisplayName()).valid(false)
                    .explanation("If you set the SSL Controller Service you should also choose an SSL based security protocol.").build());
        }
        if (!csSet && sslProtocol) {
            results.add(new ValidationResult.Builder().subject(SSL_CONTEXT_SERVICE.getDisplayName()).valid(false)
                    .explanation("If you set to an SSL based protocol you need to set the SSL Controller Service").build());
        }

        final String enableAutoCommit = validationContext.getProperty(new PropertyDescriptor.Builder().name(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG).build()).getValue();
        if (enableAutoCommit != null && !enableAutoCommit.toLowerCase().equals("false")) {
            results.add(new ValidationResult.Builder().subject(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)
                    .explanation("Enable auto commit must be false.  It is managed by the processor.").build());
        }

        final String keySerializer = validationContext.getProperty(new PropertyDescriptor.Builder().name(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG).build()).getValue();
        if (keySerializer != null && !ByteArraySerializer.class.getName().equals(keySerializer)) {
            results.add(new ValidationResult.Builder().subject(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)
                    .explanation("Key Serializer must be " + ByteArraySerializer.class.getName() + "' was '" + keySerializer + "'").build());
        }

        final String valueSerializer = validationContext.getProperty(new PropertyDescriptor.Builder().name(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG).build()).getValue();
        if (valueSerializer != null && !ByteArraySerializer.class.getName().equals(valueSerializer)) {
            results.add(new ValidationResult.Builder().subject(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)
                    .explanation("Value Serializer must be " + ByteArraySerializer.class.getName() + "' was '" + valueSerializer + "'").build());
        }

        final String keyDeSerializer = validationContext.getProperty(new PropertyDescriptor.Builder().name(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG).build()).getValue();
        if (keyDeSerializer != null && !ByteArrayDeserializer.class.getName().equals(keyDeSerializer)) {
            results.add(new ValidationResult.Builder().subject(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)
                    .explanation("Key De-Serializer must be '" + ByteArrayDeserializer.class.getName() + "' was '" + keyDeSerializer + "'").build());
        }

        final String valueDeSerializer = validationContext.getProperty(new PropertyDescriptor.Builder().name(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG).build()).getValue();
        if (valueDeSerializer != null && !ByteArrayDeserializer.class.getName().equals(valueDeSerializer)) {
            results.add(new ValidationResult.Builder().subject(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)
                    .explanation("Value De-Serializer must be " + ByteArrayDeserializer.class.getName() + "' was '" + valueDeSerializer + "'").build());
        }

        return results;
    }

    static final class KafkaConfigValidator implements Validator {

        final Class<?> classType;

        public KafkaConfigValidator(final Class classType) {
            this.classType = classType;
        }

        @Override
        public ValidationResult validate(final String subject, final String value, final ValidationContext context) {
            final boolean knownValue = KafkaProcessorUtils.isStaticStringFieldNamePresent(subject, classType, CommonClientConfigs.class, SslConfigs.class, SaslConfigs.class);
            return new ValidationResult.Builder().subject(subject).explanation("Must be a known configuration parameter for this kafka client").valid(knownValue).build();
        }
    };

    /**
     * Builds transit URI for provenance event. The transit URI will be in the
     * form of &lt;security.protocol&gt;://&lt;bootstrap.servers&gt;/topic
     */
    static String buildTransitURI(String securityProtocol, String brokers, String topic) {
        StringBuilder builder = new StringBuilder();
        builder.append(securityProtocol);
        builder.append("://");
        builder.append(brokers);
        builder.append("/");
        builder.append(topic);
        return builder.toString();
    }

    static void buildCommonKafkaProperties(final ProcessContext context, final Class kafkaConfigClass, final Map<String, String> mapToPopulate) {
        for (PropertyDescriptor propertyDescriptor : context.getProperties().keySet()) {
            if (propertyDescriptor.equals(SSL_CONTEXT_SERVICE)) {
                // Translate SSLContext Service configuration into Kafka properties
                final SSLContextService sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);
                if (sslContextService != null && sslContextService.isKeyStoreConfigured()) {
                    mapToPopulate.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslContextService.getKeyStoreFile());
                    mapToPopulate.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslContextService.getKeyStorePassword());
                    final String keyPass = sslContextService.getKeyPassword() == null ? sslContextService.getKeyStorePassword() : sslContextService.getKeyPassword();
                    mapToPopulate.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPass);
                    mapToPopulate.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, sslContextService.getKeyStoreType());
                }

                if (sslContextService != null && sslContextService.isTrustStoreConfigured()) {
                    mapToPopulate.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslContextService.getTrustStoreFile());
                    mapToPopulate.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslContextService.getTrustStorePassword());
                    mapToPopulate.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslContextService.getTrustStoreType());
                }
            }
            String pName = propertyDescriptor.getName();
            String pValue = propertyDescriptor.isExpressionLanguageSupported()
                    ? context.getProperty(propertyDescriptor).evaluateAttributeExpressions().getValue()
                    : context.getProperty(propertyDescriptor).getValue();
            if (pValue != null) {
                if (pName.endsWith(".ms")) { // kafka standard time notation
                    pValue = String.valueOf(FormatUtils.getTimeDuration(pValue.trim(), TimeUnit.MILLISECONDS));
                }
                if (isStaticStringFieldNamePresent(pName, kafkaConfigClass, CommonClientConfigs.class, SslConfigs.class, SaslConfigs.class)) {
                    mapToPopulate.put(pName, pValue);
                }
            }
        }
    }

    private static boolean isStaticStringFieldNamePresent(final String name, final Class... classes) {
        return KafkaProcessorUtils.getPublicStaticStringFieldValues(classes).contains(name);
    }

    private static Set<String> getPublicStaticStringFieldValues(final Class... classes) {
        final Set<String> strings = new HashSet<>();
        for (final Class classType : classes) {
            for (final Field field : classType.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getType().equals(String.class)) {
                    try {
                        strings.add(String.valueOf(field.get(null)));
                    } catch (IllegalArgumentException | IllegalAccessException ex) {
                        //ignore
                    }
                }
            }
        }
        return strings;
    }

}
