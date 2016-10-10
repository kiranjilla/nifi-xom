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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.apache.http.util.VersionInfo;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.security.util.CertificateUtils;
import org.apache.nifi.ssl.SSLContextService;
import org.apache.nifi.stream.io.BufferedInputStream;
import org.apache.nifi.stream.io.BufferedOutputStream;
import org.apache.nifi.stream.io.GZIPOutputStream;
import org.apache.nifi.stream.io.LeakyBucketStreamThrottler;
import org.apache.nifi.stream.io.StreamThrottler;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.FlowFilePackager;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.apache.nifi.util.FlowFilePackagerV2;
import org.apache.nifi.util.FlowFilePackagerV3;
import org.apache.nifi.util.FormatUtils;
import org.apache.nifi.util.StopWatch;
import org.apache.nifi.util.StringUtils;
import com.sun.jersey.api.client.ClientResponse.Status;

@SupportsBatching
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"http", "https", "remote", "copy", "archive"})
@CapabilityDescription("Performs an HTTP Post with the content of the FlowFile")
public class PostHTTP extends AbstractProcessor {

    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String APPLICATION_FLOW_FILE_V1 = "application/flowfile";
    public static final String APPLICATION_FLOW_FILE_V2 = "application/flowfile-v2";
    public static final String APPLICATION_FLOW_FILE_V3 = "application/flowfile-v3";
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    public static final String FLOWFILE_CONFIRMATION_HEADER = "x-prefer-acknowledge-uri";
    public static final String LOCATION_HEADER_NAME = "Location";
    public static final String LOCATION_URI_INTENT_NAME = "x-location-uri-intent";
    public static final String LOCATION_URI_INTENT_VALUE = "flowfile-hold";
    public static final String GZIPPED_HEADER = "flowfile-gzipped";
    public static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
    public static final String CONTENT_ENCODING_GZIP_VALUE = "gzip";

    public static final String PROTOCOL_VERSION_HEADER = "x-nifi-transfer-protocol-version";
    public static final String TRANSACTION_ID_HEADER = "x-nifi-transaction-id";
    public static final String PROTOCOL_VERSION = "3";

