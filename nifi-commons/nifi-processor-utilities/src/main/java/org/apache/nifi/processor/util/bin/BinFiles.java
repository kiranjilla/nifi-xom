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
package org.apache.nifi.processor.util.bin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.FlowFileSessionWrapper;
import org.apache.nifi.processor.util.StandardValidators;

/**
 * Base class for file-binning processors.
 *
 */
public abstract class BinFiles extends AbstractSessionFactoryProcessor {

    public static final PropertyDescriptor MIN_SIZE = new PropertyDescriptor.Builder()
            .name("Minimum Group Size")
            .description("The minimum size of for the bundle")
            .required(true)
            .defaultValue("0 B")
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .build();
    public static final PropertyDescriptor MAX_SIZE = new PropertyDescriptor.Builder()
            .name("Maximum Group Size")
            .description("The maximum size for the bundle. If not specified, there is no maximum.")
            .required(false)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .build();

    public static final PropertyDescriptor MIN_ENTRIES = new PropertyDescriptor.Builder()
            .name("Minimum Number of Entries")
            .description("The minimum number of files to include in a bundle")
            .required(true)
            .defaultValue("1")
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .build();
    public static final PropertyDescriptor MAX_ENTRIES = new PropertyDescriptor.Builder()
            .name("Maximum Number of Entries")
            .description("The maximum number of files to include in a bundle. If not specified, there is no maximum.")
            .required(false)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .build();

    public static final PropertyDescriptor MAX_BIN_COUNT = new PropertyDescriptor.Builder()
            .name("Maximum number of Bins")
            .description("Specifies the maximum number of bins that can be held in memory at any one time")
            .defaultValue("100")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .build();

    public static final PropertyDescriptor MAX_BIN_AGE = new PropertyDescriptor.Builder()
            .name("Max Bin Age")
            .description("The maximum age of a Bin that will trigger a Bin to be complete. Expected format is <duration> <time unit> "
                    + "where <duration> is a positive integer and time unit is one of seconds, minutes, hours")
            .required(false)
            .addValidator(StandardValidators.createTimePeriodValidator(1, TimeUnit.SECONDS, Integer.MAX_VALUE, TimeUnit.SECONDS))
            .build();

    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("The FlowFiles that were used to create the bundle")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If the bundle cannot be created, all FlowFiles that would have been used to created the bundle will be transferred to failure")
            .build();

    private final BinManager binManager = new BinManager();
    private final Queue<Bin> readyBins = new LinkedBlockingQueue<>();

    @OnStopped
    public final void resetState() {
        binManager.purge();

        Bin bin;
        while ((bin = readyBins.poll()) != null) {
            for (final FlowFileSessionWrapper wrapper : bin.getContents()) {
                wrapper.getSession().rollback();
            }
        }
    }

    /**
     * Allows general pre-processing of a flow file before it is offered to a bin. This is called before getGroupId().
     *
     * @param context context
     * @param session session
     * @param flowFile flowFile
     * @return The flow file, possibly altered
     */
    protected abstract FlowFile preprocessFlowFile(final ProcessContext context, final ProcessSession session, final FlowFile flowFile);

    /**
     * Returns a group ID representing a bin. This allows flow files to be binned into like groups.
     *
     * @param context context
     * @param flowFile flowFile
     * @return The appropriate group ID
     */
    protected abstract String getGroupId(final ProcessContext context, final FlowFile flowFile);

    /**
     * Performs any additional setup of the bin manager. Called during the OnScheduled phase.
     *
     * @param binManager The bin manager
     * @param context context
     */
    protected abstract void setUpBinManager(BinManager binManager, ProcessContext context);

