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
package org.apache.nifi.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.nifi.controller.queue.QueueSize;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.FlowFileFilter;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.FlowFileAccessException;
import org.apache.nifi.processor.exception.FlowFileHandlingException;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.provenance.ProvenanceReporter;
import org.junit.Assert;

public class MockProcessSession implements ProcessSession {

    private final Map<Relationship, List<MockFlowFile>> transferMap = new ConcurrentHashMap<>();
    private final MockFlowFileQueue processorQueue;
    private final Set<Long> beingProcessed = new HashSet<>();
    private final List<MockFlowFile> penalized = new ArrayList<>();
    private final Processor processor;

    private final Map<Long, MockFlowFile> currentVersions = new HashMap<>();
    private final Map<Long, MockFlowFile> originalVersions = new HashMap<>();
    private final SharedSessionState sharedState;
    private final Map<String, Long> counterMap = new HashMap<>();
    private final MockProvenanceReporter provenanceReporter;

    // A List of InputStreams that have been created by calls to {@link #read(FlowFile)} and have not yet been closed.
    private final List<InputStream> openInputStreams = new ArrayList<>();

    private boolean committed = false;
    private boolean rolledback = false;
    private int removedCount = 0;

    public MockProcessSession(final SharedSessionState sharedState, final Processor processor) {
        this.processor = processor;
        this.sharedState = sharedState;
        this.processorQueue = sharedState.getFlowFileQueue();
        provenanceReporter = new MockProvenanceReporter(this, sharedState, processor.getIdentifier(), processor.getClass().getSimpleName());
    }

    @Override
    public void adjustCounter(final String name, final long delta, final boolean immediate) {
        if (immediate) {
            sharedState.adjustCounter(name, delta);
            return;
        }

        Long counter = counterMap.get(name);
        if (counter == null) {
            counter = delta;
            counterMap.put(name, counter);
            return;
        }

        counter = counter + delta;
        counterMap.put(name, counter);
    }