    public static final PropertyDescriptor URL = new PropertyDescriptor.Builder()
            .name("URL")
            .description("The URL to POST to. The first part of the URL must be static. However, the path of the URL may be defined using the Attribute Expression Language. "
                    + "For example, https://${hostname} is not valid, but https://1.1.1.1:8080/files/${nf.file.name} is valid.")
            .required(true)
            .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("https?\\://.*")))
            .addValidator(StandardValidators.URL_VALIDATOR)
            .expressionLanguageSupported(true)
            .build();
    public static final PropertyDescriptor SEND_AS_FLOWFILE = new PropertyDescriptor.Builder()
            .name("Send as FlowFile")
            .description("If true, will package the FlowFile's contents and attributes together and send the FlowFile Package; otherwise, will send only the FlowFile's content")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("false")
            .build();
    public static final PropertyDescriptor CONNECTION_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Connection Timeout")
            .description("How long to wait when attempting to connect to the remote server before giving up")
            .required(true)
            .defaultValue("30 sec")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();
    public static final PropertyDescriptor DATA_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Data Timeout")
            .description("How long to wait between receiving segments of data from the remote server before giving up and discarding the partial file")
            .required(true)
            .defaultValue("30 sec")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();
    public static final PropertyDescriptor USERNAME = new PropertyDescriptor.Builder()
            .name("Username")
            .description("Username required to access the URL")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder()
            .name("Password")
            .description("Password required to access the URL")
            .required(false)
            .sensitive(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final PropertyDescriptor USER_AGENT = new PropertyDescriptor.Builder()
            .name("User Agent")
            .description("What to report as the User Agent when we connect to the remote server")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .defaultValue(VersionInfo.getUserAgent("Apache-HttpClient", "org.apache.http.client", HttpClientBuilder.class))
            .build();
    public static final PropertyDescriptor COMPRESSION_LEVEL = new PropertyDescriptor.Builder()
            .name("Compression Level")
            .description("Determines the GZIP Compression Level to use when sending the file; the value must be in the range of 0-9. A value of 0 indicates that the file will not be GZIP'ed")
            .required(true)
            .addValidator(StandardValidators.createLongValidator(0, 9, true))
            .defaultValue("0")
            .build();
    public static final PropertyDescriptor ATTRIBUTES_AS_HEADERS_REGEX = new PropertyDescriptor.Builder()
            .name("Attributes to Send as HTTP Headers (Regex)")
            .description("Specifies the Regular Expression that determines the names of FlowFile attributes that should be sent as HTTP Headers")
            .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
            .required(false)
            .build();
    public static final PropertyDescriptor MAX_DATA_RATE = new PropertyDescriptor.Builder()
            .name("Max Data to Post per Second")
            .description("The maximum amount of data to send per second; this allows the bandwidth to be throttled to a specified data rate; if not specified, the data rate is not throttled")
            .required(false)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .build();
    public static final PropertyDescriptor MAX_BATCH_SIZE = new PropertyDescriptor.Builder()
            .name("Max Batch Size")
            .description("If the Send as FlowFile property is true, specifies the max data size for a batch of FlowFiles to send in a single "
                    + "HTTP POST. If not specified, each FlowFile will be sent separately. If the Send as FlowFile property is false, this "
                    + "property is ignored")
            .required(false)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .defaultValue("100 MB")
            .build();
    public static final PropertyDescriptor CHUNKED_ENCODING = new PropertyDescriptor.Builder()
            .name("Use Chunked Encoding")
            .description("Specifies whether or not to use Chunked Encoding to send the data. This property is ignored in the event the contents are compressed "
                    + "or sent as FlowFiles.")
            .allowableValues("true", "false")
            .build();
    public static final PropertyDescriptor SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description("The Controller Service to use in order to obtain an SSL Context")
            .required(false)
            .identifiesControllerService(SSLContextService.class)
            .build();
    public static final PropertyDescriptor PROXY_HOST = new PropertyDescriptor.Builder()
            .name("Proxy Host")
            .description("The fully qualified hostname or IP address of the proxy server")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final PropertyDescriptor PROXY_PORT = new PropertyDescriptor.Builder()
            .name("Proxy Port")
            .description("The port of the proxy server")
            .required(false)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();
    public static final PropertyDescriptor CONTENT_TYPE = new PropertyDescriptor.Builder()
            .name("Content-Type")
            .description("The Content-Type to specify for the content of the FlowFile being POSTed if " + SEND_AS_FLOWFILE.getName() + " is false. "
                    + "In the case of an empty value after evaluating an expression language expression, Content-Type defaults to " + DEFAULT_CONTENT_TYPE)
            .required(true)
            .expressionLanguageSupported(true)
            .defaultValue("${" + CoreAttributes.MIME_TYPE.key() + "}")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Files that are successfully send will be transferred to success")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("Files that fail to send will transferred to failure")
            .build();

    private Set<Relationship> relationships;
    private List<PropertyDescriptor> properties;

    private final AtomicReference<DestinationAccepts> acceptsRef = new AtomicReference<>();
    private final AtomicReference<StreamThrottler> throttlerRef = new AtomicReference<>();
    private final ConcurrentMap<String, Config> configMap = new ConcurrentHashMap<>();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(URL);
        properties.add(MAX_BATCH_SIZE);
        properties.add(MAX_DATA_RATE);
        properties.add(SSL_CONTEXT_SERVICE);
        properties.add(USERNAME);
        properties.add(PASSWORD);
        properties.add(SEND_AS_FLOWFILE);
        properties.add(CHUNKED_ENCODING);
        properties.add(COMPRESSION_LEVEL);
        properties.add(CONNECTION_TIMEOUT);
        properties.add(DATA_TIMEOUT);
        properties.add(ATTRIBUTES_AS_HEADERS_REGEX);
        properties.add(USER_AGENT);
        properties.add(PROXY_HOST);
        properties.add(PROXY_PORT);
        properties.add(CONTENT_TYPE);
        this.properties = Collections.unmodifiableList(properties);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext context) {
        final Collection<ValidationResult> results = new ArrayList<>();

        if (context.getProperty(URL).getValue().startsWith("https") && context.getProperty(SSL_CONTEXT_SERVICE).getValue() == null) {
            results.add(new ValidationResult.Builder()
                    .explanation("URL is set to HTTPS protocol but no SSLContext has been specified")
                    .valid(false).subject("SSL Context").build());
        }

        if (context.getProperty(PROXY_HOST).isSet() && !context.getProperty(PROXY_PORT).isSet()) {
            results.add(new ValidationResult.Builder()
                    .explanation("Proxy Host was set but no Proxy Port was specified")
                    .valid(false)
                    .subject("Proxy server configuration")
                    .build());
        }

        boolean sendAsFlowFile = context.getProperty(SEND_AS_FLOWFILE).asBoolean();
        int compressionLevel = context.getProperty(COMPRESSION_LEVEL).asInteger();
        boolean chunkedSet = context.getProperty(CHUNKED_ENCODING).isSet();

        if (compressionLevel == 0 && !sendAsFlowFile && !chunkedSet) {
            results.add(new ValidationResult.Builder().valid(false).subject(CHUNKED_ENCODING.getName())
                    .explanation("if compression level is 0 and not sending as a FlowFile, then the \'" + CHUNKED_ENCODING.getName() + "\' property must be set").build());
        }

        return results;
    }

    @OnStopped
    public void onStopped() {
        this.acceptsRef.set(null);

        for (final Map.Entry<String, Config> entry : configMap.entrySet()) {
            final Config config = entry.getValue();
            config.getConnectionManager().shutdown();
        }

        configMap.clear();

        final StreamThrottler throttler = throttlerRef.getAndSet(null);
        if (throttler != null) {
            try {
                throttler.close();
            } catch (IOException e) {
                getLogger().error("Failed to close StreamThrottler", e);
            }
        }
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        final Double bytesPerSecond = context.getProperty(MAX_DATA_RATE).asDataSize(DataUnit.B);
        this.throttlerRef.set(bytesPerSecond == null ? null : new LeakyBucketStreamThrottler(bytesPerSecond.intValue()));
    }

    private String getBaseUrl(final String url) {
        final int index = url.indexOf("/", 9);
        if (index < 0) {
            return url;
        }

        return url.substring(0, index);
    }

    private Config getConfig(final String url, final ProcessContext context) {
        final String baseUrl = getBaseUrl(url);
        Config config = configMap.get(baseUrl);
        if (config != null) {
            return config;
        }

        final PoolingHttpClientConnectionManager conMan;
        final SSLContextService sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);
        if (sslContextService == null) {
            conMan = new PoolingHttpClientConnectionManager();
        } else {
            final SSLContext sslContext;
            try {
                sslContext = createSSLContext(sslContextService);
                getLogger().info("PostHTTP supports protocol: " + sslContext.getProtocol());
            } catch (final Exception e) {
                throw new ProcessException(e);
            }

            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
            // Also use a plain socket factory for regular http connections (especially proxies)
            final Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("https", sslsf)
                            .register("http", PlainConnectionSocketFactory.getSocketFactory())
                            .build();

            conMan = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        }

        conMan.setDefaultMaxPerRoute(context.getMaxConcurrentTasks());
        conMan.setMaxTotal(context.getMaxConcurrentTasks());
        config = new Config(conMan);
        final Config existingConfig = configMap.putIfAbsent(baseUrl, config);

        return existingConfig == null ? config : existingConfig;
    }

    private SSLContext createSSLContext(final SSLContextService service)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, KeyManagementException, UnrecoverableKeyException {
        SSLContextBuilder builder = SSLContexts.custom();
        final String trustFilename = service.getTrustStoreFile();
        if (trustFilename != null) {
            final KeyStore truststore = KeyStore.getInstance(service.getTrustStoreType());
            try (final InputStream in = new FileInputStream(new File(service.getTrustStoreFile()))) {
                truststore.load(in, service.getTrustStorePassword().toCharArray());
            }
            builder = builder.loadTrustMaterial(truststore, new TrustSelfSignedStrategy());
        }

        final String keyFilename = service.getKeyStoreFile();
        if (keyFilename != null) {
            final KeyStore keystore = KeyStore.getInstance(service.getKeyStoreType());
            try (final InputStream in = new FileInputStream(new File(service.getKeyStoreFile()))) {
                keystore.load(in, service.getKeyStorePassword().toCharArray());
            }
            builder = builder.loadKeyMaterial(keystore, service.getKeyStorePassword().toCharArray());
        }

        builder = builder.useProtocol(service.getSslAlgorithm());

        final SSLContext sslContext = builder.build();
        return sslContext;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        final boolean sendAsFlowFile = context.getProperty(SEND_AS_FLOWFILE).asBoolean();
        final int compressionLevel = context.getProperty(COMPRESSION_LEVEL).asInteger();
        final String userAgent = context.getProperty(USER_AGENT).getValue();

        final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        requestConfigBuilder.setConnectionRequestTimeout(context.getProperty(DATA_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue());
        requestConfigBuilder.setConnectTimeout(context.getProperty(CONNECTION_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue());
        requestConfigBuilder.setRedirectsEnabled(false);
        requestConfigBuilder.setSocketTimeout(context.getProperty(DATA_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue());
        final RequestConfig requestConfig = requestConfigBuilder.build();

        final StreamThrottler throttler = throttlerRef.get();
        final ComponentLog logger = getLogger();

        final Double maxBatchBytes = context.getProperty(MAX_BATCH_SIZE).asDataSize(DataUnit.B);
        String lastUrl = null;
        long bytesToSend = 0L;

        final List<FlowFile> toSend = new ArrayList<>();
        DestinationAccepts destinationAccepts = null;
        CloseableHttpClient client = null;
        final String transactionId = UUID.randomUUID().toString();

        final AtomicReference<String> dnHolder = new AtomicReference<>("none");
        while (true) {
            FlowFile flowFile = session.get();
            if (flowFile == null) {
                break;
            }

            final String url = context.getProperty(URL).evaluateAttributeExpressions(flowFile).getValue();
            try {
                new java.net.URL(url);
            } catch (final MalformedURLException e) {
                logger.error("After substituting attribute values for {}, URL is {}; this is not a valid URL, so routing to failure",
                        new Object[]{flowFile, url});
                flowFile = session.penalize(flowFile);
                session.transfer(flowFile, REL_FAILURE);
                continue;
            }

            // If this FlowFile doesn't have the same url, throw it back on the queue and stop grabbing FlowFiles
            if (lastUrl != null && !lastUrl.equals(url)) {
                session.transfer(flowFile);
                break;
            }

            lastUrl = url;
            toSend.add(flowFile);

            if (client == null || destinationAccepts == null) {
                final Config config = getConfig(url, context);
                final HttpClientConnectionManager conMan = config.getConnectionManager();

                final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
                clientBuilder.setConnectionManager(conMan);
                clientBuilder.setUserAgent(userAgent);
                clientBuilder.addInterceptorFirst(new HttpResponseInterceptor() {
                    @Override
                    public void process(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                        final HttpCoreContext coreContext = HttpCoreContext.adapt(httpContext);
                        final ManagedHttpClientConnection conn = coreContext.getConnection(ManagedHttpClientConnection.class);
                        if (!conn.isOpen()) {
                            return;
                        }

                        final SSLSession sslSession = conn.getSSLSession();

                        if (sslSession != null) {
                            final Certificate[] certChain = sslSession.getPeerCertificates();
                            if (certChain == null || certChain.length == 0) {
                                throw new SSLPeerUnverifiedException("No certificates found");
                            }

                            try {
                                final X509Certificate cert = CertificateUtils.convertAbstractX509Certificate(certChain[0]);
                                dnHolder.set(cert.getSubjectDN().getName().trim());
                            } catch (CertificateException e) {
                                final String msg = "Could not extract subject DN from SSL session peer certificate";
                                logger.warn(msg);
                                throw new SSLPeerUnverifiedException(msg);
                            }
                        }
                    }
                });

                clientBuilder.disableAutomaticRetries();
                clientBuilder.disableContentCompression();

                final String username = context.getProperty(USERNAME).getValue();
                final String password = context.getProperty(PASSWORD).getValue();
                // set the credentials if appropriate
                if (username != null) {
                    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    if (password == null) {
                        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username));
                    } else {
                        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                    }
                    clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }

                // Set the proxy if specified
                if (context.getProperty(PROXY_HOST).isSet() && context.getProperty(PROXY_PORT).isSet()) {
                    final String host = context.getProperty(PROXY_HOST).getValue();
                    final int port = context.getProperty(PROXY_PORT).asInteger();
                    clientBuilder.setProxy(new HttpHost(host, port));
                }

                client = clientBuilder.build();

                // determine whether or not destination accepts flowfile/gzip
                destinationAccepts = config.getDestinationAccepts();
                if (destinationAccepts == null) {
                    try {
                        destinationAccepts = getDestinationAcceptance(sendAsFlowFile, client, url, getLogger(), transactionId);
                        config.setDestinationAccepts(destinationAccepts);
                    } catch (final IOException e) {
                        flowFile = session.penalize(flowFile);
                        session.transfer(flowFile, REL_FAILURE);
                        logger.error("Unable to communicate with destination {} to determine whether or not it can accept "
                                + "flowfiles/gzip; routing {} to failure due to {}", new Object[]{url, flowFile, e});
                        context.yield();
                        return;
                    }
                }
            }

            bytesToSend += flowFile.getSize();
            if (bytesToSend > maxBatchBytes.longValue()) {
                break;
            }

            // if we are not sending as flowfile, or if the destination doesn't accept V3 or V2 (streaming) format,
            // then only use a single FlowFile
            if (!sendAsFlowFile || !destinationAccepts.isFlowFileV3Accepted() && !destinationAccepts.isFlowFileV2Accepted()) {
                break;
            }
        }

        if (toSend.isEmpty()) {
            return;
        }

        final String url = lastUrl;
        final HttpPost post = new HttpPost(url);
        final List<FlowFile> flowFileList = toSend;
        final DestinationAccepts accepts = destinationAccepts;
        final boolean isDestinationLegacyNiFi = accepts.getProtocolVersion() == null;

        final EntityTemplate entity = new EntityTemplate(new ContentProducer() {
            @Override
            public void writeTo(final OutputStream rawOut) throws IOException {
                final OutputStream throttled = throttler == null ? rawOut : throttler.newThrottledOutputStream(rawOut);
                OutputStream wrappedOut = new BufferedOutputStream(throttled);
                if (compressionLevel > 0 && accepts.isGzipAccepted()) {
                    wrappedOut = new GZIPOutputStream(wrappedOut, compressionLevel);
                }

                try (final OutputStream out = wrappedOut) {
                    for (final FlowFile flowFile : flowFileList) {
                        session.read(flowFile, new InputStreamCallback() {
                            @Override
                            public void process(final InputStream rawIn) throws IOException {
                                try (final InputStream in = new BufferedInputStream(rawIn)) {

                                    FlowFilePackager packager = null;
                                    if (!sendAsFlowFile) {
                                        packager = null;
                                    } else if (accepts.isFlowFileV3Accepted()) {
                                        packager = new FlowFilePackagerV3();
                                    } else if (accepts.isFlowFileV2Accepted()) {
                                        packager = new FlowFilePackagerV2();
                                    } else if (accepts.isFlowFileV1Accepted()) {
                                        packager = new FlowFilePackagerV1();
                                    }

                                    // if none of the above conditions is met, we should never get here, because
                                    // we will have already verified that at least 1 of the FlowFile packaging
                                    // formats is acceptable if sending as FlowFile.
                                    if (packager == null) {
                                        StreamUtils.copy(in, out);
                                    } else {
                                        final Map<String, String> flowFileAttributes;
                                        if (isDestinationLegacyNiFi) {
                                            // Old versions of NiFi expect nf.file.name and nf.file.path to indicate filename & path;
                                            // in order to maintain backward compatibility, we copy the filename & path to those attribute keys.
                                            flowFileAttributes = new HashMap<>(flowFile.getAttributes());
                                            flowFileAttributes.put("nf.file.name", flowFile.getAttribute(CoreAttributes.FILENAME.key()));
                                            flowFileAttributes.put("nf.file.path", flowFile.getAttribute(CoreAttributes.PATH.key()));
                                        } else {
                                            flowFileAttributes = flowFile.getAttributes();
                                        }

                                        packager.packageFlowFile(in, out, flowFileAttributes, flowFile.getSize());
                                    }
                                }
                            }
                        });
                    }

                    out.flush();
                }
            }
        }) {

            @Override
            public long getContentLength() {
                if (compressionLevel == 0 && !sendAsFlowFile && !context.getProperty(CHUNKED_ENCODING).asBoolean()) {
                    return toSend.get(0).getSize();
                } else {
                    return -1;
                }
            }
        };

        if (context.getProperty(CHUNKED_ENCODING).isSet()) {
            entity.setChunked(context.getProperty(CHUNKED_ENCODING).asBoolean());
        }
        post.setEntity(entity);
        post.setConfig(requestConfig);

        final String contentType;
        if (sendAsFlowFile) {
            if (accepts.isFlowFileV3Accepted()) {
                contentType = APPLICATION_FLOW_FILE_V3;
            } else if (accepts.isFlowFileV2Accepted()) {
                contentType = APPLICATION_FLOW_FILE_V2;
            } else if (accepts.isFlowFileV1Accepted()) {
                contentType = APPLICATION_FLOW_FILE_V1;
            } else {
                logger.error("Cannot send data to {} because the destination does not accept FlowFiles and this processor is "
                        + "configured to deliver FlowFiles; rolling back session", new Object[]{url});
                session.rollback();
                context.yield();
                IOUtils.closeQuietly(client);
                return;
            }
        } else {
            final String contentTypeValue = context.getProperty(CONTENT_TYPE).evaluateAttributeExpressions(toSend.get(0)).getValue();
            contentType = StringUtils.isBlank(contentTypeValue) ? DEFAULT_CONTENT_TYPE : contentTypeValue;
        }

        final String attributeHeaderRegex = context.getProperty(ATTRIBUTES_AS_HEADERS_REGEX).getValue();
        if (attributeHeaderRegex != null && !sendAsFlowFile && flowFileList.size() == 1) {
            final Pattern pattern = Pattern.compile(attributeHeaderRegex);

            final Map<String, String> attributes = flowFileList.get(0).getAttributes();
            for (final Map.Entry<String, String> entry : attributes.entrySet()) {
                final String key = entry.getKey();
                if (pattern.matcher(key).matches()) {
                    post.setHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        post.setHeader(CONTENT_TYPE_HEADER, contentType);
        post.setHeader(FLOWFILE_CONFIRMATION_HEADER, "true");
        post.setHeader(PROTOCOL_VERSION_HEADER, PROTOCOL_VERSION);
        post.setHeader(TRANSACTION_ID_HEADER, transactionId);
        if (compressionLevel > 0 && accepts.isGzipAccepted()) {
            if (sendAsFlowFile) {
                post.setHeader(GZIPPED_HEADER, "true");
            } else {
                post.setHeader(CONTENT_ENCODING_HEADER, CONTENT_ENCODING_GZIP_VALUE);
            }
        }

        // Do the actual POST
        final String flowFileDescription = toSend.size() <= 10 ? toSend.toString() : toSend.size() + " FlowFiles";

        final String uploadDataRate;
        final long uploadMillis;
        CloseableHttpResponse response = null;
        try {
            final StopWatch stopWatch = new StopWatch(true);
            response = client.execute(post);

            // consume input stream entirely, ignoring its contents. If we
            // don't do this, the Connection will not be returned to the pool
            EntityUtils.consume(response.getEntity());
            stopWatch.stop();
            uploadDataRate = stopWatch.calculateDataRate(bytesToSend);
            uploadMillis = stopWatch.getDuration(TimeUnit.MILLISECONDS);
        } catch (final IOException e) {
            logger.error("Failed to Post {} due to {}; transferring to failure", new Object[]{flowFileDescription, e});
            context.yield();
            for (FlowFile flowFile : toSend) {
                flowFile = session.penalize(flowFile);
                session.transfer(flowFile, REL_FAILURE);
            }
            return;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (final IOException e) {
                    getLogger().warn("Failed to close HTTP Response due to {}", new Object[]{e});
                }
            }
        }

        // If we get a 'SEE OTHER' status code and an HTTP header that indicates that the intent
        // of the Location URI is a flowfile hold, we will store this holdUri. This prevents us
        // from posting to some other webservice and then attempting to delete some resource to which
        // we are redirected
        final int responseCode = response.getStatusLine().getStatusCode();
        final String responseReason = response.getStatusLine().getReasonPhrase();
        String holdUri = null;
        if (responseCode == HttpServletResponse.SC_SEE_OTHER) {
            final Header locationUriHeader = response.getFirstHeader(LOCATION_URI_INTENT_NAME);
            if (locationUriHeader != null) {
                if (LOCATION_URI_INTENT_VALUE.equals(locationUriHeader.getValue())) {
                    final Header holdUriHeader = response.getFirstHeader(LOCATION_HEADER_NAME);
                    if (holdUriHeader != null) {
                        holdUri = holdUriHeader.getValue();
                    }
                }
            }

            if (holdUri == null) {
                for (FlowFile flowFile : toSend) {
                    flowFile = session.penalize(flowFile);
                    logger.error("Failed to Post {} to {}: sent content and received status code {}:{} but no Hold URI",
                            new Object[]{flowFile, url, responseCode, responseReason});
                    session.transfer(flowFile, REL_FAILURE);
                }
                return;
            }
        }

        if (holdUri == null) {
            if (responseCode == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                for (FlowFile flowFile : toSend) {
                    flowFile = session.penalize(flowFile);
                    logger.error("Failed to Post {} to {}: response code was {}:{}; will yield processing, "
                                    + "since the destination is temporarily unavailable",
                            new Object[]{flowFile, url, responseCode, responseReason});
                    session.transfer(flowFile, REL_FAILURE);
                }
                context.yield();
                return;
            }

            if (responseCode >= 300) {
                for (FlowFile flowFile : toSend) {
                    flowFile = session.penalize(flowFile);
                    logger.error("Failed to Post {} to {}: response code was {}:{}",
                            new Object[]{flowFile, url, responseCode, responseReason});
                    session.transfer(flowFile, REL_FAILURE);
                }
                return;
            }

            logger.info("Successfully Posted {} to {} in {} at a rate of {}",
                    new Object[]{flowFileDescription, url, FormatUtils.formatMinutesSeconds(uploadMillis, TimeUnit.MILLISECONDS), uploadDataRate});

            for (final FlowFile flowFile : toSend) {
                session.getProvenanceReporter().send(flowFile, url, "Remote DN=" + dnHolder.get(), uploadMillis, true);
                session.transfer(flowFile, REL_SUCCESS);
            }
            return;
        }

        //
        // the response indicated a Hold URI; delete the Hold.
        //
        // determine the full URI of the Flow File's Hold; Unfortunately, the responses that are returned have
        // changed over the past, so we have to take into account a few different possibilities.
        String fullHoldUri = holdUri;
        if (holdUri.startsWith("/contentListener")) {
            // If the Hold URI that we get starts with /contentListener, it may not really be /contentListener,
            // as this really indicates that it should be whatever we posted to -- if posting directly to the
            // ListenHTTP component, it will be /contentListener, but if posting to a proxy/load balancer, we may
            // be posting to some other URL.
            fullHoldUri = url + holdUri.substring(16);
        } else if (holdUri.startsWith("/")) {
            // URL indicates the full path but not hostname or port; use the same hostname & port that we posted
            // to but use the full path indicated by the response.
            int firstSlash = url.indexOf("/", 8);
            if (firstSlash < 0) {
                firstSlash = url.length();
            }
            final String beforeSlash = url.substring(0, firstSlash);
            fullHoldUri = beforeSlash + holdUri;
        } else if (!holdUri.startsWith("http")) {
            // Absolute URL
            fullHoldUri = url + (url.endsWith("/") ? "" : "/") + holdUri;
        }

        final HttpDelete delete = new HttpDelete(fullHoldUri);
        delete.setHeader(TRANSACTION_ID_HEADER, transactionId);

        while (true) {
            try {
                final HttpResponse holdResponse = client.execute(delete);
                EntityUtils.consume(holdResponse.getEntity());
                final int holdStatusCode = holdResponse.getStatusLine().getStatusCode();
                final String holdReason = holdResponse.getStatusLine().getReasonPhrase();
                if (holdStatusCode >= 300) {
                    logger.error("Failed to delete Hold that destination placed on {}: got response code {}:{}; routing to failure",
                            new Object[]{flowFileDescription, holdStatusCode, holdReason});

                    for (FlowFile flowFile : toSend) {
                        flowFile = session.penalize(flowFile);
                        session.transfer(flowFile, REL_FAILURE);
                    }
                    return;
                }

                logger.info("Successfully Posted {} to {} in {} milliseconds at a rate of {}", new Object[]{flowFileDescription, url, uploadMillis, uploadDataRate});

                for (final FlowFile flowFile : toSend) {
                    session.getProvenanceReporter().send(flowFile, url);
                    session.transfer(flowFile, REL_SUCCESS);
                }
                return;
            } catch (final IOException e) {
                logger.warn("Failed to delete Hold that destination placed on {} due to {}", new Object[]{flowFileDescription, e});
            }

            if (!isScheduled()) {
                context.yield();
                logger.warn("Failed to delete Hold that destination placed on {}; Processor has been stopped so routing FlowFile(s) to failure", new Object[]{flowFileDescription});
                for (FlowFile flowFile : toSend) {
                    flowFile = session.penalize(flowFile);
                    session.transfer(flowFile, REL_FAILURE);
                }
                return;
            }
        }
    }

    private DestinationAccepts getDestinationAcceptance(final boolean sendAsFlowFile, final HttpClient client, final String uri,
                                                        final ComponentLog logger, final String transactionId) throws IOException {
        final HttpHead head = new HttpHead(uri);
        if (sendAsFlowFile) {
            head.addHeader(TRANSACTION_ID_HEADER, transactionId);
        }
        final HttpResponse response = client.execute(head);

        // we assume that the destination can support FlowFile v1 always when the processor is also configured to send as a FlowFile
        // otherwise, we do not bother to make any determinations concerning this compatibility
        final boolean acceptsFlowFileV1 = sendAsFlowFile;
        boolean acceptsFlowFileV2 = false;
        boolean acceptsFlowFileV3 = false;
        boolean acceptsGzip = false;
        Integer protocolVersion = null;

        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == Status.METHOD_NOT_ALLOWED.getStatusCode()) {
            return new DestinationAccepts(acceptsFlowFileV3, acceptsFlowFileV2, acceptsFlowFileV1, false, null);
        } else if (statusCode == Status.OK.getStatusCode()) {
            Header[] headers = response.getHeaders(ACCEPT);
            // If configured to send as a flowfile, determine the capabilities of the endpoint
            if (sendAsFlowFile) {
                if (headers != null) {
                    for (final Header header : headers) {
                        for (final String accepted : header.getValue().split(",")) {
                            final String trimmed = accepted.trim();
                            if (trimmed.equals(APPLICATION_FLOW_FILE_V3)) {
                                acceptsFlowFileV3 = true;
                            } else if (trimmed.equals(APPLICATION_FLOW_FILE_V2)) {
                                acceptsFlowFileV2 = true;
                            }
                        }
                    }
                }

                final Header destinationVersion = response.getFirstHeader(PROTOCOL_VERSION_HEADER);
                if (destinationVersion != null) {
                    try {
                        protocolVersion = Integer.valueOf(destinationVersion.getValue());
                    } catch (final NumberFormatException e) {
                        // nothing to do here really.... it's an invalid value, so treat the same as if not specified
                    }
                }

                if (acceptsFlowFileV3) {
                    logger.debug("Connection to URI " + uri + " will be using Content Type " + APPLICATION_FLOW_FILE_V3 + " if sending data as FlowFile");
                } else if (acceptsFlowFileV2) {
                    logger.debug("Connection to URI " + uri + " will be using Content Type " + APPLICATION_FLOW_FILE_V2 + " if sending data as FlowFile");
                } else if (acceptsFlowFileV1) {
                    logger.debug("Connection to URI " + uri + " will be using Content Type " + APPLICATION_FLOW_FILE_V1 + " if sending data as FlowFile");
                }
            }

            headers = response.getHeaders(ACCEPT_ENCODING);
            if (headers != null) {
                for (final Header header : headers) {
                    for (final String accepted : header.getValue().split(",")) {
                        if (accepted.equalsIgnoreCase("gzip")) {
                            acceptsGzip = true;
                        }
                    }
                }
            }

            if (acceptsGzip) {
                logger.debug("Connection to URI " + uri + " indicates that inline GZIP compression is supported");
            } else {
                logger.debug("Connection to URI " + uri + " indicates that it does NOT support inline GZIP compression");
            }

            return new DestinationAccepts(acceptsFlowFileV3, acceptsFlowFileV2, acceptsFlowFileV1, acceptsGzip, protocolVersion);
        } else {
            logger.warn("Unable to communicate with destination; when attempting to perform an HTTP HEAD, got unexpected response code of "
                    + statusCode + ": " + response.getStatusLine().getReasonPhrase());
            return new DestinationAccepts(false, false, false, false, null);
        }
    }

    private static class DestinationAccepts {

        private final boolean flowFileV1;
        private final boolean flowFileV2;
        private final boolean flowFileV3;
        private final boolean gzip;
        private final Integer protocolVersion;

        public DestinationAccepts(final boolean flowFileV3, final boolean flowFileV2, final boolean flowFileV1, final boolean gzip, final Integer protocolVersion) {
            this.flowFileV3 = flowFileV3;
            this.flowFileV2 = flowFileV2;
            this.flowFileV1 = flowFileV1;
            this.gzip = gzip;
            this.protocolVersion = protocolVersion;
        }

        public boolean isFlowFileV3Accepted() {
            return flowFileV3;
        }

        public boolean isFlowFileV2Accepted() {
            return flowFileV2;
        }

        public boolean isFlowFileV1Accepted() {
            return flowFileV1;
        }

        public boolean isGzipAccepted() {
            return gzip;
        }

        public Integer getProtocolVersion() {
            return protocolVersion;
        }
    }

    private static class Config {

        private volatile DestinationAccepts destinationAccepts;
        private final HttpClientConnectionManager conMan;

        public Config(final HttpClientConnectionManager conMan) {
            this.conMan = conMan;
        }

        public DestinationAccepts getDestinationAccepts() {
            return this.destinationAccepts;
        }

        public void setDestinationAccepts(final DestinationAccepts destinationAccepts) {
            this.destinationAccepts = destinationAccepts;
        }

        public HttpClientConnectionManager getConnectionManager() {
            return conMan;
        }
    }
}