    /**
     * Processes a single bin. Implementing class is responsible for committing each session
     *
     * @param unmodifiableBin A reference to a single bin of flow file/session wrappers
     * @param binContents A copy of the contents of the bin
     * @param context The context
     * @param session The session that created the bin
     * @return Return true if the input bin was already committed. E.g., in case of a failure, the implementation may choose to transfer all binned files to Failure and commit their sessions. If
     * false, the processBins() method will transfer the files to Original and commit the sessions
     *
     * @throws ProcessException if any problem arises while processing a bin of FlowFiles. All flow files in the bin will be transferred to failure and the ProcessSession provided by the 'session'
     * argument rolled back
     */
    protected abstract boolean processBin(Bin unmodifiableBin, List<FlowFileSessionWrapper> binContents, ProcessContext context, ProcessSession session) throws ProcessException;

    /**
     * Allows additional custom validation to be done. This will be called from the parent's customValidation method.
     *
     * @param context The context
     * @return Validation results indicating problems
     */
    protected Collection<ValidationResult> additionalCustomValidation(final ValidationContext context) {
        return new ArrayList<>();
    }

    @Override
    public final void onTrigger(final ProcessContext context, final ProcessSessionFactory sessionFactory) throws ProcessException {
        final int flowFilesBinned = binFlowFiles(context, sessionFactory);
        getLogger().debug("Binned {} FlowFiles", new Object[]{flowFilesBinned});

        if (!isScheduled()) {
            return;
        }

        final int binsMigrated = migrateBins(context);
        final int binsProcessed = processBins(context, sessionFactory);
        //If we accomplished nothing then let's yield
        if (flowFilesBinned == 0 && binsMigrated == 0 && binsProcessed == 0) {
            context.yield();
        }
    }

    private int migrateBins(final ProcessContext context) {
        int added = 0;
        for (final Bin bin : binManager.removeReadyBins(true)) {
            this.readyBins.add(bin);
            added++;
        }

        // if we have created all of the bins that are allowed, go ahead and remove the oldest one. If we don't do
        // this, then we will simply wait for it to expire because we can't get any more FlowFiles into the
        // bins. So we may as well expire it now.
        if (added == 0 && binManager.getBinCount() >= context.getProperty(MAX_BIN_COUNT).asInteger()) {
            final Bin bin = binManager.removeOldestBin();
            if (bin != null) {
                added++;
                this.readyBins.add(bin);
            }
        }
        return added;
    }

    private int processBins(final ProcessContext context, final ProcessSessionFactory sessionFactory) {
        final Bin bin = readyBins.poll();
        if (bin == null) {
            return 0;
        }

        final List<Bin> bins = new ArrayList<>();
        bins.add(bin);

        final ProcessorLog logger = getLogger();
        final ProcessSession session = sessionFactory.createSession();

        final List<FlowFileSessionWrapper> binCopy = new ArrayList<>(bin.getContents());

        boolean binAlreadyCommitted = false;
        try {
            binAlreadyCommitted = this.processBin(bin, binCopy, context, session);
        } catch (final ProcessException e) {
            logger.error("Failed to process bundle of {} files due to {}", new Object[]{binCopy.size(), e});

            for (final FlowFileSessionWrapper wrapper : binCopy) {
                wrapper.getSession().transfer(wrapper.getFlowFile(), REL_FAILURE);
                wrapper.getSession().commit();
            }
            session.rollback();
            return 1;
        } catch (final Exception e) {
            logger.error("Failed to process bundle of {} files due to {}; rolling back sessions", new Object[] {binCopy.size(), e});

            for (final FlowFileSessionWrapper wrapper : binCopy) {
                wrapper.getSession().rollback();
            }
            session.rollback();
            return 1;
        }

        // we first commit the bundle's session before the originals' sessions because if we are restarted or crash
        // between commits, we favor data redundancy over data loss. Since we have no Distributed Transaction capability
        // across multiple sessions, we cannot guarantee atomicity across the sessions
        session.commit();
        // If this bin's session has been committed, move on.
        if (!binAlreadyCommitted) {
            for (final FlowFileSessionWrapper wrapper : bin.getContents()) {
                wrapper.getSession().transfer(wrapper.getFlowFile(), REL_ORIGINAL);
                wrapper.getSession().commit();
            }
        }

        return 1;
    }

