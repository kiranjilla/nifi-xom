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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"json", "split", "jsonpath"})
@InputRequirement(Requirement.INPUT_REQUIRED)
@CapabilityDescription("Splits a JSON File into multiple, separate FlowFiles for an array element specified by a JsonPath expression. "
        + "Each generated FlowFile is comprised of an element of the specified array and transferred to relationship 'split,' "
        + "with the original file transferred to the 'original' relationship. If the specified JsonPath is not found or "
        + "does not evaluate to an array element, the original file is routed to 'failure' and no files are generated.")
@WritesAttributes({
        @WritesAttribute(attribute = "fragment.identifier",
                description = "All split FlowFiles produced from the same parent FlowFile will have the same randomly generated UUID added for this attribute"),
        @WritesAttribute(attribute = "fragment.index",
                description = "A one-up number that indicates the ordering of the split FlowFiles that were created from a single parent FlowFile"),
        @WritesAttribute(attribute = "fragment.count",
                description = "The number of split FlowFiles generated from the parent FlowFile"),
        @WritesAttribute(attribute = "segment.original.filename ", description = "The filename of the parent FlowFile")
})

public class SplitJson extends AbstractJsonPathProcessor {

    public static final PropertyDescriptor ARRAY_JSON_PATH_EXPRESSION = new PropertyDescriptor.Builder()
            .name("JsonPath Expression")
            .description("A JsonPath expression that indicates the array element to split into JSON/scalar fragments.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR) // Full validation/caching occurs in #customValidate
            .required(true)
            .build();

    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("The original FlowFile that was split into segments. If the FlowFile fails processing, nothing will be sent to "
                    + "this relationship")
            .build();
    public static final Relationship REL_SPLIT = new Relationship.Builder()
            .name("split")
            .description("All segments of the original FlowFile will be routed to this relationship")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If a FlowFile fails processing for any reason (for example, the FlowFile is not valid JSON or the specified "
                    + "path does not exist), it will be routed to this relationship")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    private final AtomicReference<JsonPath> JSON_PATH_REF = new AtomicReference<>();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(ARRAY_JSON_PATH_EXPRESSION);
        properties.add(NULL_VALUE_DEFAULT_REPRESENTATION);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_ORIGINAL);
        relationships.add(REL_SPLIT);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
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
    public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
        if (descriptor.equals(ARRAY_JSON_PATH_EXPRESSION)) {
            if (!StringUtils.equals(oldValue, newValue)) {
                if (oldValue != null) {
                    // clear the cached item
                    JSON_PATH_REF.set(null);
                }
            }
        }
    }

    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
        JsonPathValidator validator = new JsonPathValidator() {
            @Override
            public void cacheComputedValue(String subject, String input, JsonPath computedJson) {
                JSON_PATH_REF.set(computedJson);
            }

            @Override
            public boolean isStale(String subject, String input) {
                return JSON_PATH_REF.get() == null;
            }
        };

        String value = validationContext.getProperty(ARRAY_JSON_PATH_EXPRESSION).getValue();
        return Collections.singleton(validator.validate(ARRAY_JSON_PATH_EXPRESSION.getName(), value, validationContext));
    }

    @Override
    public void onTrigger(final ProcessContext processContext, final ProcessSession processSession) {
        final FlowFile original = processSession.get();
        if (original == null) {
            return;
        }

        final ComponentLog logger = getLogger();

        DocumentContext documentContext = null;
        try {
            documentContext = validateAndEstablishJsonContext(processSession, original);
        } catch (InvalidJsonException e) {
            logger.error("FlowFile {} did not have valid JSON content.", new Object[]{original});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        final JsonPath jsonPath = JSON_PATH_REF.get();
        String representationOption = processContext.getProperty(NULL_VALUE_DEFAULT_REPRESENTATION).getValue();
        final String nullDefaultValue = NULL_REPRESENTATION_MAP.get(representationOption);

        final List<FlowFile> segments = new ArrayList<>();

        Object jsonPathResult;
        try {
            jsonPathResult = documentContext.read(jsonPath);
        } catch (PathNotFoundException e) {
            logger.warn("JsonPath {} could not be found for FlowFile {}", new Object[]{jsonPath.getPath(), original});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        if (!(jsonPathResult instanceof List)) {
            logger.error("The evaluated value {} of {} was not a JSON Array compatible type and cannot be split.", new Object[]{jsonPathResult, jsonPath.getPath()});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        List resultList = (List) jsonPathResult;
        AtomicInteger jsonLineCount = new AtomicInteger(0);

        final String fragmentIdentifier = UUID.randomUUID().toString();
        for (int i = 0; i < resultList.size(); i++) {
            Object resultSegment = resultList.get(i);
            FlowFile split = processSession.create(original);
            split = processSession.write(split, (out) -> {
                        String resultSegmentContent = getResultRepresentation(resultSegment, nullDefaultValue);
                        out.write(resultSegmentContent.getBytes(StandardCharsets.UTF_8));
                    }
            );
            split = processSession.putAttribute(split, "fragment.identifier", fragmentIdentifier);
            split = processSession.putAttribute(split, "fragment.index", Integer.toString(i));
            split = processSession.putAttribute(split, "segment.original.filename", split.getAttribute(CoreAttributes.FILENAME.key()));
            segments.add(split);
            jsonLineCount.incrementAndGet();
        }

        segments.forEach((segment) -> {
            segment = processSession.putAttribute(segment, "fragment.count", Integer.toString(jsonLineCount.get()));
            processSession.transfer(segment, REL_SPLIT);
        });

        processSession.transfer(original, REL_ORIGINAL);
        logger.info("Split {} into {} FlowFiles", new Object[]{original, segments.size()});
    }
}
