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

/* global nf */

nf.ProcessorConfiguration = (function () {

    // possible values for a processor's run duration (in millis)
    var RUN_DURATION_VALUES = [0, 25, 50, 100, 250, 500, 1000, 2000];

    /**
     * Gets the available scheduling strategies based on the specified processor.
     *
     * @param {type} processor
     * @returns {Array}
     */
    var getSchedulingStrategies = function (processor) {
        var strategies = [{
            text: 'Timer driven',
            value: 'TIMER_DRIVEN',
            description: 'Processor will be scheduled to run on an interval defined by the run schedule.'
        }];

        // conditionally support event driven based on processor
        if (processor.supportsEventDriven === true) {
            strategies.push({
                text: 'Event driven',
                value: 'EVENT_DRIVEN',
                description: 'Processor will be scheduled to run when triggered by an event (e.g. a FlowFile enters an incoming queue). This scheduling strategy is experimental.'
            });
        } else if (processor.config['schedulingStrategy'] === 'EVENT_DRIVEN') {
            // the processor was once configured for event driven but no longer supports it
            strategies.push({
                text: 'Event driven',
                value: 'EVENT_DRIVEN',
                description: 'Processor will be scheduled to run when triggered by an event (e.g. a FlowFile enters an incoming queue). This scheduling strategy is experimental.',
                disabled: true
            });
        }

        // conditionally support event driven
        if (nf.Canvas.isClustered()) {
            strategies.push({
                text: 'On primary node',
                value: 'PRIMARY_NODE_ONLY',
                description: 'Processor will be scheduled on the primary node on an interval defined by the run schedule.'
            });
        } else if (processor.config['schedulingStrategy'] === 'PRIMARY_NODE_ONLY') {
            strategies.push({
                text: 'On primary node',
                value: 'PRIMARY_NODE_ONLY',
                description: 'Processor will be scheduled on the primary node on an interval defined by the run schedule.',
                disabled: true
            });
        }

        // add an option for cron driven
        strategies.push({
            text: 'CRON driven',
            value: 'CRON_DRIVEN',
            description: 'Processor will be scheduled to run on at specific times based on the specified CRON string.'
        });

        return strategies;
    };

    /**
     * Handle any expected processor configuration errors.
     *
     * @argument {object} xhr       The XmlHttpRequest
     * @argument {string} status    The status of the request
     * @argument {string} error     The error
     */
    var handleProcessorConfigurationError = function (xhr, status, error) {
        if (xhr.status === 400) {
            var errors = xhr.responseText.split('\n');

            var content;
            if (errors.length === 1) {
                content = $('<span></span>').text(errors[0]);
            } else {
                content = nf.Common.formatUnorderedList(errors);
            }

            nf.Dialog.showOkDialog({
                dialogContent: content,
                headerText: 'Processor Configuration'
            });
        } else {
            nf.Common.handleAjaxError(xhr, status, error);
        }
    };

    /**
     * Creates an option for the specified relationship name.
     *
     * @argument {object} relationship      The relationship
     */
    var createRelationshipOption = function (relationship) {
        var relationshipLabel = $('<div class="relationship-name ellipsis"></div>').text(relationship.name);
        var relationshipValue = $('<span class="relationship-name-value hidden"></span>').text(relationship.name);

        // build the relationship checkbox element
        var relationshipCheckbox = $('<div class="processor-relationship nf-checkbox"></div>');
        if (relationship.autoTerminate === true) {
            relationshipCheckbox.addClass('checkbox-checked');
        } else {
            relationshipCheckbox.addClass('checkbox-unchecked');
        }

        // build the relationship container element
        var relationshipContainerElement = $('<div class="processor-relationship-container"></div>').append(relationshipCheckbox).append(relationshipLabel).append(relationshipValue).appendTo('#auto-terminate-relationship-names');
        if (!nf.Common.isBlank(relationship.description)) {
            var relationshipDescription = $('<div class="relationship-description"></div>').text(relationship.description);
            relationshipContainerElement.append(relationshipDescription);
        }

        return relationshipContainerElement;
    };

    /**
     * Determines whether the user has made any changes to the processor configuration
     * that needs to be saved.
     */
    var isSaveRequired = function () {
        var details = $('#processor-configuration').data('processorDetails');

        // determine if any processor settings have changed

        // consider auto terminated relationships
        var autoTerminatedChanged = false;
        var autoTerminated = marshalRelationships();
        $.each(details.relationships, function (i, relationship) {
            if (relationship.autoTerminate === true) {
                // relationship was auto terminated but is no longer selected
                if ($.inArray(relationship.name, autoTerminated) === -1) {
                    autoTerminatedChanged = true;
                    return false;
                }
            } else if (relationship.autoTerminate === false) {
                // relationship was not auto terminated but is now selected
                if ($.inArray(relationship.name, autoTerminated) >= 0) {
                    autoTerminatedChanged = true;
                    return false;
                }
            }
        });
        if (autoTerminatedChanged) {
            return true;
        }

        // consider the scheduling strategy
        var schedulingStrategy = $('#scheduling-strategy-combo').combo('getSelectedOption').value;
        if (schedulingStrategy !== (details.config['schedulingStrategy'] + '')) {
            return true;
        }

        // only consider the concurrent tasks if appropriate
        if (details.supportsParallelProcessing === true) {
            // get the appropriate concurrent tasks field
            var concurrentTasks;
            if (schedulingStrategy === 'EVENT_DRIVEN') {
                concurrentTasks = $('#event-driven-concurrently-schedulable-tasks');
            } else if (schedulingStrategy === 'CRON_DRIVEN') {
                concurrentTasks = $('#cron-driven-concurrently-schedulable-tasks');
            } else {
                concurrentTasks = $('#timer-driven-concurrently-schedulable-tasks');
            }

            // check the concurrent tasks
            if (concurrentTasks.val() !== (details.config['concurrentlySchedulableTaskCount'] + '')) {
                return true;
            }
        }

        // get the appropriate scheduling period field
        var schedulingPeriod;
        if (schedulingStrategy === 'CRON_DRIVEN') {
            schedulingPeriod = $('#cron-driven-scheduling-period');
        } else if (schedulingStrategy !== 'EVENT_DRIVEN') {
            schedulingPeriod = $('#timer-driven-scheduling-period');
        }

        // check the scheduling period
        if (nf.Common.isDefinedAndNotNull(schedulingPeriod) && schedulingPeriod.val() !== (details.config['schedulingPeriod'] + '')) {
            return true;
        }

        if ($('#processor-name').val() !== details['name']) {
            return true;
        }
        if ($('#processor-enabled').hasClass('checkbox-checked') && details['state'] === 'DISABLED') {
            return true;
        } else if ($('#processor-enabled').hasClass('checkbox-unchecked') && (details['state'] === 'RUNNING' || details['state'] === 'STOPPED')) {
            return true;
        }
        if ($('#penalty-duration').val() !== (details.config['penaltyDuration'] + '')) {
            return true;
        }
        if ($('#yield-duration').val() !== (details.config['yieldDuration'] + '')) {
            return true;
        }
        if ($('#bulletin-level-combo').combo('getSelectedOption').value !== (details.config['bulletinLevel'] + '')) {
            return true;
        }
        if ($('#processor-comments').val() !== details.config['comments']) {
            return true;
        }

        // defer to the property and relationship grids
        return $('#processor-properties').propertytable('isSaveRequired');
    };

    /**
     * Marshals the data that will be used to update the processor's configuration.
     */
    var marshalDetails = function () {
        // create the config dto
        var processorConfigDto = {};

        // get the scheduling strategy
        var schedulingStrategy = $('#scheduling-strategy-combo').combo('getSelectedOption').value;

        // get the appropriate concurrent tasks field
        var concurrentTasks;
        if (schedulingStrategy === 'EVENT_DRIVEN') {
            concurrentTasks = $('#event-driven-concurrently-schedulable-tasks');
        } else if (schedulingStrategy === 'CRON_DRIVEN') {
            concurrentTasks = $('#cron-driven-concurrently-schedulable-tasks');
        } else {
            concurrentTasks = $('#timer-driven-concurrently-schedulable-tasks');
        }

        // get the concurrent tasks if appropriate
        if (!concurrentTasks.is(':disabled')) {
            processorConfigDto['concurrentlySchedulableTaskCount'] = concurrentTasks.val();
        }

        // get the appropriate scheduling period field
        var schedulingPeriod;
        if (schedulingStrategy === 'CRON_DRIVEN') {
            schedulingPeriod = $('#cron-driven-scheduling-period');
        } else if (schedulingStrategy !== 'EVENT_DRIVEN') {
            schedulingPeriod = $('#timer-driven-scheduling-period');
        }

        // get the scheduling period if appropriate
        if (nf.Common.isDefinedAndNotNull(schedulingPeriod)) {
            processorConfigDto['schedulingPeriod'] = schedulingPeriod.val();
        }

        processorConfigDto['penaltyDuration'] = $('#penalty-duration').val();
        processorConfigDto['yieldDuration'] = $('#yield-duration').val();
        processorConfigDto['bulletinLevel'] = $('#bulletin-level-combo').combo('getSelectedOption').value;
        processorConfigDto['schedulingStrategy'] = schedulingStrategy;
        processorConfigDto['comments'] = $('#processor-comments').val();

        // run duration
        if ($('#run-duration-setting-container').is(':visible')) {
            var runDurationIndex = $('#run-duration-slider').slider('value');
            processorConfigDto['runDurationMillis'] = RUN_DURATION_VALUES[runDurationIndex];
        }

        // relationships
        processorConfigDto['autoTerminatedRelationships'] = marshalRelationships();

        // properties
        var properties = $('#processor-properties').propertytable('marshalProperties');

        // set the properties
        if ($.isEmptyObject(properties) === false) {
            processorConfigDto['properties'] = properties;
        }

        // create the processor dto
        var processorDto = {};
        processorDto['id'] = $('#processor-id').text();
        processorDto['name'] = $('#processor-name').val();
        processorDto['config'] = processorConfigDto;

        // mark the processor disabled if appropriate
        if ($('#processor-enabled').hasClass('checkbox-unchecked')) {
            processorDto['state'] = 'DISABLED';
        } else if ($('#processor-enabled').hasClass('checkbox-checked')) {
            processorDto['state'] = 'STOPPED';
        }

        // create the processor entity
        var processorEntity = {};
        processorEntity['component'] = processorDto;

        // return the marshaled details
        return processorEntity;
    };

    /**
     * Marshals the relationships that will be auto terminated.
     **/
    var marshalRelationships = function () {
        // get all available relationships
        var availableRelationships = $('#auto-terminate-relationship-names');
        var selectedRelationships = [];

        // go through each relationship to determine which are selected
        $.each(availableRelationships.children(), function (i, relationshipElement) {
            var relationship = $(relationshipElement);

            // get each relationship and its corresponding checkbox
            var relationshipCheck = relationship.children('div.processor-relationship');

            // see if this relationship has been selected
            if (relationshipCheck.hasClass('checkbox-checked')) {
                selectedRelationships.push(relationship.children('span.relationship-name-value').text());
            }
        });

        return selectedRelationships;
    };

    /**
     * Validates the specified details.
     *
     * @argument {object} details       The details to validate
     */
    var validateDetails = function (details) {
        var errors = [];
        var processor = details['component'];
        var config = processor['config'];

        // ensure numeric fields are specified correctly
        if (nf.Common.isDefinedAndNotNull(config['concurrentlySchedulableTaskCount']) && !$.isNumeric(config['concurrentlySchedulableTaskCount'])) {
            errors.push('Concurrent tasks must be an integer value');
        }
        if (nf.Common.isDefinedAndNotNull(config['schedulingPeriod']) && nf.Common.isBlank(config['schedulingPeriod'])) {
            errors.push('Run schedule must be specified');
        }
        if (nf.Common.isBlank(config['penaltyDuration'])) {
            errors.push('Penalty duration must be specified');
        }
        if (nf.Common.isBlank(config['yieldDuration'])) {
            errors.push('Yield duration must be specified');
        }

        if (errors.length > 0) {
            nf.Dialog.showOkDialog({
                dialogContent: nf.Common.formatUnorderedList(errors),
                headerText: 'Processor Configuration'
            });
            return false;
        } else {
            return true;
        }
    };

    /**
     * Reloads the outgoing connections for the specified processor.
     *
     * @param {object} processor
     */
    var reloadProcessorConnections = function (processor) {
        var connections = nf.Connection.getComponentConnections(processor.id);
        $.each(connections, function (_, connection) {
            if (connection.permissions.canRead) {
                if (connection.sourceId === processor.id) {
                    nf.Connection.reload(connection.id);
                }
            }
        });
    };

    /**
     * Goes to a service configuration from the property table.
     */
    var goToServiceFromProperty = function () {
        return $.Deferred(function (deferred) {
            // close all fields currently being edited
            $('#processor-properties').propertytable('saveRow');

            // determine if changes have been made
            if (isSaveRequired()) {
                // see if those changes should be saved
                nf.Dialog.showYesNoDialog({
                    headerText: 'Processor Configuration',
                    dialogContent: 'Save changes before going to this Controller Service?',
                    noHandler: function () {
                        deferred.resolve();
                    },
                    yesHandler: function () {
                        var processor = $('#processor-configuration').data('processorDetails');
                        saveProcessor(processor).done(function () {
                            deferred.resolve();
                        }).fail(function () {
                            deferred.reject();
                        });
                    }
                });
            } else {
                deferred.resolve();
            }
        }).promise();
    };

    /**
     *
     * @param {type} processor
     * @returns {undefined}
     */
    var saveProcessor = function (processor) {
        // marshal the settings and properties and update the processor
        var updatedProcessor = marshalDetails();

        // ensure details are valid as far as we can tell
        if (validateDetails(updatedProcessor)) {
            // set the revision
            var d = nf.Processor.get(processor.id);
            updatedProcessor['revision'] = nf.Client.getRevision(d);

            // update the selected component
            return $.ajax({
                type: 'PUT',
                data: JSON.stringify(updatedProcessor),
                url: d.uri,
                dataType: 'json',
                contentType: 'application/json'
            }).done(function (response) {
                // set the new processor state based on the response
                nf.Processor.set(response);
            }).fail(handleProcessorConfigurationError);
        } else {
            return $.Deferred(function (deferred) {
                deferred.reject();
            }).promise();
        }
    };

    return {
        /**
         * Initializes the processor properties tab.
         */
        init: function () {
            // initialize the properties tabs
            $('#processor-configuration-tabs').tabbs({
                tabStyle: 'tab',
                selectedTabStyle: 'selected-tab',
                scrollableTabContentStyle: 'scrollable',
                tabs: [{
                    name: 'Settings',
                    tabContentId: 'processor-standard-settings-tab-content'
                }, {
                    name: 'Scheduling',
                    tabContentId: 'processor-scheduling-tab-content'
                }, {
                    name: 'Properties',
                    tabContentId: 'processor-properties-tab-content'
                }, {
                    name: 'Comments',
                    tabContentId: 'processor-comments-tab-content'
                }],
                select: function () {
                    // remove all property detail dialogs
                    nf.UniversalCapture.removeAllPropertyDetailDialogs();

                    // update the processor property table size in case this is the first time its rendered
                    if ($(this).text() === 'Properties') {
                        $('#processor-properties').propertytable('resetTableSize');
                    }

                    // close all fields currently being edited
                    $('#processor-properties').propertytable('saveRow');

                    // show the border around the processor relationships if necessary
                    var processorRelationships = $('#auto-terminate-relationship-names');
                    if (processorRelationships.is(':visible') && processorRelationships.get(0).scrollHeight > processorRelationships.innerHeight()) {
                        processorRelationships.css('border-width', '1px');
                    }
                }
            });

            // initialize the processor configuration dialog
            $('#processor-configuration').modal({
                scrollableContentStyle: 'scrollable',
                headerText: 'Configure Processor',
                handler: {
                    close: function () {
                        // empty the relationship list
                        $('#auto-terminate-relationship-names').css('border-width', '0').empty();

                        // cancel any active edits and clear the table
                        $('#processor-properties').propertytable('cancelEdit').propertytable('clear');

                        // removed the cached processor details
                        $('#processor-configuration').removeData('processorDetails');
                    },
                    open: function () {
                        nf.Common.toggleScrollable($('#' + this.find('.tab-container').attr('id') + '-content').get(0));
                    }
                }
            });

            // initialize the bulletin combo
            $('#bulletin-level-combo').combo({
                options: [{
                    text: 'DEBUG',
                    value: 'DEBUG'
                }, {
                    text: 'INFO',
                    value: 'INFO'
                }, {
                    text: 'WARN',
                    value: 'WARN'
                }, {
                    text: 'ERROR',
                    value: 'ERROR'
                }]
            });

            // initialize the run duration slider
            $('#run-duration-slider').slider({
                min: 0,
                max: RUN_DURATION_VALUES.length - 1,
                change: function (event, ui) {
                    var processor = $('#processor-configuration').data('processorDetails');
                    if (ui.value > 0 && (processor.inputRequirement === 'INPUT_FORBIDDEN' || processor.inputRequirement === 'INPUT_ALLOWED')) {
                        $('#run-duration-data-loss').show();
                    } else {
                        $('#run-duration-data-loss').hide();
                    }
                }
            });

            // initialize the property table
            $('#processor-properties').propertytable({
                readOnly: false,
                dialogContainer: '#new-processor-property-container',
                descriptorDeferred: function (propertyName) {
                    var processor = $('#processor-configuration').data('processorDetails');
                    var d = nf.Processor.get(processor.id);
                    return $.ajax({
                        type: 'GET',
                        url: d.uri + '/descriptors',
                        data: {
                            propertyName: propertyName
                        },
                        dataType: 'json'
                    }).fail(nf.Common.handleAjaxError);
                },
                goToServiceDeferred: goToServiceFromProperty
            });
        },

        /**
         * Shows the configuration dialog for the specified processor.
         *
         * @argument {selection} selection      The selection
         */
        showConfiguration: function (selection) {
            if (nf.CanvasUtils.isProcessor(selection)) {
                var selectionData = selection.datum();

                // get the processor details
                var processor = selectionData.component;

                var requests = [];

                // reload the processor in case an property descriptors have updated
                requests.push(nf.Processor.reload(processor.id));

                // get the processor history
                requests.push($.ajax({
                    type: 'GET',
                    url: '../nifi-api/flow/history/components/' + encodeURIComponent(processor.id),
                    dataType: 'json'
                }));

                // once everything is loaded, show the dialog
                $.when.apply(window, requests).done(function (processorResult, historyResult) {
                    // get the updated processor'
                    var processorResponse = processorResult[0];
                    processor = processorResponse.component;
                    
                    // get the processor history
                    var processorHistory = historyResult[0].componentHistory;

                    // record the processor details
                    $('#processor-configuration').data('processorDetails', processor);

                    // determine if the enabled checkbox is checked or not
                    var processorEnableStyle = 'checkbox-checked';
                    if (processor['state'] === 'DISABLED') {
                        processorEnableStyle = 'checkbox-unchecked';
                    }

                    // populate the processor settings
                    $('#processor-id').text(processor['id']);
                    $('#processor-type').text(nf.Common.substringAfterLast(processor['type'], '.'));
                    $('#processor-name').val(processor['name']);
                    $('#processor-enabled').removeClass('checkbox-unchecked checkbox-checked').addClass(processorEnableStyle);
                    $('#penalty-duration').val(processor.config['penaltyDuration']);
                    $('#yield-duration').val(processor.config['yieldDuration']);
                    $('#processor-comments').val(processor.config['comments']);

                    // set the run duration if applicable
                    if (processor.supportsBatching === true) {
                        $('#run-duration-setting-container').show();

                        // set the run duration slider value
                        var runDuration = RUN_DURATION_VALUES.indexOf(processor.config['runDurationMillis']);
                        $('#run-duration-slider').slider('value', runDuration);
                    } else {
                        $('#run-duration-setting-container').hide();
                    }

                    // select the appropriate bulletin level
                    $('#bulletin-level-combo').combo('setSelectedOption', {
                        value: processor.config['bulletinLevel']
                    });

                    // initialize the scheduling strategy
                    $('#scheduling-strategy-combo').combo({
                        options: getSchedulingStrategies(processor),
                        selectedOption: {
                            value: processor.config['schedulingStrategy']
                        },
                        select: function (selectedOption) {
                            // show the appropriate panel
                            if (selectedOption.value === 'EVENT_DRIVEN') {
                                $('#event-driven-warning').show();

                                $('#timer-driven-options').hide();
                                $('#event-driven-options').show();
                                $('#cron-driven-options').hide();
                            } else {
                                $('#event-driven-warning').hide();

                                if (selectedOption.value === 'CRON_DRIVEN') {
                                    $('#timer-driven-options').hide();
                                    $('#event-driven-options').hide();
                                    $('#cron-driven-options').show();
                                } else {
                                    $('#timer-driven-options').show();
                                    $('#event-driven-options').hide();
                                    $('#cron-driven-options').hide();
                                }
                            }
                        }
                    });

                    // initialize the concurrentTasks
                    var defaultConcurrentTasks = processor.config['defaultConcurrentTasks'];
                    $('#timer-driven-concurrently-schedulable-tasks').val(defaultConcurrentTasks['TIMER_DRIVEN']);
                    $('#event-driven-concurrently-schedulable-tasks').val(defaultConcurrentTasks['EVENT_DRIVEN']);
                    $('#cron-driven-concurrently-schedulable-tasks').val(defaultConcurrentTasks['CRON_DRIVEN']);

                    // get the appropriate concurrent tasks field
                    var concurrentTasks;
                    if (processor.config['schedulingStrategy'] === 'EVENT_DRIVEN') {
                        concurrentTasks = $('#event-driven-concurrently-schedulable-tasks').val(processor.config['concurrentlySchedulableTaskCount']);
                    } else if (processor.config['schedulingStrategy'] === 'CRON_DRIVEN') {
                        concurrentTasks = $('#cron-driven-concurrently-schedulable-tasks').val(processor.config['concurrentlySchedulableTaskCount']);
                    } else {
                        concurrentTasks = $('#timer-driven-concurrently-schedulable-tasks').val(processor.config['concurrentlySchedulableTaskCount']);
                    }

                    // conditionally allow the user to specify the concurrent tasks
                    if (nf.Common.isDefinedAndNotNull(concurrentTasks)) {
                        if (processor.supportsParallelProcessing === true) {
                            concurrentTasks.prop('disabled', false);
                        } else {
                            concurrentTasks.prop('disabled', true);
                        }
                    }

                    // initialize the schedulingStrategy
                    var defaultSchedulingPeriod = processor.config['defaultSchedulingPeriod'];
                    $('#cron-driven-scheduling-period').val(defaultSchedulingPeriod['CRON_DRIVEN']);
                    $('#timer-driven-scheduling-period').val(defaultSchedulingPeriod['TIMER_DRIVEN']);

                    // set the scheduling period as appropriate
                    if (processor.config['schedulingStrategy'] === 'CRON_DRIVEN') {
                        $('#cron-driven-scheduling-period').val(processor.config['schedulingPeriod']);
                    } else if (processor.config['schedulingStrategy'] !== 'EVENT_DRIVEN') {
                        $('#timer-driven-scheduling-period').val(processor.config['schedulingPeriod']);
                    }

                    // load the relationship list
                    if (!nf.Common.isEmpty(processor.relationships)) {
                        $.each(processor.relationships, function (i, relationship) {
                            createRelationshipOption(relationship);
                        });
                    } else {
                        $('#auto-terminate-relationship-names').append('<div class="unset">This processor has no relationships.</div>');
                    }

                    var buttons = [{
                        buttonText: 'Apply',
                        color: {
                            base: '#728E9B',
                            hover: '#004849',
                            text: '#ffffff'
                        },
                        handler: {
                            click: function () {
                                // close all fields currently being edited
                                $('#processor-properties').propertytable('saveRow');

                                // save the processor
                                saveProcessor(processor).done(function (response) {
                                    // reload the processor's outgoing connections
                                    reloadProcessorConnections(processor);

                                    // close the details panel
                                    $('#processor-configuration').modal('hide');

                                    // inform Angular app values have changed
                                    nf.ng.Bridge.digest();
                                });
                            }
                        }
                    },
                        {
                            buttonText: 'Cancel',
                            color: {
                                base: '#E3E8EB',
                                hover: '#C7D2D7',
                                text: '#004849'
                            },
                            handler: {
                                click: function () {
                                    $('#processor-configuration').modal('hide');
                                }
                            }
                        }];

                    // determine if we should show the advanced button
                    if (nf.Common.isDefinedAndNotNull(processor.config.customUiUrl) && processor.config.customUiUrl !== '') {
                        buttons.push({
                            buttonText: 'Advanced',
                            clazz: 'fa fa-cog button-icon',
                            color: {
                                base: '#E3E8EB',
                                hover: '#C7D2D7',
                                text: '#004849'
                            },
                            handler: {
                                click: function () {
                                    var openCustomUi = function () {
                                        // reset state and close the dialog manually to avoid hiding the faded background
                                        $('#processor-configuration').modal('hide');

                                        // show the custom ui
                                        nf.CustomUi.showCustomUi(processorResponse, processor.config.customUiUrl, true).done(function () {
                                            // once the custom ui is closed, reload the processor
                                            nf.Processor.reload(processor.id);

                                            // and reload the processor's outgoing connections
                                            reloadProcessorConnections(processor);
                                        });
                                    };

                                    // close all fields currently being edited
                                    $('#processor-properties').propertytable('saveRow');

                                    // determine if changes have been made
                                    if (isSaveRequired()) {
                                        // see if those changes should be saved
                                        nf.Dialog.showYesNoDialog({
                                            headerText: 'Save',
                                            dialogContent: 'Save changes before opening the advanced configuration?',
                                            noHandler: openCustomUi,
                                            yesHandler: function () {
                                                saveProcessor(processor).done(function (deferred) {
                                                    // open the custom ui
                                                    openCustomUi();
                                                });
                                            }
                                        });
                                    } else {
                                        // if there were no changes, simply open the custom ui
                                        openCustomUi();
                                    }
                                }
                            }
                        });
                    }

                    // set the button model
                    $('#processor-configuration').modal('setButtonModel', buttons);

                    // load the property table
                    $('#processor-properties')
                        .propertytable('setGroupId', processor.parentGroupId)
                        .propertytable('loadProperties', processor.config.properties, processor.config.descriptors, processorHistory.propertyHistory);

                    // show the details
                    $('#processor-configuration').modal('show');

                    // add ellipsis if necessary
                    $('#processor-configuration div.relationship-name').ellipsis();

                    // show the border if necessary
                    var processorRelationships = $('#auto-terminate-relationship-names');
                    if (processorRelationships.is(':visible') && processorRelationships.get(0).scrollHeight > processorRelationships.innerHeight()) {
                        processorRelationships.css('border-width', '1px');
                    }
                }).fail(nf.Common.handleAjaxError);
            }
        }
    };
}());