    private int binFlowFiles(final ProcessContext context, final ProcessSessionFactory sessionFactory) {
        int flowFilesBinned = 0;
        while (binManager.getBinCount() <= context.getProperty(MAX_BIN_COUNT).asInteger().intValue()) {
            if (!isScheduled()) {
                break;
            }

            final ProcessSession session = sessionFactory.createSession();
            FlowFile flowFile = session.get();
            if (flowFile == null) {
                break;
            }

            flowFile = this.preprocessFlowFile(context, session, flowFile);

            String groupId = this.getGroupId(context, flowFile);

            final boolean binned = binManager.offer(groupId, flowFile, session);

            // could not be added to a bin -- probably too large by itself, so create a separate bin for just this guy.
            if (!binned) {
                Bin bin = new Bin(0, Long.MAX_VALUE, 0, Integer.MAX_VALUE, null);
                bin.offer(flowFile, session);
                this.readyBins.add(bin);
            }

            flowFilesBinned++;
        }

        return flowFilesBinned;
    }

    @OnScheduled
    public final void onScheduled(final ProcessContext context) throws IOException {
        binManager.setMinimumSize(context.getProperty(MIN_SIZE).asDataSize(DataUnit.B).longValue());

        if (context.getProperty(MAX_BIN_AGE).isSet()) {
            binManager.setMaxBinAge(context.getProperty(MAX_BIN_AGE).asTimePeriod(TimeUnit.SECONDS).intValue());
        } else {
            binManager.setMaxBinAge(Integer.MAX_VALUE);
        }

        if (context.getProperty(MAX_SIZE).isSet()) {
            binManager.setMaximumSize(context.getProperty(MAX_SIZE).asDataSize(DataUnit.B).longValue());
        } else {
            binManager.setMaximumSize(Long.MAX_VALUE);
        }

        binManager.setMinimumEntries(context.getProperty(MIN_ENTRIES).asInteger());

        if (context.getProperty(MAX_ENTRIES).isSet()) {
            binManager.setMaximumEntries(context.getProperty(MAX_ENTRIES).asInteger().intValue());
        } else {
            binManager.setMaximumEntries(Integer.MAX_VALUE);
        }

        this.setUpBinManager(binManager, context);
    }

    @Override
    protected final Collection<ValidationResult> customValidate(final ValidationContext context) {
        final List<ValidationResult> problems = new ArrayList<>(super.customValidate(context));

        final long minBytes = context.getProperty(MIN_SIZE).asDataSize(DataUnit.B).longValue();
        final Double maxBytes = context.getProperty(MAX_SIZE).asDataSize(DataUnit.B);

        if (maxBytes != null && maxBytes.longValue() < minBytes) {
            problems.add(
                    new ValidationResult.Builder()
                    .subject(MIN_SIZE.getName())
                    .input(context.getProperty(MIN_SIZE).getValue())
                    .valid(false)
                    .explanation("Min Size must be less than or equal to Max Size")
                    .build()
            );
        }

        final Long min = context.getProperty(MIN_ENTRIES).asLong();
        final Long max = context.getProperty(MAX_ENTRIES).asLong();

        if (min != null && max != null) {
            if (min > max) {
                problems.add(
                        new ValidationResult.Builder().subject(MIN_ENTRIES.getName())
                        .input(context.getProperty(MIN_ENTRIES).getValue())
                        .valid(false)
                        .explanation("Min Entries must be less than or equal to Max Entries")
                        .build()
                );
            }
        }

        Collection<ValidationResult> otherProblems = this.additionalCustomValidation(context);
        if (otherProblems != null) {
            problems.addAll(otherProblems);
        }

        return problems;
    }
}