    @Override
    public MockFlowFile clone(final FlowFile flowFile) {
        validateState(flowFile);
        final MockFlowFile newFlowFile = new MockFlowFile(sharedState.nextFlowFileId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        beingProcessed.add(newFlowFile.getId());
        return newFlowFile;
    }

    @Override
    public MockFlowFile clone(final FlowFile flowFile, final long offset, final long size) {
        validateState(flowFile);
        if (offset + size > flowFile.getSize()) {
            throw new FlowFileHandlingException("Specified offset of " + offset + " and size " + size + " exceeds size of " + flowFile.toString());
        }

        final MockFlowFile newFlowFile = new MockFlowFile(sharedState.nextFlowFileId(), flowFile);
        final byte[] newContent = Arrays.copyOfRange(((MockFlowFile) flowFile).getData(), (int) offset, (int) (offset + size));
        newFlowFile.setData(newContent);

        currentVersions.put(newFlowFile.getId(), newFlowFile);
        beingProcessed.add(newFlowFile.getId());
        return newFlowFile;
    }

    @Override
    public void commit() {
        if (!beingProcessed.isEmpty()) {
            throw new FlowFileHandlingException("Cannot commit session because the following FlowFiles have not been removed or transferred: " + beingProcessed);
        }

        if (!openInputStreams.isEmpty()) {
            final List<InputStream> openStreamCopy = new ArrayList<>(openInputStreams); // avoid ConcurrentModificationException by creating a copy of the List
            for (final InputStream openInputStream : openStreamCopy) {
                try {
                    openInputStream.close();
                } catch (final IOException e) {
                }
            }

            throw new FlowFileHandlingException("Cannot commit session because the following Input Streams were created via "
                + "calls to ProcessSession.read(FlowFile) and never closed: " + openStreamCopy);
        }

        committed = true;
        beingProcessed.clear();
        currentVersions.clear();
        originalVersions.clear();

        for (final Map.Entry<String, Long> entry : counterMap.entrySet()) {
            sharedState.adjustCounter(entry.getKey(), entry.getValue());
        }

        sharedState.addProvenanceEvents(provenanceReporter.getEvents());
        counterMap.clear();
    }

    /**
     * Clear the 'committed' flag so that we can test that the next iteration of
     * {@link org.apache.nifi.processor.Processor#onTrigger} commits or rolls back the
     * session
     */
    public void clearCommited() {
        committed = false;
    }

    /**
     * Clear the 'rolledBack' flag so that we can test that the next iteration
     * of {@link org.apache.nifi.processor.Processor#onTrigger} commits or rolls back the
     * session
     */
    public void clearRollback() {
        rolledback = false;
    }

    @Override
    public MockFlowFile create() {
        final MockFlowFile flowFile = new MockFlowFile(sharedState.nextFlowFileId());
        currentVersions.put(flowFile.getId(), flowFile);
        beingProcessed.add(flowFile.getId());
        return flowFile;
    }

    @Override
    public MockFlowFile create(final FlowFile flowFile) {
        MockFlowFile newFlowFile = create();
        newFlowFile = (MockFlowFile) inheritAttributes(flowFile, newFlowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        beingProcessed.add(newFlowFile.getId());
        return newFlowFile;
    }

    @Override
    public MockFlowFile create(final Collection<FlowFile> flowFiles) {
        MockFlowFile newFlowFile = create();
        newFlowFile = (MockFlowFile) inheritAttributes(flowFiles, newFlowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        beingProcessed.add(newFlowFile.getId());
        return newFlowFile;
    }

    @Override
    public void exportTo(final FlowFile flowFile, final OutputStream out) {
        validateState(flowFile);
        if (flowFile == null || out == null) {
            throw new IllegalArgumentException("arguments cannot be null");
        }

        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }

        final MockFlowFile mock = (MockFlowFile) flowFile;

        try {
            out.write(mock.getData());
        } catch (final IOException e) {
            throw new FlowFileAccessException(e.toString(), e);
        }
    }

    @Override
    public void exportTo(final FlowFile flowFile, final Path path, final boolean append) {
        validateState(flowFile);
        if (flowFile == null || path == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }

        final MockFlowFile mock = (MockFlowFile) flowFile;

        final OpenOption mode = append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE;

        try (final OutputStream out = Files.newOutputStream(path, mode)) {
            out.write(mock.getData());
        } catch (final IOException e) {
            throw new FlowFileAccessException(e.toString(), e);
        }
    }

    @Override
    public MockFlowFile get() {
        final MockFlowFile flowFile = processorQueue.poll();
        if (flowFile != null) {
            beingProcessed.add(flowFile.getId());
            currentVersions.put(flowFile.getId(), flowFile);
            originalVersions.put(flowFile.getId(), flowFile);
        }
        return flowFile;
    }

    @Override
    public List<FlowFile> get(final int maxResults) {
        final List<FlowFile> flowFiles = new ArrayList<>(Math.min(500, maxResults));
        for (int i = 0; i < maxResults; i++) {
            final MockFlowFile nextFlowFile = get();
            if (nextFlowFile == null) {
                return flowFiles;
            }

            flowFiles.add(nextFlowFile);
        }

        return flowFiles;
    }

    @Override
    public List<FlowFile> get(final FlowFileFilter filter) {
        final List<FlowFile> flowFiles = new ArrayList<>();
        final List<MockFlowFile> unselected = new ArrayList<>();

        while (true) {
            final MockFlowFile flowFile = processorQueue.poll();
            if (flowFile == null) {
                break;
            }

            final FlowFileFilter.FlowFileFilterResult filterResult = filter.filter(flowFile);
            if (filterResult.isAccept()) {
                flowFiles.add(flowFile);

                beingProcessed.add(flowFile.getId());
                currentVersions.put(flowFile.getId(), flowFile);
                originalVersions.put(flowFile.getId(), flowFile);
            } else {
                unselected.add(flowFile);
            }

            if (!filterResult.isContinue()) {
                break;
            }
        }

        processorQueue.addAll(unselected);
        return flowFiles;
    }

    @Override
    public QueueSize getQueueSize() {
        return processorQueue.size();
    }

    @Override
    public MockFlowFile importFrom(final InputStream in, final FlowFile flowFile) {
        validateState(flowFile);
        if (in == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        try {
            final byte[] data = readFully(in);
            newFlowFile.setData(data);
            return newFlowFile;
        } catch (final IOException e) {
            throw new FlowFileAccessException(e.toString(), e);
        }
    }

    @Override
    public MockFlowFile importFrom(final Path path, final boolean keepSourceFile, final FlowFile flowFile) {
        validateState(flowFile);
        if (path == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;
        MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Files.copy(path, baos);
        } catch (final IOException e) {
            throw new FlowFileAccessException(e.toString(), e);
        }

        newFlowFile.setData(baos.toByteArray());
        newFlowFile = putAttribute(newFlowFile, CoreAttributes.FILENAME.key(), path.getFileName().toString());
        return newFlowFile;
    }

    @Override
    public MockFlowFile merge(final Collection<FlowFile> sources, final FlowFile destination) {
        for (final FlowFile source : sources) {
            validateState(source);
        }
        validateState(destination);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (final FlowFile flowFile : sources) {
            final MockFlowFile mock = (MockFlowFile) flowFile;
            final byte[] data = mock.getData();
            try {
                baos.write(data);
            } catch (final IOException e) {
                throw new AssertionError("Failed to write to BAOS");
            }
        }

        final MockFlowFile newFlowFile = new MockFlowFile(destination.getId(), destination);
        newFlowFile.setData(baos.toByteArray());
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        return newFlowFile;
    }

    @Override
    public MockFlowFile putAllAttributes(final FlowFile flowFile, final Map<String, String> attrs) {
        validateState(flowFile);
        if (attrs == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot update attributes of a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;
        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        newFlowFile.putAttributes(attrs);
        return newFlowFile;
    }

    @Override
    public MockFlowFile putAttribute(final FlowFile flowFile, final String attrName, final String attrValue) {
        validateState(flowFile);
        if (attrName == null || attrValue == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot update attributes of a flow file that I did not create");
        }

        if ("uuid".equals(attrName)) {
            Assert.fail("Should not be attempting to set FlowFile UUID via putAttribute. This will be ignored in production");
        }

        final MockFlowFile mock = (MockFlowFile) flowFile;
        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        final Map<String, String> attrs = new HashMap<>();
        attrs.put(attrName, attrValue);
        newFlowFile.putAttributes(attrs);
        return newFlowFile;
    }

    @Override
    public void read(final FlowFile flowFile, final InputStreamCallback callback) {
        read(flowFile, false, callback);
    }

    @Override
    public void read(final FlowFile flowFile, boolean allowSessionStreamManagement, final InputStreamCallback callback) {
        if (callback == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }

        validateState(flowFile);
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final ByteArrayInputStream bais = new ByteArrayInputStream(mock.getData());
        try {
            callback.process(bais);
            if(!allowSessionStreamManagement){
                bais.close();
            }
        } catch (final IOException e) {
            throw new ProcessException(e.toString(), e);
        }
    }

    @Override
    public InputStream read(final FlowFile flowFile) {
        if (flowFile == null) {
            throw new IllegalArgumentException("FlowFile cannot be null");
        }

        validateState(flowFile);
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final ByteArrayInputStream bais = new ByteArrayInputStream(mock.getData());
        final InputStream errorHandlingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                return bais.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return bais.read(b, off, len);
            }

            @Override
            public void close() throws IOException {
                openInputStreams.remove(this);
                bais.close();
            }

            @Override
            public String toString() {
                return "ErrorHandlingInputStream[flowFile=" + flowFile + "]";
            }
        };

        openInputStreams.add(errorHandlingStream);
        return errorHandlingStream;
    }

    @Override
    public void remove(final FlowFile flowFile) {
        validateState(flowFile);

        final Iterator<MockFlowFile> penalizedItr = penalized.iterator();
        while (penalizedItr.hasNext()) {
            final MockFlowFile ff = penalizedItr.next();
            if (Objects.equals(ff.getId(), flowFile.getId())) {
                penalizedItr.remove();
                penalized.remove(ff);
                break;
            }
        }

        final Iterator<Long> processedItr = beingProcessed.iterator();
        while (processedItr.hasNext()) {
            final Long ffId = processedItr.next();
            if (ffId != null && ffId.equals(flowFile.getId())) {
                processedItr.remove();
                beingProcessed.remove(ffId);
                removedCount++;
                currentVersions.remove(ffId);
                return;
            }
        }

        throw new ProcessException(flowFile + " not found in queue");
    }

    @Override
    public void remove(final Collection<FlowFile> flowFiles) {
        for (final FlowFile flowFile : flowFiles) {
            validateState(flowFile);
        }

        for (final FlowFile flowFile : flowFiles) {
            remove(flowFile);
        }
    }

    @Override
    public MockFlowFile removeAllAttributes(final FlowFile flowFile, final Set<String> attrNames) {
        validateState(flowFile);
        if (attrNames == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        newFlowFile.removeAttributes(attrNames);
        return newFlowFile;
    }

    @Override
    public MockFlowFile removeAllAttributes(final FlowFile flowFile, final Pattern keyPattern) {
        validateState(flowFile);
        if (flowFile == null) {
            throw new IllegalArgumentException("flowFile cannot be null");
        }
        if (keyPattern == null) {
            return (MockFlowFile) flowFile;
        }

        final Set<String> attrsToRemove = new HashSet<>();
        for (final String key : flowFile.getAttributes().keySet()) {
            if (keyPattern.matcher(key).matches()) {
                attrsToRemove.add(key);
            }
        }

        return removeAllAttributes(flowFile, attrsToRemove);
    }

    @Override
    public MockFlowFile removeAttribute(final FlowFile flowFile, final String attrName) {
        validateState(flowFile);
        if (attrName == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;
        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        final Set<String> attrNames = new HashSet<>();
        attrNames.add(attrName);
        newFlowFile.removeAttributes(attrNames);
        return newFlowFile;
    }

    @Override
    public void rollback() {
        rollback(false);
    }

    @Override
    public void rollback(final boolean penalize) {
        //if we've already committed then rollback is basically a no-op
        if(committed){
            return;
        }
        final List<InputStream> openStreamCopy = new ArrayList<>(openInputStreams); // avoid ConcurrentModificationException by creating a copy of the List
        for (final InputStream openInputStream : openStreamCopy) {
            try {
                openInputStream.close();
            } catch (final IOException e) {
            }
        }

        for (final List<MockFlowFile> list : transferMap.values()) {
            for (final MockFlowFile flowFile : list) {
                processorQueue.offer(flowFile);
                if (penalize) {
                    penalized.add(flowFile);
                }
            }
        }

        for (final Long flowFileId : beingProcessed) {
            final MockFlowFile flowFile = originalVersions.get(flowFileId);
            if (flowFile != null) {
                processorQueue.offer(flowFile);
                if (penalize) {
                    penalized.add(flowFile);
                }
            }
        }

        rolledback = true;
        beingProcessed.clear();
        currentVersions.clear();
        originalVersions.clear();
        transferMap.clear();
        clearTransferState();
        if (!penalize) {
            penalized.clear();
        }
    }

    @Override
    public void transfer(final FlowFile flowFile) {
        validateState(flowFile);
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("I only accept MockFlowFile");
        }

        beingProcessed.remove(flowFile.getId());
        processorQueue.offer((MockFlowFile) flowFile);
    }

    @Override
    public void transfer(final Collection<FlowFile> flowFiles) {
        for (final FlowFile flowFile : flowFiles) {
            transfer(flowFile);
        }
    }

    @Override
    public void transfer(final FlowFile flowFile, final Relationship relationship) {
        if (relationship == Relationship.SELF) {
            transfer(flowFile);
            return;
        }
        if(!processor.getRelationships().contains(relationship)){
            throw new IllegalArgumentException("this relationship " + relationship.getName() + " is not known");
        }

        validateState(flowFile);
        List<MockFlowFile> list = transferMap.get(relationship);
        if (list == null) {
            list = new ArrayList<>();
            transferMap.put(relationship, list);
        }

        beingProcessed.remove(flowFile.getId());
        list.add((MockFlowFile) flowFile);
    }

    @Override
    public void transfer(final Collection<FlowFile> flowFiles, final Relationship relationship) {
        if (relationship == Relationship.SELF) {
            transfer(flowFiles);
            return;
        }
        if(!processor.getRelationships().contains(relationship)){
            throw new IllegalArgumentException("this relationship " + relationship.getName() + " is not known");
        }

        for (final FlowFile flowFile : flowFiles) {
            validateState(flowFile);
        }

        List<MockFlowFile> list = transferMap.get(relationship);
        if (list == null) {
            list = new ArrayList<>();
            transferMap.put(relationship, list);
        }

        for (final FlowFile flowFile : flowFiles) {
            beingProcessed.remove(flowFile.getId());
            list.add((MockFlowFile) flowFile);
        }
    }

    @Override
    public MockFlowFile write(final FlowFile flowFile, final OutputStreamCallback callback) {
        validateState(flowFile);
        if (callback == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            callback.process(baos);
        } catch (final IOException e) {
            throw new ProcessException(e.toString(), e);
        }

        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        newFlowFile.setData(baos.toByteArray());
        return newFlowFile;
    }

    @Override
    public FlowFile append(final FlowFile flowFile, final OutputStreamCallback callback) {
        validateState(flowFile);
        if (callback == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(mock.getData());
            callback.process(baos);
        } catch (final IOException e) {
            throw new ProcessException(e.toString(), e);
        }

        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        newFlowFile.setData(baos.toByteArray());
        return newFlowFile;
    }

    @Override
    public MockFlowFile write(final FlowFile flowFile, final StreamCallback callback) {
        validateState(flowFile);
        if (callback == null || flowFile == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
        if (!(flowFile instanceof MockFlowFile)) {
            throw new IllegalArgumentException("Cannot export a flow file that I did not create");
        }
        final MockFlowFile mock = (MockFlowFile) flowFile;

        final ByteArrayInputStream in = new ByteArrayInputStream(mock.getData());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            callback.process(in, out);
        } catch (final IOException e) {
            throw new ProcessException(e.toString(), e);
        }

        final MockFlowFile newFlowFile = new MockFlowFile(mock.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        newFlowFile.setData(out.toByteArray());

        return newFlowFile;
    }

    private byte[] readFully(final InputStream in) throws IOException {
        final byte[] buffer = new byte[4096];
        int len;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((len = in.read(buffer)) >= 0) {
            baos.write(buffer, 0, len);
        }

        return baos.toByteArray();
    }

    public List<MockFlowFile> getFlowFilesForRelationship(final Relationship relationship) {
        List<MockFlowFile> list = this.transferMap.get(relationship);
        if (list == null) {
            list = new ArrayList<>();
        }

        return list;
    }

    public List<MockFlowFile> getPenalizedFlowFiles() {
        return penalized;
    }

    /**
     * @param relationship to get flowfiles for
     * @return a List of FlowFiles in the order in which they were transferred
     *         to the given relationship
     */
    public List<MockFlowFile> getFlowFilesForRelationship(final String relationship) {
        final Relationship procRel = new Relationship.Builder().name(relationship).build();
        return getFlowFilesForRelationship(procRel);
    }

    public MockFlowFile createFlowFile(final File file) throws IOException {
        return createFlowFile(Files.readAllBytes(file.toPath()));
    }

    public MockFlowFile createFlowFile(final byte[] data) {
        final MockFlowFile flowFile = create();
        flowFile.setData(data);
        return flowFile;
    }

    public MockFlowFile createFlowFile(final byte[] data, final Map<String, String> attrs) {
        final MockFlowFile ff = createFlowFile(data);
        ff.putAttributes(attrs);
        return ff;
    }

    @Override
    public MockFlowFile merge(Collection<FlowFile> sources, FlowFile destination, byte[] header, byte[] footer, byte[] demarcator) {
        for (final FlowFile flowFile : sources) {
            validateState(flowFile);
        }
        validateState(destination);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            if (header != null) {
                baos.write(header);
            }

            int count = 0;
            for (final FlowFile flowFile : sources) {
                baos.write(((MockFlowFile) flowFile).getData());
                if (demarcator != null && ++count != sources.size()) {
                    baos.write(demarcator);
                }
            }

            if (footer != null) {
                baos.write(footer);
            }
        } catch (final IOException e) {
            throw new AssertionError("failed to write data to BAOS");
        }

        final MockFlowFile newFlowFile = new MockFlowFile(destination.getId(), destination);
        newFlowFile.setData(baos.toByteArray());
        currentVersions.put(newFlowFile.getId(), newFlowFile);

        return newFlowFile;
    }

    private void validateState(final FlowFile flowFile) {
        Objects.requireNonNull(flowFile);
        final FlowFile currentVersion = currentVersions.get(flowFile.getId());
        if (currentVersion == null) {
            throw new FlowFileHandlingException(flowFile + " is not known in this session");
        }

        if (currentVersion != flowFile) {
            throw new FlowFileHandlingException(flowFile + " is not the most recent version of this flow file within this session");
        }

        for (final List<MockFlowFile> flowFiles : transferMap.values()) {
            if (flowFiles.contains(flowFile)) {
                throw new IllegalStateException(flowFile + " has already been transferred");
            }
        }
    }

    /**
     * Inherits the attributes from the given source flow file into another flow
     * file. The UUID of the source becomes the parent UUID of this flow file.
     * If a parent uuid had previously been established it will be replaced by
     * the uuid of the given source
     *
     * @param source the FlowFile from which to copy attributes
     * @param destination the FlowFile to which to copy attributes
     */
    private FlowFile inheritAttributes(final FlowFile source, final FlowFile destination) {
        if (source == null || destination == null || source == destination) {
            return destination; // don't need to inherit from ourselves
        }
        final FlowFile updated = putAllAttributes(destination, source.getAttributes());
        getProvenanceReporter().fork(source, Collections.singletonList(updated));
        return updated;
    }

    /**
     * Inherits the attributes from the given source flow files into the
     * destination flow file. The UUIDs of the sources becomes the parent UUIDs
     * of the destination flow file. Only attributes which is common to all
     * source items is copied into this flow files attributes. Any previously
     * established parent UUIDs will be replaced by the UUIDs of the sources. It
     * will capture the uuid of a certain number of source objects and may not
     * capture all of them. How many it will capture is unspecified.
     *
     * @param sources to inherit common attributes from
     */
    private FlowFile inheritAttributes(final Collection<FlowFile> sources, final FlowFile destination) {
        final StringBuilder parentUuidBuilder = new StringBuilder();
        int uuidsCaptured = 0;
        for (final FlowFile source : sources) {
            if (source == destination) {
                continue; // don't want to capture parent uuid of this. Something can't be a child of itself
            }
            final String sourceUuid = source.getAttribute(CoreAttributes.UUID.key());
            if (sourceUuid != null && !sourceUuid.trim().isEmpty()) {
                uuidsCaptured++;
                if (parentUuidBuilder.length() > 0) {
                    parentUuidBuilder.append(",");
                }
                parentUuidBuilder.append(sourceUuid);
            }

            if (uuidsCaptured > 100) {
                break;
            }
        }

        final FlowFile updated = putAllAttributes(destination, intersectAttributes(sources));
        getProvenanceReporter().join(sources, updated);
        return updated;
    }

    /**
     * Returns the attributes that are common to every flow file given. The key
     * and value must match exactly.
     *
     * @param flowFileList a list of flow files
     *
     * @return the common attributes
     */
    private static Map<String, String> intersectAttributes(final Collection<FlowFile> flowFileList) {
        final Map<String, String> result = new HashMap<>();
        // trivial cases
        if (flowFileList == null || flowFileList.isEmpty()) {
            return result;
        } else if (flowFileList.size() == 1) {
            result.putAll(flowFileList.iterator().next().getAttributes());
        }

        /*
         * Start with the first attribute map and only put an entry to the
         * resultant map if it is common to every map.
         */
        final Map<String, String> firstMap = flowFileList.iterator().next().getAttributes();

        outer: for (final Map.Entry<String, String> mapEntry : firstMap.entrySet()) {
            final String key = mapEntry.getKey();
            final String value = mapEntry.getValue();
            for (final FlowFile flowFile : flowFileList) {
                final Map<String, String> currMap = flowFile.getAttributes();
                final String curVal = currMap.get(key);
                if (curVal == null || !curVal.equals(value)) {
                    continue outer;
                }
            }
            result.put(key, value);
        }

        return result;
    }

    /**
     * Assert that {@link #commit()} has been called
     */
    public void assertCommitted() {
        Assert.assertTrue("Session was not committed", committed);
    }

    /**
     * Assert that {@link #commit()} has not been called
     */
    public void assertNotCommitted() {
        Assert.assertFalse("Session was committed", committed);
    }

    /**
     * Assert that {@link #rollback()} has been called
     */
    public void assertRolledBack() {
        Assert.assertTrue("Session was not rolled back", rolledback);
    }

    /**
     * Assert that {@link #rollback()} has not been called
     */
    public void assertNotRolledBack() {
        Assert.assertFalse("Session was rolled back", rolledback);
    }

    /**
     * Assert that the number of FlowFiles transferred to the given relationship
     * is equal to the given count
     *
     * @param relationship to validate transfer count of
     * @param count items transfer to given relationship
     */
    public void assertTransferCount(final Relationship relationship, final int count) {
        final int transferCount = getFlowFilesForRelationship(relationship).size();
        Assert.assertEquals("Expected " + count + " FlowFiles to be transferred to "
            + relationship + " but actual transfer count was " + transferCount, count, transferCount);
    }

    /**
     * Assert that the number of FlowFiles transferred to the given relationship
     * is equal to the given count
     *
     * @param relationship to validate transfer count of
     * @param count items transfer to given relationship
     */
    public void assertTransferCount(final String relationship, final int count) {
        assertTransferCount(new Relationship.Builder().name(relationship).build(), count);
    }

    /**
     * Assert that there are no FlowFiles left on the input queue.
     */
    public void assertQueueEmpty() {
        Assert.assertTrue("FlowFile Queue has " + this.processorQueue.size() + " items", this.processorQueue.isEmpty());
    }

    /**
     * Assert that at least one FlowFile is on the input queue
     */
    public void assertQueueNotEmpty() {
        Assert.assertFalse("FlowFile Queue is empty", this.processorQueue.isEmpty());
    }

    /**
     * Asserts that all FlowFiles that were transferred were transferred to the
     * given relationship
     *
     * @param relationship to check for transferred flow files
     */
    public void assertAllFlowFilesTransferred(final String relationship) {
        assertAllFlowFilesTransferred(new Relationship.Builder().name(relationship).build());
    }

    /**
     * Asserts that all FlowFiles that were transferred were transferred to the
     * given relationship
     *
     * @param relationship to validate
     */
    public void assertAllFlowFilesTransferred(final Relationship relationship) {
        for (final Map.Entry<Relationship, List<MockFlowFile>> entry : transferMap.entrySet()) {
            final Relationship rel = entry.getKey();
            final List<MockFlowFile> flowFiles = entry.getValue();

            if (!rel.equals(relationship) && flowFiles != null && !flowFiles.isEmpty()) {
                Assert.fail("Expected all Transferred FlowFiles to go to " + relationship + " but " + flowFiles.size() + " were routed to " + rel);
            }
        }
    }

    /**
     * Asserts that all FlowFiles that were transferred are compliant with the
     * given validator.
     *
     * @param validator validator to use
     */
    public void assertAllFlowFiles(FlowFileValidator validator) {
        for (final Map.Entry<Relationship, List<MockFlowFile>> entry : transferMap.entrySet()) {
            final List<MockFlowFile> flowFiles = entry.getValue();
            for (MockFlowFile mockFlowFile : flowFiles) {
                validator.assertFlowFile(mockFlowFile);
            }
        }
    }

    /**
     * Asserts that all FlowFiles that were transferred in the given relationship
     * are compliant with the given validator.
     *
     * @param validator validator to use
     */
    public void assertAllFlowFiles(Relationship relationship, FlowFileValidator validator) {
        for (final Map.Entry<Relationship, List<MockFlowFile>> entry : transferMap.entrySet()) {
            final List<MockFlowFile> flowFiles = entry.getValue();
            final Relationship rel = entry.getKey();
            for (MockFlowFile mockFlowFile : flowFiles) {
                if(rel.equals(relationship)) {
                    validator.assertFlowFile(mockFlowFile);
                }
            }
        }
    }

    /**
     * Removes all state information about FlowFiles that have been transferred
     */
    public void clearTransferState() {
        this.transferMap.clear();
    }

    /**
     * Asserts that all FlowFiles that were transferred were transferred to the
     * given relationship and that the number of FlowFiles transferred is equal
     * to <code>count</code>
     *
     * @param relationship to validate
     * @param count number of items sent to that relationship (expected)
     */
    public void assertAllFlowFilesTransferred(final Relationship relationship, final int count) {
        assertAllFlowFilesTransferred(relationship);
        assertTransferCount(relationship, count);
    }

    /**
     * Asserts that all FlowFiles that were transferred were transferred to the
     * given relationship and that the number of FlowFiles transferred is equal
     * to <code>count</code>
     *
     * @param relationship to validate
     * @param count number of items sent to that relationship (expected)
     */
    public void assertAllFlowFilesTransferred(final String relationship, final int count) {
        assertAllFlowFilesTransferred(new Relationship.Builder().name(relationship).build(), count);
    }

    /**
     * @return the number of FlowFiles that were removed
     */
    public int getRemovedCount() {
        return removedCount;
    }

    @Override
    public ProvenanceReporter getProvenanceReporter() {
        return provenanceReporter;
    }

    @Override
    public MockFlowFile penalize(final FlowFile flowFile) {
        validateState(flowFile);
        final MockFlowFile mockFlowFile = (MockFlowFile) flowFile;
        final MockFlowFile newFlowFile = new MockFlowFile(mockFlowFile.getId(), flowFile);
        currentVersions.put(newFlowFile.getId(), newFlowFile);
        newFlowFile.setPenalized();
        penalized.add(newFlowFile);
        return newFlowFile;
    }

    public byte[] getContentAsByteArray(final MockFlowFile flowFile) {
        validateState(flowFile);
        return flowFile.getData();
    }

    /**
     * Checks if a FlowFile is known in this session.
     *
     * @param flowFile
     *            the FlowFile to check
     * @return <code>true</code> if the FlowFile is known in this session,
     *         <code>false</code> otherwise.
     */
    boolean isFlowFileKnown(final FlowFile flowFile) {
        final FlowFile curFlowFile = currentVersions.get(flowFile.getId());
        if (curFlowFile == null) {
            return false;
        }

        final String curUuid = curFlowFile.getAttribute(CoreAttributes.UUID.key());
        final String providedUuid = curFlowFile.getAttribute(CoreAttributes.UUID.key());
        if (!curUuid.equals(providedUuid)) {
            return false;
        }

        return true;
    }
}
