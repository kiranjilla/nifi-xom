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

nf.ReportingTask = (function () {

    var config = {
        edit: 'edit',
        readOnly: 'read-only'
    };

    /**
     * Gets the controller services table.
     *
     * @returns {*|jQuery|HTMLElement}
     */
    var getControllerServicesTable = function () {
        return $('#controller-services-table');
    };
    
    /**
     * Handle any expected reporting task configuration errors.
     *
     * @argument {object} xhr       The XmlHttpRequest
     * @argument {string} status    The status of the request
     * @argument {string} error     The error
     */
    var handleReportingTaskConfigurationError = function (xhr, status, error) {
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
                headerText: 'Reporting Task'
            });
        } else {
            nf.Common.handleAjaxError(xhr, status, error);
        }
    };

    /**
     * Determines whether the user has made any changes to the reporting task configuration
     * that needs to be saved.
     */
    var isSaveRequired = function () {
        var entity = $('#reporting-task-configuration').data('reportingTaskDetails');

        // determine if any reporting task settings have changed

        if ($('#reporting-task-name').val() !== entity.component['name']) {
            return true;
        }
        if ($('#reporting-task-comments').val() !== entity.component['comments']) {
            return true;
        }
        if ($('#reporting-task-enabled').hasClass('checkbox-checked') && entity.component['state'] === 'DISABLED') {
            return true;
        } else if ($('#reporting-task-enabled').hasClass('checkbox-unchecked') && (entity.component['state'] === 'RUNNING' || entity.component['state'] === 'STOPPED')) {
            return true;
        }

        // consider the scheduling strategy
        var schedulingStrategy = $('#reporting-task-scheduling-strategy-combo').combo('getSelectedOption').value;
        if (schedulingStrategy !== (entity.component['schedulingStrategy'] + '')) {
            return true;
        }

        // get the appropriate scheduling period field
        var schedulingPeriod;
        if (schedulingStrategy === 'CRON_DRIVEN') {
            schedulingPeriod = $('#reporting-task-cron-driven-scheduling-period');
        } else {
            schedulingPeriod = $('#reporting-task-timer-driven-scheduling-period');
        }

        // check the scheduling period
        if (nf.Common.isDefinedAndNotNull(schedulingPeriod) && schedulingPeriod.val() !== (entity.component['schedulingPeriod'] + '')) {
            return true;
        }

        // defer to the properties
        return $('#reporting-task-properties').propertytable('isSaveRequired');
    };

    /**
     * Marshals the data that will be used to update the reporting task's configuration.
     */
    var marshalDetails = function () {
        // properties
        var properties = $('#reporting-task-properties').propertytable('marshalProperties');

        // get the scheduling strategy
        var schedulingStrategy = $('#reporting-task-scheduling-strategy-combo').combo('getSelectedOption').value;

        // get the appropriate scheduling period field
        var schedulingPeriod;
        if (schedulingStrategy === 'CRON_DRIVEN') {
            schedulingPeriod = $('#reporting-task-cron-driven-scheduling-period');
        } else {
            schedulingPeriod = $('#reporting-task-timer-driven-scheduling-period');
        }

        // create the reporting task dto
        var reportingTaskDto = {};
        reportingTaskDto['id'] = $('#reporting-task-id').text();
        reportingTaskDto['name'] = $('#reporting-task-name').val();
        reportingTaskDto['schedulingStrategy'] = schedulingStrategy;
        reportingTaskDto['schedulingPeriod'] = schedulingPeriod.val();
        reportingTaskDto['comments'] = $('#reporting-task-comments').val();

        // mark the processor disabled if appropriate
        if ($('#reporting-task-enabled').hasClass('checkbox-unchecked')) {
            reportingTaskDto['state'] = 'DISABLED';
        } else if ($('#reporting-task-enabled').hasClass('checkbox-checked')) {
            reportingTaskDto['state'] = 'STOPPED';
        }

        // set the properties
        if ($.isEmptyObject(properties) === false) {
            reportingTaskDto['properties'] = properties;
        }

        // create the reporting task entity
        var reportingTaskEntity = {};
        reportingTaskEntity['component'] = reportingTaskDto;

        // return the marshaled details
        return reportingTaskEntity;
    };

    /**
     * Validates the specified details.
     *
     * @argument {object} details       The details to validate
     */
    var validateDetails = function (details) {
        var errors = [];
        var reportingTask = details['component'];

        if (nf.Common.isBlank(reportingTask['schedulingPeriod'])) {
            errors.push('Run schedule must be specified');
        }

        if (errors.length > 0) {
            nf.Dialog.showOkDialog({
                dialogContent: nf.Common.formatUnorderedList(errors),
                headerText: 'Reporting Task'
            });
            return false;
        } else {
            return true;
        }
    };

    /**
     * Renders the specified reporting task.
     *
     * @param {object} reportingTask
     */
    var renderReportingTask = function (reportingTaskEntity) {
        // get the table and update the row accordingly
        var reportingTaskGrid = $('#reporting-tasks-table').data('gridInstance');
        var reportingTaskData = reportingTaskGrid.getData();
        var currentReportingTask = reportingTaskData.getItemById(reportingTaskEntity.id);
        reportingTaskData.updateItem(reportingTaskEntity.id, $.extend({
            bulletins: currentReportingTask.bulletins
        }, reportingTaskEntity));
    };

    /**
     *
     * @param {object} reportingTaskEntity
     * @param {boolean} running
     */
    var setRunning = function (reportingTaskEntity, running) {
        var entity = {
            'revision': nf.Client.getRevision(reportingTaskEntity),
            'component': {
                'id': reportingTaskEntity.id,
                'state': running === true ? 'RUNNING' : 'STOPPED'
            }
        };

        return $.ajax({
            type: 'PUT',
            url: reportingTaskEntity.uri,
            data: JSON.stringify(entity),
            dataType: 'json',
            contentType: 'application/json'
        }).done(function (response) {
            // update the task
            renderReportingTask(response);
            nf.ControllerService.reloadReferencedServices(getControllerServicesTable(), response.component);
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Goes to a service configuration from the property table.
     */
    var goToServiceFromProperty = function () {
        return $.Deferred(function (deferred) {
            // close all fields currently being edited
            $('#reporting-task-properties').propertytable('saveRow');

            // determine if changes have been made
            if (isSaveRequired()) {
                // see if those changes should be saved
                nf.Dialog.showYesNoDialog({
                    headerText: 'Save',
                    dialogContent: 'Save changes before going to this Controller Service?',
                    noHandler: function () {
                        deferred.resolve();
                    },
                    yesHandler: function () {
                        var reportingTask = $('#reporting-task-configuration').data('reportingTaskDetails');
                        saveReportingTask(reportingTask).done(function () {
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
     * Saves the specified reporting task.
     *
     * @param {type} reportingTask
     */
    var saveReportingTask = function (reportingTaskEntity) {
        // marshal the settings and properties and update the reporting task
        var updatedReportingTask = marshalDetails();

        // ensure details are valid as far as we can tell
        if (validateDetails(updatedReportingTask)) {
            updatedReportingTask['revision'] = nf.Client.getRevision(reportingTaskEntity);

            // update the selected component
            return $.ajax({
                type: 'PUT',
                data: JSON.stringify(updatedReportingTask),
                url: reportingTaskEntity.uri,
                dataType: 'json',
                contentType: 'application/json'
            }).done(function (response) {
                // update the reporting task
                renderReportingTask(response);
            }).fail(handleReportingTaskConfigurationError);
        } else {
            return $.Deferred(function (deferred) {
                deferred.reject();
            }).promise();
        }
    };

    /**
     * Gets a property descriptor for the controller service currently being configured.
     *
     * @param {type} propertyName
     */
    var getReportingTaskPropertyDescriptor = function (propertyName) {
        var details = $('#reporting-task-configuration').data('reportingTaskDetails');
        return $.ajax({
            type: 'GET',
            url: details.uri + '/descriptors',
            data: {
                propertyName: propertyName
            },
            dataType: 'json'
        }).fail(nf.Common.handleAjaxError);
    };

    return {
        /**
         * Initializes the reporting task configuration dialog.
         */
        init: function () {
            // initialize the configuration dialog tabs
            $('#reporting-task-configuration-tabs').tabbs({
                tabStyle: 'tab',
                selectedTabStyle: 'selected-tab',
                scrollableTabContentStyle: 'scrollable',
                tabs: [{
                    name: 'Settings',
                    tabContentId: 'reporting-task-standard-settings-tab-content'
                }, {
                    name: 'Properties',
                    tabContentId: 'reporting-task-properties-tab-content'
                }, {
                    name: 'Comments',
                    tabContentId: 'reporting-task-comments-tab-content'
                }],
                select: function () {
                    // remove all property detail dialogs
                    nf.UniversalCapture.removeAllPropertyDetailDialogs();

                    // update the property table size in case this is the first time its rendered
                    if ($(this).text() === 'Properties') {
                        $('#reporting-task-properties').propertytable('resetTableSize');
                    }

                    // close all fields currently being edited
                    $('#reporting-task-properties').propertytable('saveRow');
                }
            });

            // initialize the reporting task configuration dialog
            $('#reporting-task-configuration').data('mode', config.edit).modal({
                scrollableContentStyle: 'scrollable',
                headerText: 'Configure Reporting Task',
                handler: {
                    close: function () {
                        // cancel any active edits
                        $('#reporting-task-properties').propertytable('cancelEdit');

                        // clear the tables
                        $('#reporting-task-properties').propertytable('clear');

                        // clear the comments
                        nf.Common.clearField('read-only-reporting-task-comments');

                        // removed the cached reporting task details
                        $('#reporting-task-configuration').removeData('reportingTaskDetails');
                    },
                    open: function () {
                        nf.Common.toggleScrollable($('#' + this.find('.tab-container').attr('id') + '-content').get(0));
                    }
                }
            });

            // initialize the property table
            $('#reporting-task-properties').propertytable({
                readOnly: false,
                dialogContainer: '#new-reporting-task-property-container',
                descriptorDeferred: getReportingTaskPropertyDescriptor,
                goToServiceDeferred: goToServiceFromProperty
            });
        },

        /**
         * Shows the configuration dialog for the specified reporting task.
         *
         * @argument {reportingTask} reportingTaskEntity      The reporting task
         */
        showConfiguration: function (reportingTaskEntity) {
            var reportingTaskDialog = $('#reporting-task-configuration');
            if (reportingTaskDialog.data('mode') === config.readOnly) {
                // update the visibility
                $('#reporting-task-configuration .reporting-task-read-only').hide();
                $('#reporting-task-configuration .reporting-task-editable').show();

                // initialize the property table
                $('#reporting-task-properties').propertytable('destroy').propertytable({
                    readOnly: false,
                    dialogContainer: '#new-reporting-task-property-container',
                    descriptorDeferred: getReportingTaskPropertyDescriptor,
                    goToServiceDeferred: goToServiceFromProperty
                });

                // update the mode
                reportingTaskDialog.data('mode', config.edit);
            }

            // reload the task in case the property descriptors have changed
            var reloadTask = $.ajax({
                type: 'GET',
                url: reportingTaskEntity.uri,
                dataType: 'json'
            });

            // get the reporting task history
            var loadHistory = $.ajax({
                type: 'GET',
                url: '../nifi-api/flow/history/components/' + encodeURIComponent(reportingTaskEntity.id),
                dataType: 'json'
            });

            // once everything is loaded, show the dialog
            $.when(reloadTask, loadHistory).done(function (taskResponse, historyResponse) {
                // get the updated reporting task
                reportingTaskEntity = taskResponse[0];
                var reportingTask = reportingTaskEntity.component;

                // get the reporting task history
                var reportingTaskHistory = historyResponse[0].componentHistory;

                // record the reporting task details
                $('#reporting-task-configuration').data('reportingTaskDetails', reportingTaskEntity);

                // determine if the enabled checkbox is checked or not
                var reportingTaskEnableStyle = 'checkbox-checked';
                if (reportingTask['state'] === 'DISABLED') {
                    reportingTaskEnableStyle = 'checkbox-unchecked';
                }

                // populate the reporting task settings
                nf.Common.populateField('reporting-task-id', reportingTask['id']);
                nf.Common.populateField('reporting-task-type', nf.Common.substringAfterLast(reportingTask['type'], '.'));
                $('#reporting-task-name').val(reportingTask['name']);
                $('#reporting-task-enabled').removeClass('checkbox-unchecked checkbox-checked').addClass(reportingTaskEnableStyle);
                $('#reporting-task-comments').val(reportingTask['comments']);

                // get the default schedule period
                var defaultSchedulingPeriod = reportingTask['defaultSchedulingPeriod'];
                var cronSchedulingPeriod = $('#reporting-task-cron-driven-scheduling-period').val(defaultSchedulingPeriod['CRON_DRIVEN']);
                var timerSchedulingPeriod = $('#reporting-task-timer-driven-scheduling-period').val(defaultSchedulingPeriod['TIMER_DRIVEN']);

                // set the scheduling period as appropriate
                if (reportingTask['schedulingStrategy'] === 'CRON_DRIVEN') {
                    cronSchedulingPeriod.val(reportingTask['schedulingPeriod']);
                } else {
                    timerSchedulingPeriod.val(reportingTask['schedulingPeriod']);
                }

                // initialize the scheduling strategy
                $('#reporting-task-scheduling-strategy-combo').combo({
                    options: [{
                        text: 'Timer driven',
                        value: 'TIMER_DRIVEN',
                        description: 'Reporting task will be scheduled to run on an interval defined by the run schedule.'
                    }, {
                        text: 'CRON driven',
                        value: 'CRON_DRIVEN',
                        description: 'Reporting task will be scheduled to run on at specific times based on the specified CRON string.'
                    }],
                    selectedOption: {
                        value: reportingTask['schedulingStrategy']
                    },
                    select: function (selectedOption) {
                        if (selectedOption.value === 'CRON_DRIVEN') {
                            timerSchedulingPeriod.hide();
                            cronSchedulingPeriod.show();
                        } else {
                            timerSchedulingPeriod.show();
                            cronSchedulingPeriod.hide();
                        }
                    }
                });

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
                            $('#reporting-task-properties').propertytable('saveRow');

                                // save the reporting task
                                saveReportingTask(reportingTaskEntity).done(function (response) {
                                    // reload the reporting task
                                    nf.ControllerService.reloadReferencedServices(getControllerServicesTable(), response.component);

                                // close the details panel
                                $('#reporting-task-configuration').modal('hide');
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
                                $('#reporting-task-configuration').modal('hide');
                            }
                        }
                    }];

                // determine if we should show the advanced button
                if (nf.Common.isDefinedAndNotNull(reportingTask.customUiUrl) && reportingTask.customUiUrl !== '') {
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
                                    $('#reporting-task-configuration').modal('hide');

                                    // close the settings dialog since the custom ui is also opened in the shell
                                    $('#shell-close-button').click();

                                    // show the custom ui
                                    nf.CustomUi.showCustomUi(reportingTaskEntity, reportingTask.customUiUrl, true).done(function () {
                                        // once the custom ui is closed, reload the reporting task
                                        nf.ReportingTask.reload(reportingTaskEntity.id).done(function (response) {
                                            nf.ControllerService.reloadReferencedServices(getControllerServicesTable(), response.reportingTask);
                                        });

                                        // show the settings
                                        nf.Settings.showSettings();
                                    });
                                };

                                // close all fields currently being edited
                                $('#reporting-task-properties').propertytable('saveRow');

                                // determine if changes have been made
                                if (isSaveRequired()) {
                                    // see if those changes should be saved
                                    nf.Dialog.showYesNoDialog({
                                        headerText: 'Save',
                                        dialogContent: 'Save changes before opening the advanced configuration?',
                                        noHandler: openCustomUi,
                                        yesHandler: function () {
                                            saveReportingTask(reportingTaskEntity).done(function () {
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
                $('#reporting-task-configuration').modal('setButtonModel', buttons);

                // load the property table
                $('#reporting-task-properties')
                    .propertytable('setGroupId', reportingTask.parentGroupId)
                    .propertytable('loadProperties', reportingTask.properties, reportingTask.descriptors, reportingTaskHistory.propertyHistory);

                // show the details
                $('#reporting-task-configuration').modal('show');

                $('#reporting-task-properties').propertytable('resetTableSize');
            }).fail(nf.Common.handleAjaxError);
        },

        /**
         * Shows the reporting task details in a read only dialog.
         *
         * @param {object} reportingTaskEntity
         */
        showDetails: function (reportingTaskEntity) {
            var reportingTaskDialog = $('#reporting-task-configuration');
            if (reportingTaskDialog.data('mode') === config.edit) {
                // update the visibility
                $('#reporting-task-configuration .reporting-task-read-only').show();
                $('#reporting-task-configuration .reporting-task-editable').hide();

                // initialize the property table
                $('#reporting-task-properties').propertytable('destroy').propertytable({
                    readOnly: true
                });

                // update the mode
                reportingTaskDialog.data('mode', config.readOnly);
            }

            // reload the task in case the property descriptors have changed
            var reloadTask = $.ajax({
                type: 'GET',
                url: reportingTaskEntity.uri,
                dataType: 'json'
            });

            // get the reporting task history
            var loadHistory = $.ajax({
                type: 'GET',
                url: '../nifi-api/flow/history/components/' + encodeURIComponent(reportingTaskEntity.id),
                dataType: 'json'
            });

            // once everything is loaded, show the dialog
            $.when(reloadTask, loadHistory).done(function (taskResponse, historyResponse) {
                // get the updated reporting task
                reportingTaskEntity = taskResponse[0];
                var reportingTask = reportingTaskEntity.component;

                // get the reporting task history
                var reportingTaskHistory = historyResponse[0].componentHistory;

                // populate the reporting task settings
                nf.Common.populateField('reporting-task-id', reportingTask['id']);
                nf.Common.populateField('reporting-task-type', nf.Common.substringAfterLast(reportingTask['type'], '.'));
                nf.Common.populateField('read-only-reporting-task-name', reportingTask['name']);
                nf.Common.populateField('read-only-reporting-task-comments', reportingTask['comments']);

                // make the scheduling strategy human readable
                var schedulingStrategy = reportingTask['schedulingStrategy'];
                if (schedulingStrategy === 'CRON_DRIVEN') {
                    schedulingStrategy = 'CRON driven';
                } else {
                    schedulingStrategy = "Timer driven";
                }
                nf.Common.populateField('read-only-reporting-task-scheduling-strategy', schedulingStrategy);
                nf.Common.populateField('read-only-reporting-task-scheduling-period', reportingTask['schedulingPeriod']);

                var buttons = [{
                    buttonText: 'Ok',
                    color: {
                        base: '#728E9B',
                        hover: '#004849',
                        text: '#ffffff'
                    },
                    handler: {
                        click: function () {
                            // hide the dialog
                            reportingTaskDialog.modal('hide');
                        }
                    }
                }];

                // determine if we should show the advanced button
                if (nf.Common.isDefinedAndNotNull(nf.CustomUi) && nf.Common.isDefinedAndNotNull(reportingTask.customUiUrl) && reportingTask.customUiUrl !== '') {
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
                                // reset state and close the dialog manually to avoid hiding the faded background
                                reportingTaskDialog.modal('hide');

                                // close the settings dialog since the custom ui is also opened in the shell
                                $('#shell-close-button').click();

                                // show the custom ui
                                nf.CustomUi.showCustomUi(reportingTaskEntity, reportingTask.customUiUrl, false).done(function() {
                                    nf.Settings.showSettings();
                                });
                            }
                        }
                    });
                }

                // show the dialog
                reportingTaskDialog.modal('setButtonModel', buttons).modal('show');

                // load the property table
                $('#reporting-task-properties').propertytable('loadProperties', reportingTask.properties, reportingTask.descriptors, reportingTaskHistory.propertyHistory);

                // show the details
                reportingTaskDialog.modal('show');

                $('#reporting-task-properties').propertytable('resetTableSize');
            });
        },

        /**
         * Starts the specified reporting task.
         *
         * @param {object} reportingTaskEntity
         */
        start: function (reportingTaskEntity) {
            setRunning(reportingTaskEntity, true);
        },

        /**
         * Stops the specified reporting task.
         *
         * @param {object} reportingTaskEntity
         */
        stop: function (reportingTaskEntity) {
            setRunning(reportingTaskEntity, false);
        },

        /**
         * Reloads the specified reporting task.
         *
         * @param {string} id
         */
        reload: function (id) {
            var reportingTaskGrid = $('#reporting-tasks-table').data('gridInstance');
            var reportingTaskData = reportingTaskGrid.getData();
            var reportingTaskEntity = reportingTaskData.getItemById(id);

            return $.ajax({
                type: 'GET',
                url: reportingTaskEntity.uri,
                dataType: 'json'
            }).done(function (response) {
                renderReportingTask(response);
            }).fail(nf.Common.handleAjaxError);
        },

        /**
         * Deletes the specified reporting task.
         *
         * @param {object} reportingTaskEntity
         */
        remove: function (reportingTaskEntity) {
            // prompt for removal?

            var revision = nf.Client.getRevision(reportingTaskEntity);
            $.ajax({
                type: 'DELETE',
                url: reportingTaskEntity.uri + '?' + $.param({
                    version: revision.version,
                    clientId: revision.clientId
                }),
                dataType: 'json'
            }).done(function (response) {
                // remove the task
                var reportingTaskGrid = $('#reporting-tasks-table').data('gridInstance');
                var reportingTaskData = reportingTaskGrid.getData();
                reportingTaskData.deleteItem(reportingTaskEntity.id);
            }).fail(nf.Common.handleAjaxError);
        }
    };
}());
