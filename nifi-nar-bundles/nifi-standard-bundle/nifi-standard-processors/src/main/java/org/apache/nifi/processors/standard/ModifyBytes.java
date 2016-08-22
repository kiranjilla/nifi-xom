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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.StopWatch;

@EventDriven
@SideEffectFree
@Tags({"binary", "discard", "keep"})
@InputRequirement(Requirement.INPUT_REQUIRED)
@CapabilityDescription("Discard byte range at the start and end or all content of a binary file.")
public class ModifyBytes extends AbstractProcessor {

    // Relationships
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Processed flowfiles.")
            .build();
    private final Set<Relationship> relationships;
    public static final PropertyDescriptor START_OFFSET = new PropertyDescriptor.Builder()
            .name("Start Offset")
            .description("Number of bytes removed at the beginning of the file.")
            .required(true)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .defaultValue("0 B")
            .build();
    public static final PropertyDescriptor END_OFFSET = new PropertyDescriptor.Builder()
            .name("End Offset")
            .description("Number of bytes removed at the end of the file.")
            .required(true)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .defaultValue("0 B")
            .build();
    public static final PropertyDescriptor REMOVE_ALL = new PropertyDescriptor.Builder()
            .name("Remove All Content")
            .description("Remove all content from the FlowFile superseding Start Offset and End Offset properties.")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("false")
            .build();
    private final List<PropertyDescriptor> propDescriptors;

    public ModifyBytes() {
        HashSet<Relationship> r = new HashSet<>();
        r.add(REL_SUCCESS);
        relationships = Collections.unmodifiableSet(r);

        ArrayList<PropertyDescriptor> pds = new ArrayList<>();
        pds.add(START_OFFSET);
        pds.add(END_OFFSET);
        pds.add(REMOVE_ALL);
        propDescriptors = Collections.unmodifiableList(pds);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return propDescriptors;
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile ff = session.get();
        if (null == ff) {
            return;
        }

        final ComponentLog logger = getLogger();

        final long startOffset = context.getProperty(START_OFFSET).asDataSize(DataUnit.B).longValue();
        final long endOffset = context.getProperty(END_OFFSET).asDataSize(DataUnit.B).longValue();
        final boolean removeAll = context.getProperty(REMOVE_ALL).asBoolean();
        final long newFileSize = removeAll ? 0L : ff.getSize() - startOffset - endOffset;

        final StopWatch stopWatch = new StopWatch(true);
        if (newFileSize <= 0) {
            ff = session.write(ff, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    out.write(new byte[0]);
                }
            });
        } else {
            ff = session.write(ff, new StreamCallback() {
                @Override
                public void process(final InputStream in, final OutputStream out) throws IOException {
                    in.skip(startOffset);
                    StreamUtils.copy(in, out, newFileSize);
                }
            });
        }

        logger.info("Transferred {} to 'success'", new Object[]{ff});
        session.getProvenanceReporter().modifyContent(ff, stopWatch.getElapsed(TimeUnit.MILLISECONDS));
        session.transfer(ff, REL_SUCCESS);
    }
}
