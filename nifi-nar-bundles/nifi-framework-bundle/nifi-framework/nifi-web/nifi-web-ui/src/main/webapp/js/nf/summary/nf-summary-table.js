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
/* global nf, top, Slick */

nf.SummaryTable = (function () {

    /**
     * Configuration object used to hold a number of configuration items.
     */
    var config = {
        urls: {
            api: '../nifi-api',
            status: '../nifi-api/flow/process-groups/root/status',
            systemDiagnostics: '../nifi-api/system-diagnostics',
            flowConfig: '../nifi-api/flow/config'
        }
    };

    /**
     * Goes to the specified component if possible.
     *
     * @argument {string} groupId       The id of the group
     * @argument {string} componentId   The id of the component
     */
    var goTo = function (groupId, componentId) {
        // only attempt this if we're within a frame
        if (top !== window) {
            // and our parent has canvas utils and shell defined
            if (nf.Common.isDefinedAndNotNull(parent.nf) && nf.Common.isDefinedAndNotNull(parent.nf.CanvasUtils) && nf.Common.isDefinedAndNotNull(parent.nf.Shell)) {
                parent.nf.CanvasUtils.showComponent(groupId, componentId);
                parent.$('#shell-close-button').click();
            }
        }
    };

    /**
     * Initializes the summary table.
     *
     * @param {type} isClustered
     */
    var initSummaryTable = function (isClustered) {
        // define the function for filtering the list
        $('#summary-filter').keyup(function () {
            applyFilter();
        });

        // initialize the summary tabs
        $('#summary-tabs').tabbs({
            tabStyle: 'tab',
            selectedTabStyle: 'selected-tab',
            scrollableTabContentStyle: 'scrollable',
            tabs: [{
                name: 'Processors',
                tabContentId: 'processor-summary-tab-content'
            }, {
                name: 'Input Ports',
                tabContentId: 'input-port-summary-tab-content'
            }, {
                name: 'Output Ports',
                tabContentId: 'output-port-summary-tab-content'
            }, {
                name: 'Remote Process Groups',
                tabContentId: 'remote-process-group-summary-tab-content'
            }, {
                name: 'Connections',
                tabContentId: 'connection-summary-tab-content'
            }, {
                name: 'Process Groups',
                tabContentId: 'process-group-summary-tab-content'
            }],
            select: function () {
                var tab = $(this).text();
                if (tab === 'Processors') {
                    // ensure the processor table is sized properly
                    var processorsGrid = $('#processor-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(processorsGrid)) {
                        processorsGrid.resizeCanvas();

                        // update the total number of processors
                        $('#displayed-items').text(nf.Common.formatInteger(processorsGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(processorsGrid.getData().getLength()));
                    }

                    // update the combo for processors
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by name',
                            value: 'name'
                        }, {
                            text: 'by type',
                            value: 'type'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                } else if (tab === 'Connections') {
                    // ensure the connection table is size properly
                    var connectionsGrid = $('#connection-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(connectionsGrid)) {
                        connectionsGrid.resizeCanvas();

                        // update the total number of connections
                        $('#displayed-items').text(nf.Common.formatInteger(connectionsGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(connectionsGrid.getData().getLength()));
                    }

                    // update the combo for connections
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by source',
                            value: 'sourceName'
                        }, {
                            text: 'by name',
                            value: 'name'
                        }, {
                            text: 'by destination',
                            value: 'destinationName'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                } else if (tab === 'Input Ports') {
                    // ensure the connection table is size properly
                    var inputPortsGrid = $('#input-port-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(inputPortsGrid)) {
                        inputPortsGrid.resizeCanvas();

                        // update the total number of input ports
                        $('#displayed-items').text(nf.Common.formatInteger(inputPortsGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(inputPortsGrid.getData().getLength()));
                    }

                    // update the combo for input ports
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by name',
                            value: 'name'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                } else if (tab === 'Output Ports') {
                    // ensure the connection table is size properly
                    var outputPortsGrid = $('#output-port-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(outputPortsGrid)) {
                        outputPortsGrid.resizeCanvas();

                        // update the total number of output ports
                        $('#displayed-items').text(nf.Common.formatInteger(outputPortsGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(outputPortsGrid.getData().getLength()));
                    }

                    // update the combo for output ports
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by name',
                            value: 'name'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                } else if (tab === 'Remote Process Groups') {
                    // ensure the connection table is size properly
                    var remoteProcessGroupsGrid = $('#remote-process-group-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(remoteProcessGroupsGrid)) {
                        remoteProcessGroupsGrid.resizeCanvas();

                        // update the total number of remote process groups
                        $('#displayed-items').text(nf.Common.formatInteger(remoteProcessGroupsGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(remoteProcessGroupsGrid.getData().getLength()));
                    }

                    // update the combo for remote process groups
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by name',
                            value: 'name'
                        }, {
                            text: 'by uri',
                            value: 'targetUri'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                } else {
                    // ensure the connection table is size properly
                    var processGroupGrid = $('#process-group-summary-table').data('gridInstance');
                    if (nf.Common.isDefinedAndNotNull(processGroupGrid)) {
                        processGroupGrid.resizeCanvas();

                        // update the total number of process groups
                        $('#displayed-items').text(nf.Common.formatInteger(processGroupGrid.getData().getLength()));
                        $('#total-items').text(nf.Common.formatInteger(processGroupGrid.getData().getLength()));
                    }

                    // update the combo for process groups
                    $('#summary-filter-type').combo({
                        options: [{
                            text: 'by name',
                            value: 'name'
                        }],
                        select: function (option) {
                            applyFilter();
                        }
                    });
                }

                // reset the filter
                $('#summary-filter').val('');
                applyFilter();
            }
        });

        // define a custom formatter for showing more processor details
        var moreProcessorDetails = function (row, cell, value, columnDef, dataContext) {
            var markup = '<div title="View Processor Details" class="pointer show-processor-details fa fa-info-circle" style="margin-right: 3px;"></div>';

            // if there are bulletins, render them on the graph
            if (!nf.Common.isEmpty(dataContext.bulletins)) {
                markup += '<div class="has-bulletins fa fa-sticky-note-o"></div><span class="hidden row-id">' + nf.Common.escapeHtml(dataContext.id) + '</span>';
            }

            return markup;
        };

        // formatter for io
        var ioFormatter = function (row, cell, value, columnDef, dataContext) {
            return dataContext.read + ' / ' + dataContext.written;
        };

        // formatter for tasks
        var taskTimeFormatter = function (row, cell, value, columnDef, dataContext) {
            return nf.Common.formatInteger(dataContext.tasks) + ' / ' + dataContext.tasksDuration;
        };

        // function for formatting the last accessed time
        var valueFormatter = function (row, cell, value, columnDef, dataContext) {
            return nf.Common.formatValue(value);
        };

        // define a custom formatter for the run status column
        var runStatusFormatter = function (row, cell, value, columnDef, dataContext) {
            var activeThreadCount = '';
            if (nf.Common.isDefinedAndNotNull(dataContext.activeThreadCount) && dataContext.activeThreadCount > 0) {
                activeThreadCount = '(' + dataContext.activeThreadCount + ')';
            }
            var classes = nf.Common.escapeHtml(value.toLowerCase());
            switch(nf.Common.escapeHtml(value.toLowerCase())) {
                case 'running':
                    classes += ' fa fa-play';
                    break;
                case 'stopped':
                    classes += ' fa fa-stop';
                    break;
                case 'enabled':
                    classes += ' fa fa-flash';
                    break;
                case 'disabled':
                    classes += ' icon icon-enable-false';
                case 'invalid':
                    classes += ' fa fa-warning';
                    break;
                default:
                    classes += '';
            }
            var formattedValue = '<div layout="row"><div class="' + classes + '"></div>';
            return formattedValue + '<div class="status-text" style="margin-top: 4px;">' + nf.Common.escapeHtml(value) + '</div><div style="float: left; margin-left: 4px;">' + nf.Common.escapeHtml(activeThreadCount) + '</div></div>';
        };

        // define the input, read, written, and output columns (reused between both tables)
        var nameColumn = {id: 'name', field: 'name', name: 'Name', sortable: true, resizable: true};
        var runStatusColumn = {
            id: 'runStatus',
            field: 'runStatus',
            name: 'Run Status',
            formatter: runStatusFormatter,
            sortable: true
        };
        var inputColumn = {
            id: 'input',
            field: 'input',
            name: '<span class="input-title">In</span>&nbsp;/&nbsp;<span class="input-size-title">Size</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / data size in the last 5 min',
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };
        var ioColumn = {
            id: 'io',
            field: 'io',
            name: '<span class="read-title">Read</span>&nbsp;/&nbsp;<span class="written-title">Write</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Data size in the last 5 min',
            formatter: ioFormatter,
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };
        var outputColumn = {
            id: 'output',
            field: 'output',
            name: '<span class="output-title">Out</span>&nbsp;/&nbsp;<span class="output-size-title">Size</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / data size in the last 5 min',
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };
        var tasksTimeColumn = {
            id: 'tasks',
            field: 'tasks',
            name: '<span class="tasks-title">Tasks</span>&nbsp;/&nbsp;<span class="time-title">Time</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / duration in the last 5 min',
            formatter: taskTimeFormatter,
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };

        // define the column model for the processor summary table
        var processorsColumnModel = [
            {
                id: 'moreDetails',
                field: 'moreDetails',
                name: '&nbsp;',
                resizable: false,
                formatter: moreProcessorDetails,
                sortable: true,
                width: 50,
                maxWidth: 50,
                toolTip: 'Sorts based on presence of bulletins'
            },
            nameColumn,
            {id: 'type', field: 'type', name: 'Type', sortable: true, resizable: true},
            runStatusColumn,
            inputColumn,
            ioColumn,
            outputColumn,
            tasksTimeColumn
        ];

        // initialize the search field if applicable
        if (isClustered) {
            nf.ClusterSearch.init();
        }

        // determine if the this page is in the shell
        var isInShell = (top !== window);

        // add an action column if appropriate
        if (isClustered || isInShell || nf.Common.SUPPORTS_SVG) {
            // define how the column is formatted
            var processorActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Processor" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (nf.Common.SUPPORTS_SVG) {
                    markup += '<div class="pointer show-processor-status-history fa fa-area-chart" title="View Status History" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-processor-summary fa fa-cubes" title="View Processor Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            processorsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: processorActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the templates table
        var processorsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var processorsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        processorsData.setItems([]);
        processorsData.setFilterArgs({
            searchString: '',
            property: 'name'
        });
        processorsData.setFilter(filter);

        // initialize the sort
        sort('processor-summary-table', {
            columnId: 'name',
            sortAsc: true
        }, processorsData);

        // initialize the grid
        var processorsGrid = new Slick.Grid('#processor-summary-table', processorsData, processorsColumnModel, processorsOptions);
        processorsGrid.setSelectionModel(new Slick.RowSelectionModel());
        processorsGrid.registerPlugin(new Slick.AutoTooltips());
        processorsGrid.setSortColumn('name', true);
        processorsGrid.onSort.subscribe(function (e, args) {
            sort('processor-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, processorsData);
        });

        // configure a click listener
        processorsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = processorsData.getItem(args.row);

            // determine the desired action
            if (processorsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    goTo(item.groupId, item.id);
                } else if (target.hasClass('show-processor-status-history')) {
                    nf.StatusHistory.showProcessorChart(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-processor-summary')) {
                    // load the cluster processor summary
                    loadClusterProcessorSummary(item.groupId, item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-processor-summary-dialog').modal('show');

                    // resize after opening
                    clusterProcessorsGrid.resizeCanvas();
                }
            } else if (processorsGrid.getColumns()[args.cell].id === 'moreDetails') {
                if (target.hasClass('show-processor-details')) {
                    nf.ProcessorDetails.showDetails(item.groupId, item.id);
                }
            }
        });

        // wire up the dataview to the grid
        processorsData.onRowCountChanged.subscribe(function (e, args) {
            processorsGrid.updateRowCount();
            processorsGrid.render();

            // update the total number of displayed processors if necessary
            if ($('#processor-summary-table').is(':visible')) {
                $('#displayed-items').text(nf.Common.formatInteger(args.current));
            }
        });
        processorsData.onRowsChanged.subscribe(function (e, args) {
            processorsGrid.invalidateRows(args.rows);
            processorsGrid.render();
        });

        // hold onto an instance of the grid
        $('#processor-summary-table').data('gridInstance', processorsGrid).on('mouseenter', 'div.slick-cell', function (e) {
            var bulletinIcon = $(this).find('div.has-bulletins');
            if (bulletinIcon.length && !bulletinIcon.data('qtip')) {
                var processorId = $(this).find('span.row-id').text();

                // get the status item
                var item = processorsData.getItemById(processorId);

                // format the tooltip
                var bulletins = nf.Common.getFormattedBulletins(item.bulletins);
                var tooltip = nf.Common.formatUnorderedList(bulletins);

                // show the tooltip
                if (nf.Common.isDefinedAndNotNull(tooltip)) {
                    bulletinIcon.qtip($.extend({}, nf.Common.config.tooltipConfig, {
                        content: tooltip,
                        position: {
                            container: $('#summary'),
                            at: 'bottom right',
                            my: 'top left',
                            adjust: {
                                x: 4,
                                y: 4
                            }
                        }
                    }));
                }
            }
        });

        // initialize the cluster processor summary dialog
        $('#cluster-processor-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Processor Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster processor summary dialog
                        $('#cluster-processor-id').text('');
                        $('#cluster-processor-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster processor refresh
        $('#cluster-processor-refresh-button').click(function () {
            loadClusterProcessorSummary($('#cluster-processor-group-id').text(), $('#cluster-processor-id').text());
        });

        // initialize the cluster processor column model
        var clusterProcessorsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            runStatusColumn,
            inputColumn,
            ioColumn,
            outputColumn,
            tasksTimeColumn
        ];

        // initialize the options for the cluster processors table
        var clusterProcessorsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterProcessorsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterProcessorsData.setItems([]);

        // initialize the sort
        sort('cluster-processor-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterProcessorsData);

        // initialize the grid
        var clusterProcessorsGrid = new Slick.Grid('#cluster-processor-summary-table', clusterProcessorsData, clusterProcessorsColumnModel, clusterProcessorsOptions);
        clusterProcessorsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterProcessorsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterProcessorsGrid.setSortColumn('node', true);
        clusterProcessorsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-processor-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterProcessorsData);
        });

        // wire up the dataview to the grid
        clusterProcessorsData.onRowCountChanged.subscribe(function (e, args) {
            clusterProcessorsGrid.updateRowCount();
            clusterProcessorsGrid.render();
        });
        clusterProcessorsData.onRowsChanged.subscribe(function (e, args) {
            clusterProcessorsGrid.invalidateRows(args.rows);
            clusterProcessorsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-processor-summary-table').data('gridInstance', clusterProcessorsGrid);

        // define a custom formatter for showing more processor details
        var moreConnectionDetails = function (row, cell, value, columnDef, dataContext) {
            return '<div class="pointer show-connection-details fa fa-info-circle" title="View Connection Details" style="margin-top: 5px;"></div>';
        };

        // define the input, read, written, and output columns (reused between both tables)
        var queueColumn = {
            id: 'queued',
            field: 'queued',
            name: '<span class="queued-title">Queue</span>&nbsp;/&nbsp;<span class="queued-size-title">Size</span>',
            sortable: true,
            defaultSortAsc: false,
            resize: true
        };

        // define the column model for the summary table
        var connectionsColumnModel = [
            {
                id: 'moreDetails',
                name: '&nbsp;',
                sortable: false,
                resizable: false,
                formatter: moreConnectionDetails,
                width: 50,
                maxWidth: 50
            },
            {id: 'sourceName', field: 'sourceName', name: 'Source Name', sortable: true, resizable: true},
            {id: 'name', field: 'name', name: 'Name', sortable: true, resizable: true, formatter: valueFormatter},
            {
                id: 'destinationName',
                field: 'destinationName',
                name: 'Destination Name',
                sortable: true,
                resizable: true
            },
            inputColumn,
            queueColumn,
            outputColumn
        ];

        // add an action column if appropriate
        if (isClustered || isInShell || nf.Common.SUPPORTS_SVG) {
            // define how the column is formatted
            var connectionActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Connection" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (nf.Common.SUPPORTS_SVG) {
                    markup += '<div class="pointer show-connection-status-history fa fa-area-chart" title="View Status History" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-connection-summary fa fa-cubes" title="View Connection Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            connectionsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: connectionActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the templates table
        var connectionsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var connectionsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        connectionsData.setItems([]);
        connectionsData.setFilterArgs({
            searchString: '',
            property: 'sourceName'
        });
        connectionsData.setFilter(filter);

        // initialize the sort
        sort('connection-summary-table', {
            columnId: 'sourceName',
            sortAsc: true
        }, connectionsData);

        // initialize the grid
        var connectionsGrid = new Slick.Grid('#connection-summary-table', connectionsData, connectionsColumnModel, connectionsOptions);
        connectionsGrid.setSelectionModel(new Slick.RowSelectionModel());
        connectionsGrid.registerPlugin(new Slick.AutoTooltips());
        connectionsGrid.setSortColumn('sourceName', true);
        connectionsGrid.onSort.subscribe(function (e, args) {
            sort('connection-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, connectionsData);
        });

        // configure a click listener
        connectionsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = connectionsData.getItem(args.row);

            // determine the desired action
            if (connectionsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    goTo(item.groupId, item.id);
                } else if (target.hasClass('show-connection-status-history')) {
                    nf.StatusHistory.showConnectionChart(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-connection-summary')) {
                    // load the cluster processor summary
                    loadClusterConnectionSummary(item.groupId, item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-connection-summary-dialog').modal('show');

                    // resize after opening
                    clusterConnectionsGrid.resizeCanvas();
                }
            } else if (connectionsGrid.getColumns()[args.cell].id === 'moreDetails') {
                if (target.hasClass('show-connection-details')) {
                    nf.ConnectionDetails.showDetails(item.groupId, item.id);
                }
            }
        });

        // wire up the dataview to the grid
        connectionsData.onRowCountChanged.subscribe(function (e, args) {
            connectionsGrid.updateRowCount();
            connectionsGrid.render();

            // update the total number of displayed processors, if necessary
            if ($('#connection-summary-table').is(':visible')) {
                $('#displayed-items').text(nf.Common.formatInteger(args.current));
            }
        });
        connectionsData.onRowsChanged.subscribe(function (e, args) {
            connectionsGrid.invalidateRows(args.rows);
            connectionsGrid.render();
        });

        // hold onto an instance of the grid
        $('#connection-summary-table').data('gridInstance', connectionsGrid);

        // initialize the cluster connection summary dialog
        $('#cluster-connection-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Connection Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster connection summary dialog
                        $('#cluster-connection-id').text('');
                        $('#cluster-connection-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster connection refresh
        $('#cluster-connection-refresh-button').click(function () {
            loadClusterConnectionSummary($('#cluster-connection-group-id').text(), $('#cluster-connection-id').text());
        });

        // initialize the cluster processor column model
        var clusterConnectionsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            inputColumn,
            queueColumn,
            outputColumn
        ];

        // initialize the options for the cluster processors table
        var clusterConnectionsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterConnectionsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterConnectionsData.setItems([]);

        // initialize the sort
        sort('cluster-connection-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterConnectionsData);

        // initialize the grid
        var clusterConnectionsGrid = new Slick.Grid('#cluster-connection-summary-table', clusterConnectionsData, clusterConnectionsColumnModel, clusterConnectionsOptions);
        clusterConnectionsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterConnectionsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterConnectionsGrid.setSortColumn('node', true);
        clusterConnectionsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-connection-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterConnectionsData);
        });

        // wire up the dataview to the grid
        clusterConnectionsData.onRowCountChanged.subscribe(function (e, args) {
            clusterConnectionsGrid.updateRowCount();
            clusterConnectionsGrid.render();
        });
        clusterConnectionsData.onRowsChanged.subscribe(function (e, args) {
            clusterConnectionsGrid.invalidateRows(args.rows);
            clusterConnectionsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-connection-summary-table').data('gridInstance', clusterConnectionsGrid);

        // define a custom formatter for showing more port/group details
        var moreDetails = function (row, cell, value, columnDef, dataContext) {
            var markup = '';

            // if there are bulletins, render them on the graph
            if (!nf.Common.isEmpty(dataContext.bulletins)) {
                markup += '<div class="has-bulletins fa fa-sticky-note-o" style="margin-top: 5px; margin-left: 5px; float: left;"></div><span class="hidden row-id">' + nf.Common.escapeHtml(dataContext.id) + '</span>';
            }

            return markup;
        };

        var moreDetailsColumn = {
            id: 'moreDetails',
            field: 'moreDetails',
            name: '&nbsp;',
            resizable: false,
            formatter: moreDetails,
            sortable: true,
            width: 50,
            maxWidth: 50,
            toolTip: 'Sorts based on presence of bulletins'
        };
        var transferredColumn = {
            id: 'transferred',
            field: 'transferred',
            name: '<span class="transferred-title">Transferred</span>&nbsp;/&nbsp;<span class="transferred-size-title">Size</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / data size transferred to and from connections in the last 5 min',
            resizable: true,
            defaultSortAsc: false,
            sortable: true
        };
        var sentColumn = {
            id: 'sent',
            field: 'sent',
            name: '<span class="sent-title">Sent</span>&nbsp;/&nbsp;<span class="sent-size-title">Size</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / data size in the last 5 min',
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };
        var receivedColumn = {
            id: 'received',
            field: 'received',
            name: '<span class="received-title">Received</span>&nbsp;/&nbsp;<span class="received-size-title">Size</span>&nbsp;<span style="font-weight: normal; overflow: hidden;">5 min</span>',
            toolTip: 'Count / data size in the last 5 min',
            sortable: true,
            defaultSortAsc: false,
            resizable: true
        };

        // define the column model for the summary table
        var processGroupsColumnModel = [
            moreDetailsColumn,
            {id: 'name', field: 'name', name: 'Name', sortable: true, resizable: true, formatter: valueFormatter},
            transferredColumn,
            inputColumn,
            ioColumn,
            outputColumn,
            sentColumn,
            receivedColumn
        ];

        // add an action column if appropriate
        if (isClustered || isInShell || nf.Common.SUPPORTS_SVG) {
            // define how the column is formatted
            var processGroupActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell && dataContext.groupId !== null) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Process Group" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (nf.Common.SUPPORTS_SVG) {
                    markup += '<div class="pointer show-process-group-status-history fa fa-area-chart" title="View Status History" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-process-group-summary fa fa-cubes" title="View Process Group Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            processGroupsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: processGroupActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the templates table
        var processGroupsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var processGroupsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        processGroupsData.setItems([]);
        processGroupsData.setFilterArgs({
            searchString: '',
            property: 'name'
        });
        processGroupsData.setFilter(filter);

        // initialize the sort
        sort('process-group-summary-table', {
            columnId: 'name',
            sortAsc: true
        }, processGroupsData);

        // initialize the grid
        var processGroupsGrid = new Slick.Grid('#process-group-summary-table', processGroupsData, processGroupsColumnModel, processGroupsOptions);
        processGroupsGrid.setSelectionModel(new Slick.RowSelectionModel());
        processGroupsGrid.registerPlugin(new Slick.AutoTooltips());
        processGroupsGrid.setSortColumn('name', true);
        processGroupsGrid.onSort.subscribe(function (e, args) {
            sort('process-group-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, processGroupsData);
        });

        // configure a click listener
        processGroupsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = processGroupsData.getItem(args.row);

            // determine the desired action
            if (processGroupsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    if (nf.Common.isDefinedAndNotNull(parent.nf) && nf.Common.isDefinedAndNotNull(parent.nf.CanvasUtils) && nf.Common.isDefinedAndNotNull(parent.nf.Shell)) {
                        parent.nf.CanvasUtils.enterGroup(item.id);
                        parent.$('#shell-close-button').click();
                    }
                } else if (target.hasClass('show-process-group-status-history')) {
                    nf.StatusHistory.showProcessGroupChart(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-process-group-summary')) {
                    // load the cluster processor summary
                    loadClusterProcessGroupSummary(item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-process-group-summary-dialog').modal('show');

                    // resize after opening
                    clusterProcessGroupsGrid.resizeCanvas();
                }
            }
        });

        // wire up the dataview to the grid
        processGroupsData.onRowCountChanged.subscribe(function (e, args) {
            processGroupsGrid.updateRowCount();
            processGroupsGrid.render();

            // update the total number of displayed process groups if necessary
            if ($('#process-group-summary-table').is(':visible')) {
                $('#displayed-items').text(nf.Common.formatInteger(args.current));
            }
        });
        processGroupsData.onRowsChanged.subscribe(function (e, args) {
            processGroupsGrid.invalidateRows(args.rows);
            processGroupsGrid.render();
        });

        // hold onto an instance of the grid
        $('#process-group-summary-table').data('gridInstance', processGroupsGrid).on('mouseenter', 'div.slick-cell', function (e) {
            var bulletinIcon = $(this).find('div.has-bulletins');
            if (bulletinIcon.length && !bulletinIcon.data('qtip')) {
                var processGroupId = $(this).find('span.row-id').text();

                // get the status item
                var item = processGroupsData.getItemById(processGroupId);

                // format the tooltip
                var bulletins = nf.Common.getFormattedBulletins(item.bulletins);
                var tooltip = nf.Common.formatUnorderedList(bulletins);

                // show the tooltip
                if (nf.Common.isDefinedAndNotNull(tooltip)) {
                    bulletinIcon.qtip($.extend({}, nf.Common.config.tooltipConfig, {
                        content: tooltip,
                        position: {
                            container: $('#summary'),
                            at: 'bottom right',
                            my: 'top left',
                            adjust: {
                                x: 4,
                                y: 4
                            }
                        }
                    }));
                }
            }
        });

        // initialize the cluster process group summary dialog
        $('#cluster-process-group-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Process Group Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster processor summary dialog
                        $('#cluster-process-group-id').text('');
                        $('#cluster-process-group-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster process group refresh
        $('#cluster-process-group-refresh-button').click(function () {
            loadClusterProcessGroupSummary($('#cluster-process-group-id').text());
        });

        // initialize the cluster process groups column model
        var clusterProcessGroupsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            transferredColumn,
            inputColumn,
            ioColumn,
            outputColumn,
            sentColumn,
            receivedColumn
        ];

        // initialize the options for the cluster processors table
        var clusterProcessGroupsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterProcessGroupsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterProcessGroupsData.setItems([]);

        // initialize the sort
        sort('cluster-processor-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterProcessGroupsData);

        // initialize the grid
        var clusterProcessGroupsGrid = new Slick.Grid('#cluster-process-group-summary-table', clusterProcessGroupsData, clusterProcessGroupsColumnModel, clusterProcessGroupsOptions);
        clusterProcessGroupsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterProcessGroupsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterProcessGroupsGrid.setSortColumn('node', true);
        clusterProcessGroupsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-process-group-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterProcessGroupsData);
        });

        // wire up the dataview to the grid
        clusterProcessGroupsData.onRowCountChanged.subscribe(function (e, args) {
            clusterProcessGroupsGrid.updateRowCount();
            clusterProcessGroupsGrid.render();
        });
        clusterProcessGroupsData.onRowsChanged.subscribe(function (e, args) {
            clusterProcessGroupsGrid.invalidateRows(args.rows);
            clusterProcessGroupsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-process-group-summary-table').data('gridInstance', clusterProcessGroupsGrid);

        // define the column model for the summary table
        var inputPortsColumnModel = [
            moreDetailsColumn,
            nameColumn,
            runStatusColumn,
            outputColumn
        ];

        // add an action column if appropriate
        if (isClustered || isInShell) {
            // define how the column is formatted
            var inputPortActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Input Port" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-input-port-summary fa fa-cubes" title="View Input Port Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            inputPortsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: inputPortActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the input ports table
        var inputPortsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var inputPortsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        inputPortsData.setItems([]);
        inputPortsData.setFilterArgs({
            searchString: '',
            property: 'name'
        });
        inputPortsData.setFilter(filter);

        // initialize the sort
        sort('input-port-summary-table', {
            columnId: 'name',
            sortAsc: true
        }, inputPortsData);

        // initialize the grid
        var inputPortsGrid = new Slick.Grid('#input-port-summary-table', inputPortsData, inputPortsColumnModel, inputPortsOptions);
        inputPortsGrid.setSelectionModel(new Slick.RowSelectionModel());
        inputPortsGrid.registerPlugin(new Slick.AutoTooltips());
        inputPortsGrid.setSortColumn('name', true);
        inputPortsGrid.onSort.subscribe(function (e, args) {
            sort('input-port-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, inputPortsData);
        });

        // configure a click listener
        inputPortsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = inputPortsData.getItem(args.row);

            // determine the desired action
            if (inputPortsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    goTo(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-input-port-summary')) {
                    // load the cluster processor summary
                    loadClusterInputPortSummary(item.groupId, item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-input-port-summary-dialog').modal('show');

                    // resize after opening
                    clusterInputPortsGrid.resizeCanvas();
                }
            }
        });

        // wire up the dataview to the grid
        inputPortsData.onRowCountChanged.subscribe(function (e, args) {
            inputPortsGrid.updateRowCount();
            inputPortsGrid.render();

            // update the total number of displayed processors, if necessary
            if ($('#input-port-summary-table').is(':visible')) {
                $('#display-items').text(nf.Common.formatInteger(args.current));
            }
        });
        inputPortsData.onRowsChanged.subscribe(function (e, args) {
            inputPortsGrid.invalidateRows(args.rows);
            inputPortsGrid.render();
        });

        // hold onto an instance of the grid
        $('#input-port-summary-table').data('gridInstance', inputPortsGrid).on('mouseenter', 'div.slick-cell', function (e) {
            var bulletinIcon = $(this).find('div.has-bulletins');
            if (bulletinIcon.length && !bulletinIcon.data('qtip')) {
                var portId = $(this).find('span.row-id').text();

                // get the status item
                var item = inputPortsData.getItemById(portId);

                // format the tooltip
                var bulletins = nf.Common.getFormattedBulletins(item.bulletins);
                var tooltip = nf.Common.formatUnorderedList(bulletins);

                // show the tooltip
                if (nf.Common.isDefinedAndNotNull(tooltip)) {
                    bulletinIcon.qtip($.extend({}, nf.Common.config.tooltipConfig, {
                        content: tooltip,
                        position: {
                            container: $('#summary'),
                            at: 'bottom right',
                            my: 'top left',
                            adjust: {
                                x: 4,
                                y: 4
                            }
                        }
                    }));
                }
            }
        });

        // initialize the cluster input port summary dialog
        $('#cluster-input-port-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Input Port Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster processor summary dialog
                        $('#cluster-input-port-id').text('');
                        $('#cluster-input-port-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster input port refresh
        $('#cluster-input-port-refresh-button').click(function () {
            loadClusterInputPortSummary($('#cluster-input-port-group-id').text(), $('#cluster-input-port-id').text());
        });

        // initialize the cluster input port column model
        var clusterInputPortsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            runStatusColumn,
            outputColumn
        ];

        // initialize the options for the cluster input port table
        var clusterInputPortsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterInputPortsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterInputPortsData.setItems([]);

        // initialize the sort
        sort('cluster-input-port-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterInputPortsData);

        // initialize the grid
        var clusterInputPortsGrid = new Slick.Grid('#cluster-input-port-summary-table', clusterInputPortsData, clusterInputPortsColumnModel, clusterInputPortsOptions);
        clusterInputPortsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterInputPortsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterInputPortsGrid.setSortColumn('node', true);
        clusterInputPortsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-input-port-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterInputPortsData);
        });

        // wire up the dataview to the grid
        clusterInputPortsData.onRowCountChanged.subscribe(function (e, args) {
            clusterInputPortsGrid.updateRowCount();
            clusterInputPortsGrid.render();
        });
        clusterInputPortsData.onRowsChanged.subscribe(function (e, args) {
            clusterInputPortsGrid.invalidateRows(args.rows);
            clusterInputPortsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-input-port-summary-table').data('gridInstance', clusterInputPortsGrid);

        // define the column model for the summary table
        var outputPortsColumnModel = [
            moreDetailsColumn,
            nameColumn,
            runStatusColumn,
            inputColumn
        ];

        // add an action column if appropriate
        if (isClustered || isInShell) {
            // define how the column is formatted
            var outputPortActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Output Port" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-output-port-summary fa fa-cubes" title="View Output Port Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            outputPortsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: outputPortActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the input ports table
        var outputPortsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var outputPortsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        outputPortsData.setItems([]);
        outputPortsData.setFilterArgs({
            searchString: '',
            property: 'name'
        });
        outputPortsData.setFilter(filter);

        // initialize the sort
        sort('output-port-summary-table', {
            columnId: 'name',
            sortAsc: true
        }, outputPortsData);

        // initialize the grid
        var outputPortsGrid = new Slick.Grid('#output-port-summary-table', outputPortsData, outputPortsColumnModel, outputPortsOptions);
        outputPortsGrid.setSelectionModel(new Slick.RowSelectionModel());
        outputPortsGrid.registerPlugin(new Slick.AutoTooltips());
        outputPortsGrid.setSortColumn('name', true);
        outputPortsGrid.onSort.subscribe(function (e, args) {
            sort('output-port-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, outputPortsData);
        });

        // configure a click listener
        outputPortsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = outputPortsData.getItem(args.row);

            // determine the desired action
            if (outputPortsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    goTo(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-output-port-summary')) {
                    // load the cluster processor summary
                    loadClusterOutputPortSummary(item.groupId, item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-output-port-summary-dialog').modal('show');

                    // resize after opening
                    clusterOutputPortsGrid.resizeCanvas();
                }
            }
        });

        // wire up the dataview to the grid
        outputPortsData.onRowCountChanged.subscribe(function (e, args) {
            outputPortsGrid.updateRowCount();
            outputPortsGrid.render();

            // update the total number of displayed processors, if necessary
            if ($('#output-port-summary-table').is(':visible')) {
                $('#display-items').text(nf.Common.formatInteger(args.current));
            }
        });
        outputPortsData.onRowsChanged.subscribe(function (e, args) {
            outputPortsGrid.invalidateRows(args.rows);
            outputPortsGrid.render();
        });

        // hold onto an instance of the grid
        $('#output-port-summary-table').data('gridInstance', outputPortsGrid).on('mouseenter', 'div.slick-cell', function (e) {
            var bulletinIcon = $(this).find('div.has-bulletins');
            if (bulletinIcon.length && !bulletinIcon.data('qtip')) {
                var portId = $(this).find('span.row-id').text();

                // get the status item
                var item = outputPortsData.getItemById(portId);

                // format the tooltip
                var bulletins = nf.Common.getFormattedBulletins(item.bulletins);
                var tooltip = nf.Common.formatUnorderedList(bulletins);

                // show the tooltip
                if (nf.Common.isDefinedAndNotNull(tooltip)) {
                    bulletinIcon.qtip($.extend({}, nf.Common.config.tooltipConfig, {
                        content: tooltip,
                        position: {
                            container: $('#summary'),
                            at: 'bottom right',
                            my: 'top left',
                            adjust: {
                                x: 4,
                                y: 4
                            }
                        }
                    }));
                }
            }
        });

        // initialize the cluster output port summary dialog
        $('#cluster-output-port-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Output Port Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster processor summary dialog
                        $('#cluster-output-port-id').text('');
                        $('#cluster-output-port-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster output port refresh
        $('#cluster-output-port-refresh-button').click(function () {
            loadClusterOutputPortSummary($('#cluster-output-port-group-id').text(), $('#cluster-output-port-id').text());
        });

        // initialize the cluster output port column model
        var clusterOutputPortsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            runStatusColumn,
            inputColumn
        ];

        // initialize the options for the cluster output port table
        var clusterOutputPortsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterOutputPortsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterOutputPortsData.setItems([]);

        // initialize the sort
        sort('cluster-output-port-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterOutputPortsData);

        // initialize the grid
        var clusterOutputPortsGrid = new Slick.Grid('#cluster-output-port-summary-table', clusterOutputPortsData, clusterOutputPortsColumnModel, clusterOutputPortsOptions);
        clusterOutputPortsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterOutputPortsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterOutputPortsGrid.setSortColumn('node', true);
        clusterOutputPortsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-output-port-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterOutputPortsData);
        });

        // wire up the dataview to the grid
        clusterOutputPortsData.onRowCountChanged.subscribe(function (e, args) {
            clusterOutputPortsGrid.updateRowCount();
            clusterOutputPortsGrid.render();
        });
        clusterOutputPortsData.onRowsChanged.subscribe(function (e, args) {
            clusterOutputPortsGrid.invalidateRows(args.rows);
            clusterOutputPortsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-output-port-summary-table').data('gridInstance', clusterOutputPortsGrid);

        // define a custom formatter for the run status column
        var transmissionStatusFormatter = function (row, cell, value, columnDef, dataContext) {
            var activeThreadCount = '';
            if (nf.Common.isDefinedAndNotNull(dataContext.activeThreadCount) && dataContext.activeThreadCount > 0) {
                activeThreadCount = '(' + dataContext.activeThreadCount + ')';
            }

            // determine what to put in the mark up
            var transmissionClass = 'invalid fa fa-warning';
            var transmissionLabel = 'Invalid';
            if (value === 'Transmitting') {
                transmissionClass = 'transmitting fa fa-bullseye';
                transmissionLabel = value;
            } else {
                transmissionClass = 'not-transmitting icon icon-transmit-false';
                transmissionLabel = 'Not Transmitting';
            }

            // generate the mark up
            var formattedValue = '<div class="' + transmissionClass + '" style="margin-top: 5px;"></div>';
            return formattedValue + '<div class="status-text" style="margin-top: 4px;">' + transmissionLabel + '</div><div style="float: left; margin-left: 4px;">' + nf.Common.escapeHtml(activeThreadCount) + '</div>';
        };

        var transmissionStatusColumn = {
            id: 'transmissionStatus',
            field: 'transmissionStatus',
            name: 'Transmitting',
            formatter: transmissionStatusFormatter,
            sortable: true,
            resizable: true
        };
        var targetUriColumn = {
            id: 'targetUri',
            field: 'targetUri',
            name: 'Target URI',
            sortable: true,
            resizable: true
        };

        // define the column model for the summary table
        var remoteProcessGroupsColumnModel = [
            {
                id: 'moreDetails',
                field: 'moreDetails',
                name: '&nbsp;',
                resizable: false,
                formatter: moreDetails,
                sortable: true,
                width: 50,
                maxWidth: 50,
                toolTip: 'Sorts based on presence of bulletins'
            },
            nameColumn,
            targetUriColumn,
            transmissionStatusColumn,
            sentColumn,
            receivedColumn
        ];

        // add an action column if appropriate
        if (isClustered || isInShell || nf.Common.SUPPORTS_SVG) {
            // define how the column is formatted
            var remoteProcessGroupActionFormatter = function (row, cell, value, columnDef, dataContext) {
                var markup = '';

                if (isInShell) {
                    markup += '<div class="pointer go-to fa fa-long-arrow-right" title="Go To Process Group" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (nf.Common.SUPPORTS_SVG) {
                    markup += '<div class="pointer show-remote-process-group-status-history fa fa-area-chart" title="View Status History" style="margin-right: 3px;"></div>&nbsp;';
                }

                if (isClustered) {
                    markup += '<div class="pointer show-cluster-remote-process-group-summary fa fa-cubes" title="View Remote Process Group Details"></div>&nbsp;';
                }

                return markup;
            };

            // define the action column for clusters and within the shell
            remoteProcessGroupsColumnModel.push({
                id: 'actions',
                name: '&nbsp;',
                formatter: remoteProcessGroupActionFormatter,
                resizable: false,
                sortable: false,
                width: 75,
                maxWidth: 75
            });
        }

        // initialize the remote process groups table
        var remoteProcessGroupsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: true,
            enableColumnReorder: false,
            autoEdit: false,
            multiSelect: false,
            rowHeight: 24
        };

        // initialize the dataview
        var remoteProcessGroupsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        remoteProcessGroupsData.setItems([]);
        remoteProcessGroupsData.setFilterArgs({
            searchString: '',
            property: 'name'
        });
        remoteProcessGroupsData.setFilter(filter);

        // initialize the sort
        sort('remote-process-group-summary-table', {
            columnId: 'name',
            sortAsc: true
        }, remoteProcessGroupsData);

        // initialize the grid
        var remoteProcessGroupsGrid = new Slick.Grid('#remote-process-group-summary-table', remoteProcessGroupsData, remoteProcessGroupsColumnModel, remoteProcessGroupsOptions);
        remoteProcessGroupsGrid.setSelectionModel(new Slick.RowSelectionModel());
        remoteProcessGroupsGrid.registerPlugin(new Slick.AutoTooltips());
        remoteProcessGroupsGrid.setSortColumn('name', true);
        remoteProcessGroupsGrid.onSort.subscribe(function (e, args) {
            sort('remote-process-group-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, remoteProcessGroupsData);
        });

        // configure a click listener
        remoteProcessGroupsGrid.onClick.subscribe(function (e, args) {
            var target = $(e.target);

            // get the node at this row
            var item = remoteProcessGroupsData.getItem(args.row);

            // determine the desired action
            if (remoteProcessGroupsGrid.getColumns()[args.cell].id === 'actions') {
                if (target.hasClass('go-to')) {
                    goTo(item.groupId, item.id);
                } else if (target.hasClass('show-remote-process-group-status-history')) {
                    nf.StatusHistory.showRemoteProcessGroupChart(item.groupId, item.id);
                } else if (target.hasClass('show-cluster-remote-process-group-summary')) {
                    // load the cluster processor summary
                    loadClusterRemoteProcessGroupSummary(item.groupId, item.id);

                    // hide the summary loading indicator
                    $('#summary-loading-container').hide();

                    // show the dialog
                    $('#cluster-remote-process-group-summary-dialog').modal('show');

                    // resize after opening
                    clusterRemoteProcessGroupsGrid.resizeCanvas();
                }
            }
        });

        // wire up the dataview to the grid
        remoteProcessGroupsData.onRowCountChanged.subscribe(function (e, args) {
            remoteProcessGroupsGrid.updateRowCount();
            remoteProcessGroupsGrid.render();

            // update the total number of displayed processors, if necessary
            if ($('#remote-process-group-summary-table').is(':visible')) {
                $('#displayed-items').text(nf.Common.formatInteger(args.current));
            }
        });
        remoteProcessGroupsData.onRowsChanged.subscribe(function (e, args) {
            remoteProcessGroupsGrid.invalidateRows(args.rows);
            remoteProcessGroupsGrid.render();
        });

        // hold onto an instance of the grid
        $('#remote-process-group-summary-table').data('gridInstance', remoteProcessGroupsGrid).on('mouseenter', 'div.slick-cell', function (e) {
            var bulletinIcon = $(this).find('div.has-bulletins');
            if (bulletinIcon.length && !bulletinIcon.data('qtip')) {
                var remoteProcessGroupId = $(this).find('span.row-id').text();

                // get the status item
                var item = remoteProcessGroupsData.getItemById(remoteProcessGroupId);

                // format the tooltip
                var bulletins = nf.Common.getFormattedBulletins(item.bulletins);
                var tooltip = nf.Common.formatUnorderedList(bulletins);

                // show the tooltip
                if (nf.Common.isDefinedAndNotNull(tooltip)) {
                    bulletinIcon.qtip($.extend({}, nf.Common.config.tooltipConfig, {
                        content: tooltip,
                        position: {
                            container: $('#summary'),
                            at: 'bottom right',
                            my: 'top left',
                            adjust: {
                                x: 4,
                                y: 4
                            }
                        }
                    }));
                }
            }
        });

        // initialize the cluster remote process group summary dialog
        $('#cluster-remote-process-group-summary-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'Cluster Remote Process Group Summary',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        // clear the cluster processor summary dialog
                        $('#cluster-remote-process-group-id').text('');
                        $('#cluster-remote-process-group-name').text('');

                        // close the dialog
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                }
            }
        });

        // cluster remote process group refresh
        $('#cluster-remote-process-group-refresh-button').click(function () {
            loadClusterRemoteProcessGroupSummary($('#cluster-remote-process-group-group-id').text(), $('#cluster-remote-process-group-id').text());
        });

        // initialize the cluster remote process group column model
        var clusterRemoteProcessGroupsColumnModel = [
            {id: 'node', field: 'node', name: 'Node', sortable: true, resizable: true},
            targetUriColumn,
            transmissionStatusColumn,
            sentColumn,
            receivedColumn
        ];

        // initialize the options for the cluster remote process group table
        var clusterRemoteProcessGroupsOptions = {
            forceFitColumns: true,
            enableTextSelectionOnCells: true,
            enableCellNavigation: false,
            enableColumnReorder: false,
            autoEdit: false,
            rowHeight: 24
        };

        // initialize the dataview
        var clusterRemoteProcessGroupsData = new Slick.Data.DataView({
            inlineFilters: false
        });
        clusterRemoteProcessGroupsData.setItems([]);

        // initialize the sort
        sort('cluster-remote-process-group-summary-table', {
            columnId: 'node',
            sortAsc: true
        }, clusterRemoteProcessGroupsData);

        // initialize the grid
        var clusterRemoteProcessGroupsGrid = new Slick.Grid('#cluster-remote-process-group-summary-table', clusterRemoteProcessGroupsData, clusterRemoteProcessGroupsColumnModel, clusterRemoteProcessGroupsOptions);
        clusterRemoteProcessGroupsGrid.setSelectionModel(new Slick.RowSelectionModel());
        clusterRemoteProcessGroupsGrid.registerPlugin(new Slick.AutoTooltips());
        clusterRemoteProcessGroupsGrid.setSortColumn('node', true);
        clusterRemoteProcessGroupsGrid.onSort.subscribe(function (e, args) {
            sort('cluster-remote-process-group-summary-table', {
                columnId: args.sortCol.field,
                sortAsc: args.sortAsc
            }, clusterRemoteProcessGroupsData);
        });

        // wire up the dataview to the grid
        clusterRemoteProcessGroupsData.onRowCountChanged.subscribe(function (e, args) {
            clusterRemoteProcessGroupsGrid.updateRowCount();
            clusterRemoteProcessGroupsGrid.render();
        });
        clusterRemoteProcessGroupsData.onRowsChanged.subscribe(function (e, args) {
            clusterRemoteProcessGroupsGrid.invalidateRows(args.rows);
            clusterRemoteProcessGroupsGrid.render();
        });

        // hold onto an instance of the grid
        $('#cluster-remote-process-group-summary-table').data('gridInstance', clusterRemoteProcessGroupsGrid);

        // show the dialog when clicked
        $('#system-diagnostics-link').click(function () {
            refreshSystemDiagnostics().done(function () {
                // hide the summary loading container
                $('#summary-loading-container').hide();

                // show the dialog
                $('#system-diagnostics-dialog').modal('show');
            });
        });

        // initialize the summary tabs
        $('#system-diagnostics-tabs').tabbs({
            tabStyle: 'tab',
            selectedTabStyle: 'selected-tab',
            scrollableTabContentStyle: 'scrollable',
            tabs: [{
                name: 'JVM',
                tabContentId: 'jvm-tab-content'
            }, {
                name: 'System',
                tabContentId: 'system-tab-content'
            }]
        });

        // initialize the system diagnostics dialog
        $('#system-diagnostics-dialog').modal({
            scrollableContentStyle: 'scrollable',
            headerText: 'System Diagnostics',
            buttons: [{
                buttonText: 'Close',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: function () {
                        this.modal('hide');
                    }
                }
            }],
            handler: {
                close: function () {
                    // show the summary loading container
                    $('#summary-loading-container').show();
                },
                open: function () {
                    nf.Common.toggleScrollable($('#' + this.find('.tab-container').attr('id') + '-content').get(0));
                }
            }
        });

        // refresh the system diagnostics when clicked
        $('#system-diagnostics-refresh-button').click(function () {
            refreshSystemDiagnostics();
        });

        // initialize the total number of processors
        $('#total-items').text('0');
    };

    var sortState = {};

    /**
     * Sorts the specified data using the specified sort details.
     *
     * @param {string} tableId
     * @param {object} sortDetails
     * @param {object} data
     */
    var sort = function (tableId, sortDetails, data) {
        // ensure there is a state object for this table
        if (nf.Common.isUndefined(sortState[tableId])) {
            sortState[tableId] = {};
        }

        // defines a function for sorting
        var comparer = function (a, b) {
            if (sortDetails.columnId === 'moreDetails') {
                var aBulletins = 0;
                if (!nf.Common.isEmpty(a.bulletins)) {
                    aBulletins = a.bulletins.length;
                }
                var bBulletins = 0;
                if (!nf.Common.isEmpty(b.bulletins)) {
                    bBulletins = b.bulletins.length;
                }
                return aBulletins - bBulletins;
            } else if (sortDetails.columnId === 'runStatus' || sortDetails.columnId === 'transmissionStatus') {
                var aString = nf.Common.isDefinedAndNotNull(a[sortDetails.columnId]) ? a[sortDetails.columnId] : '';
                var bString = nf.Common.isDefinedAndNotNull(b[sortDetails.columnId]) ? b[sortDetails.columnId] : '';
                if (aString === bString) {
                    return a.activeThreadCount - b.activeThreadCount;
                } else {
                    return aString === bString ? 0 : aString > bString ? 1 : -1;
                }
            } else if (sortDetails.columnId === 'queued') {
                var mod = sortState[tableId].count % 4;
                if (mod < 2) {
                    $('#' + tableId + ' span.queued-title').addClass('sorted');
                    var aQueueCount = nf.Common.parseCount(a['queuedCount']);
                    var bQueueCount = nf.Common.parseCount(b['queuedCount']);
                    return aQueueCount - bQueueCount;
                } else {
                    $('#' + tableId + ' span.queued-size-title').addClass('sorted');
                    var aQueueSize = nf.Common.parseSize(a['queuedSize']);
                    var bQueueSize = nf.Common.parseSize(b['queuedSize']);
                    return aQueueSize - bQueueSize;
                }
            } else if (sortDetails.columnId === 'sent' || sortDetails.columnId === 'received' || sortDetails.columnId === 'input' || sortDetails.columnId === 'output' || sortDetails.columnId === 'transferred') {
                var aSplit = a[sortDetails.columnId].split(/ \/ /);
                var bSplit = b[sortDetails.columnId].split(/ \/ /);
                var mod = sortState[tableId].count % 4;
                if (mod < 2) {
                    $('#' + tableId + ' span.' + sortDetails.columnId + '-title').addClass('sorted');
                    var aCount = nf.Common.parseCount(aSplit[0]);
                    var bCount = nf.Common.parseCount(bSplit[0]);
                    return aCount - bCount;
                } else {
                    $('#' + tableId + ' span.' + sortDetails.columnId + '-size-title').addClass('sorted');
                    var aSize = nf.Common.parseSize(aSplit[1]);
                    var bSize = nf.Common.parseSize(bSplit[1]);
                    return aSize - bSize;
                }
            } else if (sortDetails.columnId === 'io') {
                var mod = sortState[tableId].count % 4;
                if (mod < 2) {
                    $('#' + tableId + ' span.read-title').addClass('sorted');
                    var aReadSize = nf.Common.parseSize(a['read']);
                    var bReadSize = nf.Common.parseSize(b['read']);
                    return aReadSize - bReadSize;
                } else {
                    $('#' + tableId + ' span.written-title').addClass('sorted');
                    var aWriteSize = nf.Common.parseSize(a['written']);
                    var bWriteSize = nf.Common.parseSize(b['written']);
                    return aWriteSize - bWriteSize;
                }
            } else if (sortDetails.columnId === 'tasks') {
                var mod = sortState[tableId].count % 4;
                if (mod < 2) {
                    $('#' + tableId + ' span.tasks-title').addClass('sorted');
                    var aTasks = nf.Common.parseCount(a['tasks']);
                    var bTasks = nf.Common.parseCount(b['tasks']);
                    return aTasks - bTasks;
                } else {
                    $('#' + tableId + ' span.time-title').addClass('sorted');
                    var aDuration = nf.Common.parseDuration(a['tasksDuration']);
                    var bDuration = nf.Common.parseDuration(b['tasksDuration']);
                    return aDuration - bDuration;
                }
            } else {
                var aString = nf.Common.isDefinedAndNotNull(a[sortDetails.columnId]) ? a[sortDetails.columnId] : '';
                var bString = nf.Common.isDefinedAndNotNull(b[sortDetails.columnId]) ? b[sortDetails.columnId] : '';
                return aString === bString ? 0 : aString > bString ? 1 : -1;
            }
        };

        // remove previous sort indicators
        $('#' + tableId + ' span.queued-title').removeClass('sorted');
        $('#' + tableId + ' span.queued-size-title').removeClass('sorted');
        $('#' + tableId + ' span.input-title').removeClass('sorted');
        $('#' + tableId + ' span.input-size-title').removeClass('sorted');
        $('#' + tableId + ' span.output-title').removeClass('sorted');
        $('#' + tableId + ' span.output-size-title').removeClass('sorted');
        $('#' + tableId + ' span.read-title').removeClass('sorted');
        $('#' + tableId + ' span.written-title').removeClass('sorted');
        $('#' + tableId + ' span.time-title').removeClass('sorted');
        $('#' + tableId + ' span.tasks-title').removeClass('sorted');
        $('#' + tableId + ' span.sent-title').removeClass('sorted');
        $('#' + tableId + ' span.sent-size-title').removeClass('sorted');
        $('#' + tableId + ' span.received-title').removeClass('sorted');
        $('#' + tableId + ' span.received-size-title').removeClass('sorted');
        $('#' + tableId + ' span.transferred-title').removeClass('sorted');
        $('#' + tableId + ' span.transferred-size-title').removeClass('sorted');

        // update/reset the count as appropriate
        if (sortState[tableId].prevColumn !== sortDetails.columnId) {
            sortState[tableId].count = 0;
        } else {
            sortState[tableId].count++;
        }

        // perform the sort
        data.sort(comparer, sortDetails.sortAsc);

        // record the previous table and sorted column
        sortState[tableId].prevColumn = sortDetails.columnId;
    };

    /**
     * Performs the processor filtering.
     *
     * @param {object} item     The item subject to filtering
     * @param {object} args     Filter arguments
     * @returns {Boolean}       Whether or not to include the item
     */
    var filter = function (item, args) {
        if (args.searchString === '') {
            return true;
        }

        try {
            // perform the row filtering
            var filterExp = new RegExp(args.searchString, 'i');
        } catch (e) {
            // invalid regex
            return false;
        }

        return item[args.property].search(filterExp) >= 0;
    };

    /**
     * Refreshes the system diagnostics.
     */
    var refreshSystemDiagnostics = function () {
        var systemDiagnosticsUri = config.urls.systemDiagnostics;

        // add the parameter if appropriate
        var parameters = {};
        if (!nf.Common.isNull(clusterNodeId)) {
            parameters['clusterNodeId'] = clusterNodeId;
        }

        // update the status uri if appropriate
        if (!$.isEmptyObject(parameters)) {
            systemDiagnosticsUri += '?' + $.param(parameters);
        }

        return $.ajax({
            type: 'GET',
            url: systemDiagnosticsUri,
            dataType: 'json'
        }).done(function (response) {
            var systemDiagnostics = response.systemDiagnostics;
            var aggregateSnapshot = systemDiagnostics.aggregateSnapshot;

            // heap
            $('#max-heap').text(aggregateSnapshot.maxHeap);
            $('#total-heap').text(aggregateSnapshot.totalHeap);
            $('#used-heap').text(aggregateSnapshot.usedHeap);
            $('#free-heap').text(aggregateSnapshot.freeHeap);

            // ensure the heap utilization could be calculated
            if (nf.Common.isDefinedAndNotNull(aggregateSnapshot.heapUtilization)) {
                $('#utilization-heap').text('(' + aggregateSnapshot.heapUtilization + ')');
            } else {
                $('#utilization-heap').text('');
            }

            // non heap
            $('#max-non-heap').text(aggregateSnapshot.maxNonHeap);
            $('#total-non-heap').text(aggregateSnapshot.totalNonHeap);
            $('#used-non-heap').text(aggregateSnapshot.usedNonHeap);
            $('#free-non-heap').text(aggregateSnapshot.freeNonHeap);

            // enure the non heap utilization could be calculated
            if (nf.Common.isDefinedAndNotNull(aggregateSnapshot.nonHeapUtilization)) {
                $('#utilization-non-heap').text('(' + aggregateSnapshot.nonHeapUtilization + ')');
            } else {
                $('#utilization-non-heap').text('');
            }

            // garbage collection
            var garbageCollectionContainer = $('#garbage-collection-table tbody').empty();
            $.each(aggregateSnapshot.garbageCollection, function (_, garbageCollection) {
                addGarbageCollection(garbageCollectionContainer, garbageCollection);
            });

            // available processors
            $('#available-processors').text(aggregateSnapshot.availableProcessors);

            // load
            if (nf.Common.isDefinedAndNotNull(aggregateSnapshot.processorLoadAverage)) {
                $('#processor-load-average').text(nf.Common.formatFloat(aggregateSnapshot.processorLoadAverage));
            } else {
                $('#processor-load-average').html(nf.Common.formatValue(aggregateSnapshot.processorLoadAverage));
            }

            // database storage usage
            var flowFileRepositoryStorageUsageContainer = $('#flow-file-repository-storage-usage-container').empty();
            addStorageUsage(flowFileRepositoryStorageUsageContainer, aggregateSnapshot.flowFileRepositoryStorageUsage);

            // database storage usage
            var contentRepositoryUsageContainer = $('#content-repository-storage-usage-container').empty();
            $.each(aggregateSnapshot.contentRepositoryStorageUsage, function (_, contentRepository) {
                addStorageUsage(contentRepositoryUsageContainer, contentRepository);
            });

            // update the stats last refreshed timestamp
            $('#system-diagnostics-last-refreshed').text(aggregateSnapshot.statsLastRefreshed);
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Adds a new garbage collection to the dialog
     * @param {type} container
     * @param {type} garbageCollection
     */
    var addGarbageCollection = function (container, garbageCollection) {
        var nameTr = $('<tr></tr>').appendTo(container);
        $('<td class="setting-name"></td>').append(garbageCollection.name + ':').appendTo(nameTr);
        var valTr = $('<tr></tr>').appendTo(container);
        $('<td></td>').append($('<b></b>').text(garbageCollection.collectionCount + ' times (' + garbageCollection.collectionTime + ')')).appendTo(valTr);
        $('<tr></tr>').text(' ').appendTo(container);
    };

    /**
     * Adds a new storage usage process bar to the dialog.
     *
     * @argument {jQuery} container     The container to add the storage usage to.
     * @argument {object} storageUsage     The storage usage.
     */
    var addStorageUsage = function (container, storageUsage) {
        var total = parseInt(storageUsage.totalSpaceBytes, 10);
        var used = parseInt(storageUsage.usedSpaceBytes, 10);
        var storageUsageContainer = $('<div class="storage-usage"></div>').appendTo(container);

        var storage = $('<div class="storage-identifier setting-name"></div>');
        storage.text('Usage:');
        if (nf.Common.isDefinedAndNotNull(storageUsage.identifier)) {
            storage.text('Usage for ' + storageUsage.identifier + ':');
        }
        storage.appendTo(storageUsageContainer);

        (nf.ng.Bridge.injector.get('$compile')($('<md-progress-linear class="md-hue-2" md-mode="determinate" value="' + (used/total)*100 + '" aria-label="FlowFile Repository Storage Usage"></md-progress-linear>'))(nf.ng.Bridge.rootScope)).appendTo(storageUsageContainer);

        var usageDetails = $('<div class="storage-usage-details"></div>').text(' (' + storageUsage.usedSpace + ' of ' + storageUsage.totalSpace + ')').prepend($('<b></b>').text(Math.round((used/total)*100) + '%'));
        $('<div class="storage-usage-header"></div>').append(usageDetails).append('<div class="clear"></div>').appendTo(storageUsageContainer);
    };

    /**
     * Get the text out of the filter field. If the filter field doesn't
     * have any text it will contain the text 'filter list' so this method
     * accounts for that.
     */
    var getFilterText = function () {
        return $('#summary-filter').val();
    };

    /**
     * Poplates the processor summary table using the specified process group.
     *
     * @argument {array} processorItems                 The processor data
     * @argument {array} connectionItems                The connection data
     * @argument {array} processGroupItems              The process group data
     * @argument {array} inputPortItems                 The input port data
     * @argument {array} outputPortItems                The input port data
     * @argument {array} remoteProcessGroupItems        The remote process group data
     * @argument {object} aggregateSnapshot            The process group status
     */
    var populateProcessGroupStatus = function (processorItems, connectionItems, processGroupItems, inputPortItems, outputPortItems, remoteProcessGroupItems, aggregateSnapshot) {
        // add the processors to the summary grid
        $.each(aggregateSnapshot.processorStatusSnapshots, function (i, procStatusEntity) {
            processorItems.push(procStatusEntity.processorStatusSnapshot);
        });

        // add the processors to the summary grid
        $.each(aggregateSnapshot.connectionStatusSnapshots, function (i, connStatusEntity) {
            connectionItems.push(connStatusEntity.connectionStatusSnapshot);
        });

        // add the input ports to the summary grid
        $.each(aggregateSnapshot.inputPortStatusSnapshots, function (i, portStatusEntity) {
            inputPortItems.push(portStatusEntity.portStatusSnapshot);
        });

        // add the input ports to the summary grid
        $.each(aggregateSnapshot.outputPortStatusSnapshots, function (i, portStatusEntity) {
            outputPortItems.push(portStatusEntity.portStatusSnapshot);
        });

        // add the input ports to the summary grid
        $.each(aggregateSnapshot.remoteProcessGroupStatusSnapshots, function (i, rpgStatusEntity) {
            remoteProcessGroupItems.push(rpgStatusEntity.remoteProcessGroupStatusSnapshot);
        });

        // add the process group status as well
        processGroupItems.push(aggregateSnapshot);

        // add any child group's status
        $.each(aggregateSnapshot.processGroupStatusSnapshots, function (i, childProcessGroupEntity) {
            populateProcessGroupStatus(processorItems, connectionItems, processGroupItems, inputPortItems, outputPortItems, remoteProcessGroupItems, childProcessGroupEntity.processGroupStatusSnapshot);
        });
    };

    /**
     * Applies the filter found in the filter expression text field.
     */
    var applyFilter = function () {
        var grid;
        if ($('#processor-summary-table').is(':visible')) {
            grid = $('#processor-summary-table').data('gridInstance');
        } else if ($('#connection-summary-table').is(':visible')) {
            grid = $('#connection-summary-table').data('gridInstance');
        } else if ($('#input-port-summary-table').is(':visible')) {
            grid = $('#input-port-summary-table').data('gridInstance');
        } else if ($('#output-port-summary-table').is(':visible')) {
            grid = $('#output-port-summary-table').data('gridInstance');
        } else if ($('#process-group-summary-table').is(':visible')) {
            grid = $('#process-group-summary-table').data('gridInstance');
        } else if ($('#remote-process-group-summary-table').is(':visible')) {
            grid = $('#remote-process-group-summary-table').data('gridInstance');
        }

        // ensure the grid has been initialized
        if (nf.Common.isDefinedAndNotNull(grid)) {
            var data = grid.getData();

            // update the search criteria
            data.setFilterArgs({
                searchString: getFilterText(),
                property: $('#summary-filter-type').combo('getSelectedOption').value
            });
            data.refresh();
        }
    };

    /**
     * Loads  the cluster processor details dialog for the specified processor.
     *
     * @argument {string} groupId         The group id
     * @argument {string} processorId     The processor id
     */
    var loadClusterProcessorSummary = function (groupId, processorId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/processors/' + encodeURIComponent(processorId) + '/status',
            data: {
                nodewise: true
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.processorStatus)) {
                var processorStatus = response.processorStatus;

                var clusterProcessorsGrid = $('#cluster-processor-summary-table').data('gridInstance');
                var clusterProcessorsData = clusterProcessorsGrid.getData();

                var clusterProcessors = [];

                // populate the table
                $.each(processorStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterProcessors.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        runStatus: snapshot.runStatus,
                        activeThreadCount: snapshot.activeThreadCount,
                        input: snapshot.input,
                        read: snapshot.read,
                        written: snapshot.written,
                        output: snapshot.output,
                        tasks: snapshot.tasks,
                        tasksDuration: snapshot.tasksDuration
                    });
                });

                // update the processors
                clusterProcessorsData.setItems(clusterProcessors);
                clusterProcessorsData.reSort();
                clusterProcessorsGrid.invalidate();

                // populate the processor details
                $('#cluster-processor-name').text(processorStatus.name).ellipsis();
                $('#cluster-processor-id').text(processorStatus.id);
                $('#cluster-processor-group-id').text(processorStatus.groupId);

                // update the stats last refreshed timestamp
                $('#cluster-processor-summary-last-refreshed').text(processorStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Loads the cluster connection details dialog for the specified processor.
     *
     * @argument {string} groupId   The group id
     * @argument {string} connectionId     The connection id
     */
    var loadClusterConnectionSummary = function (groupId, connectionId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/connections/' + encodeURIComponent(connectionId) + '/status',
            data: {
                nodewise: true
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.connectionStatus)) {
                var connectionStatus = response.connectionStatus;

                var clusterConnectionsGrid = $('#cluster-connection-summary-table').data('gridInstance');
                var clusterConnectionsData = clusterConnectionsGrid.getData();

                var clusterConnections = [];

                // populate the table
                $.each(connectionStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterConnections.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        input: snapshot.input,
                        queued: snapshot.queued,
                        queuedCount: snapshot.queuedCount,
                        queuedSize: snapshot.queuedSize,
                        output: snapshot.output
                    });
                });

                // update the processors
                clusterConnectionsData.setItems(clusterConnections);
                clusterConnectionsData.reSort();
                clusterConnectionsGrid.invalidate();

                // populate the processor details
                $('#cluster-connection-name').text(connectionStatus.name).ellipsis();
                $('#cluster-connection-id').text(connectionStatus.id);
                $('#cluster-connection-group-id').text(connectionStatus.groupId);

                // update the stats last refreshed timestamp
                $('#cluster-connection-summary-last-refreshed').text(connectionStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Loads the cluster input port details dialog for the specified processor.
     *
     * @argument {string} processGroupId     The process group id
     */
    var loadClusterProcessGroupSummary = function (processGroupId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/process-groups/' + encodeURIComponent(processGroupId) + '/status',
            data: {
                nodewise: true,
                recursive: false
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.processGroupStatus)) {
                var processGroupStatus = response.processGroupStatus;

                var clusterProcessGroupsGrid = $('#cluster-process-group-summary-table').data('gridInstance');
                var clusterProcessGroupsData = clusterProcessGroupsGrid.getData();

                var clusterProcessGroups = [];

                // populate the table
                $.each(processGroupStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterProcessGroups.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        activeThreadCount: snapshot.activeThreadCount,
                        transferred: snapshot.transferred,
                        input: snapshot.input,
                        queued: snapshot.queued,
                        queuedCount: snapshot.queuedCount,
                        queuedSize: snapshot.queuedSize,
                        output: snapshot.output,
                        read: snapshot.read,
                        written: snapshot.written,
                        sent: snapshot.sent,
                        received: snapshot.received
                    });
                });

                // update the input ports
                clusterProcessGroupsData.setItems(clusterProcessGroups);
                clusterProcessGroupsData.reSort();
                clusterProcessGroupsGrid.invalidate();

                // populate the input port details
                $('#cluster-process-group-name').text(processGroupStatus.name).ellipsis();
                $('#cluster-process-group-id').text(processGroupStatus.id);

                // update the stats last refreshed timestamp
                $('#cluster-process-group-summary-last-refreshed').text(processGroupStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Loads the cluster input port details dialog for the specified processor.
     *
     * @argument {string} groupId         The group id
     * @argument {string} inputPortId     The input port id
     */
    var loadClusterInputPortSummary = function (groupId, inputPortId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/input-ports/' + encodeURIComponent(inputPortId) + '/status',
            data: {
                nodewise: true
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.portStatus)) {
                var inputPortStatus = response.portStatus;

                var clusterInputPortsGrid = $('#cluster-input-port-summary-table').data('gridInstance');
                var clusterInputPortsData = clusterInputPortsGrid.getData();

                var clusterInputPorts = [];

                // populate the table
                $.each(inputPortStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterInputPorts.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        runStatus: snapshot.runStatus,
                        activeThreadCount: snapshot.activeThreadCount,
                        output: snapshot.output
                    });
                });

                // update the input ports
                clusterInputPortsData.setItems(clusterInputPorts);
                clusterInputPortsData.reSort();
                clusterInputPortsGrid.invalidate();

                // populate the input port details
                $('#cluster-input-port-name').text(inputPortStatus.name).ellipsis();
                $('#cluster-input-port-id').text(inputPortStatus.id);
                $('#cluster-input-port-group-id').text(inputPortStatus.groupId);

                // update the stats last refreshed timestamp
                $('#cluster-input-port-summary-last-refreshed').text(inputPortStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Loads the cluster output port details dialog for the specified processor.
     *
     * @argument {string} groupId          The group id
     * @argument {string} outputPortId     The row id
     */
    var loadClusterOutputPortSummary = function (groupId, outputPortId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/output-ports/' + encodeURIComponent(outputPortId) + '/status',
            data: {
                nodewise: true
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.portStatus)) {
                var outputPortStatus = response.portStatus;

                var clusterOutputPortsGrid = $('#cluster-output-port-summary-table').data('gridInstance');
                var clusterOutputPortsData = clusterOutputPortsGrid.getData();

                var clusterOutputPorts = [];

                // populate the table
                $.each(outputPortStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterOutputPorts.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        runStatus: snapshot.runStatus,
                        activeThreadCount: snapshot.activeThreadCount,
                        input: snapshot.input
                    });
                });

                // update the output ports
                clusterOutputPortsData.setItems(clusterOutputPorts);
                clusterOutputPortsData.reSort();
                clusterOutputPortsGrid.invalidate();

                // populate the output port details
                $('#cluster-output-port-name').text(outputPortStatus.name).ellipsis();
                $('#cluster-output-port-id').text(outputPortStatus.id);
                $('#cluster-output-port-group-id').text(outputPortStatus.groupId);

                // update the stats last refreshed timestamp
                $('#cluster-output-port-summary-last-refreshed').text(outputPortStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    /**
     * Loads the cluster remote process group details dialog for the specified processor.
     *
     * @argument {string} groupId   The group id
     * @argument {string} remoteProcessGroupId     The remote process group id
     */
    var loadClusterRemoteProcessGroupSummary = function (groupId, remoteProcessGroupId) {
        // get the summary
        $.ajax({
            type: 'GET',
            url: config.urls.api + '/flow/remote-process-groups/' + encodeURIComponent(remoteProcessGroupId) + '/status',
            data: {
                nodewise: true
            },
            dataType: 'json'
        }).done(function (response) {
            if (nf.Common.isDefinedAndNotNull(response.remoteProcessGroupStatus)) {
                var remoteProcessGroupStatus = response.remoteProcessGroupStatus;

                var clusterRemoteProcessGroupsGrid = $('#cluster-remote-process-group-summary-table').data('gridInstance');
                var clusterRemoteProcessGroupsData = clusterRemoteProcessGroupsGrid.getData();

                var clusterRemoteProcessGroups = [];

                // populate the table
                $.each(remoteProcessGroupStatus.nodeSnapshots, function (i, nodeSnapshot) {
                    var snapshot = nodeSnapshot.statusSnapshot;
                    clusterRemoteProcessGroups.push({
                        id: nodeSnapshot.nodeId,
                        node: nodeSnapshot.address + ':' + nodeSnapshot.apiPort,
                        targetUri: snapshot.targetUri,
                        transmissionStatus: snapshot.transmissionStatus,
                        sent: snapshot.sent,
                        received: snapshot.received,
                        activeThreadCount: snapshot.activeThreadCount
                    });
                });

                // update the remote process groups
                clusterRemoteProcessGroupsData.setItems(clusterRemoteProcessGroups);
                clusterRemoteProcessGroupsData.reSort();
                clusterRemoteProcessGroupsGrid.invalidate();

                // populate the remote process group details
                $('#cluster-remote-process-group-name').text(remoteProcessGroupStatus.name).ellipsis();
                $('#cluster-remote-process-group-id').text(remoteProcessGroupStatus.id);
                $('#cluster-remote-process-group-group-id').text(remoteProcessGroupStatus.groupId);

                // update the stats last refreshed timestamp
                $('#cluster-remote-process-group-summary-last-refreshed').text(remoteProcessGroupStatus.statsLastRefreshed);
            }
        }).fail(nf.Common.handleAjaxError);
    };

    var clusterNodeId = null;

    return {

        /**
         * Initializes the status table.
         *
         * @argument {boolean} isClustered Whether or not this NiFi is clustered.
         */
        init: function (isClustered) {
            return $.Deferred(function (deferred) {
                // get the controller config to get the server offset
                var configRequest = $.ajax({
                    type: 'GET',
                    url: config.urls.flowConfig,
                    dataType: 'json'
                }).done(function (configResponse) {
                    var configDetails = configResponse.flowConfiguration;

                    // initialize the chart
                    nf.StatusHistory.init(configDetails.timeOffset);

                    // initialize the processor/connection details dialog
                    nf.ProcessorDetails.init(false);
                    nf.ConnectionDetails.init();
                    initSummaryTable(isClustered);

                    deferred.resolve();
                }).fail(function () {
                    deferred.reject();
                });
            }).promise();
        },

        /**
         * Sets the cluster node id which will only show status from this node on subsequent reloads.
         *
         * @param nodeId
         */
        setClusterNodeId: function (nodeId) {
            clusterNodeId = nodeId;
        },

        /**
         * Update the size of the grid based on its container's current size.
         */
        resetTableSize: function () {
            var processorsTable = $('#processor-summary-table');
            if (processorsTable.is(':visible')) {
                var processorsGrid = processorsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(processorsGrid)) {
                    processorsGrid.resizeCanvas();
                }
            }

            var connectionsTable = $('#connection-summary-table');
            if (connectionsTable.is(':visible')) {
                var connectionsGrid = connectionsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(connectionsGrid)) {
                    connectionsGrid.resizeCanvas();
                }
            }

            var processGroupsTable = $('#process-group-summary-table');
            if (processGroupsTable.is(':visible')) {
                var processGroupsGrid = processGroupsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(processGroupsGrid)) {
                    processGroupsGrid.resizeCanvas();
                }
            }

            var inputPortsTable = $('#input-port-summary-table');
            if (inputPortsTable.is(':visible')) {
                var inputPortGrid = inputPortsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(inputPortGrid)) {
                    inputPortGrid.resizeCanvas();
                }
            }

            var outputPortsTable = $('#output-port-summary-table');
            if (outputPortsTable.is(':visible')) {
                var outputPortGrid = outputPortsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(outputPortGrid)) {
                    outputPortGrid.resizeCanvas();
                }
            }

            var remoteProcessGroupsTable = $('#remote-process-group-summary-table');
            if (remoteProcessGroupsTable.is(':visible')) {
                var remoteProcessGroupGrid = remoteProcessGroupsTable.data('gridInstance');
                if (nf.Common.isDefinedAndNotNull(remoteProcessGroupGrid)) {
                    remoteProcessGroupGrid.resizeCanvas();
                }
            }
        },

        /**
         * Load the summary table.
         */
        loadSummaryTable: function () {
            var statusUri = config.urls.status;

            // add the parameter if appropriate
            var parameters = {};
            if (!nf.Common.isNull(clusterNodeId)) {
                parameters['clusterNodeId'] = clusterNodeId;
            }

            // update the status uri if appropriate
            if (!$.isEmptyObject(parameters)) {
                statusUri += '?' + $.param(parameters);
            }

            return $.ajax({
                type: 'GET',
                url: statusUri,
                data: {
                    recursive: true
                },
                dataType: 'json'
            }).done(function (response) {
                var processGroupStatus = response.processGroupStatus;
                var aggregateSnapshot = processGroupStatus.aggregateSnapshot;

                if (nf.Common.isDefinedAndNotNull(aggregateSnapshot)) {
                    // remove any tooltips from the processor table
                    var processorsGridElement = $('#processor-summary-table');
                    nf.Common.cleanUpTooltips(processorsGridElement, 'div.has-bulletins');

                    // get the processor grid/data
                    var processorsGrid = processorsGridElement.data('gridInstance');
                    var processorsData = processorsGrid.getData();

                    // get the connections grid/data (do not render bulletins)
                    var connectionsGrid = $('#connection-summary-table').data('gridInstance');
                    var connectionsData = connectionsGrid.getData();

                    // remove any tooltips from the process group table
                    var processGroupGridElement = $('#process-group-summary-table');
                    nf.Common.cleanUpTooltips(processGroupGridElement, 'div.has-bulletins');

                    // get the process group grid/data
                    var processGroupGrid = processGroupGridElement.data('gridInstance');
                    var processGroupData = processGroupGrid.getData();

                    // remove any tooltips from the input port table
                    var inputPortsGridElement = $('#input-port-summary-table');
                    nf.Common.cleanUpTooltips(inputPortsGridElement, 'div.has-bulletins');

                    // get the input ports grid/data
                    var inputPortsGrid = inputPortsGridElement.data('gridInstance');
                    var inputPortsData = inputPortsGrid.getData();

                    // remove any tooltips from the output port table
                    var outputPortsGridElement = $('#output-port-summary-table');
                    nf.Common.cleanUpTooltips(outputPortsGridElement, 'div.has-bulletins');

                    // get the output ports grid/data
                    var outputPortsGrid = outputPortsGridElement.data('gridInstance');
                    var outputPortsData = outputPortsGrid.getData();

                    // remove any tooltips from the remote process group table
                    var remoteProcessGroupsGridElement = $('#remote-process-group-summary-table');
                    nf.Common.cleanUpTooltips(remoteProcessGroupsGridElement, 'div.has-bulletins');

                    // get the remote process groups grid
                    var remoteProcessGroupsGrid = remoteProcessGroupsGridElement.data('gridInstance');
                    var remoteProcessGroupsData = remoteProcessGroupsGrid.getData();

                    var processorItems = [];
                    var connectionItems = [];
                    var processGroupItems = [];
                    var inputPortItems = [];
                    var outputPortItems = [];
                    var remoteProcessGroupItems = [];

                    // populate the tables
                    populateProcessGroupStatus(processorItems, connectionItems, processGroupItems, inputPortItems, outputPortItems, remoteProcessGroupItems, aggregateSnapshot);

                    // update the processors
                    processorsData.setItems(processorItems);
                    processorsData.reSort();
                    processorsGrid.invalidate();

                    // update the connections
                    connectionsData.setItems(connectionItems);
                    connectionsData.reSort();
                    connectionsGrid.invalidate();

                    // update the process groups
                    processGroupData.setItems(processGroupItems);
                    processGroupData.reSort();
                    processGroupGrid.invalidate();

                    // update the input ports
                    inputPortsData.setItems(inputPortItems);
                    inputPortsData.reSort();
                    inputPortsGrid.invalidate();

                    // update the output ports
                    outputPortsData.setItems(outputPortItems);
                    outputPortsData.reSort();
                    outputPortsGrid.invalidate();

                    // update the remote process groups
                    remoteProcessGroupsData.setItems(remoteProcessGroupItems);
                    remoteProcessGroupsData.reSort();
                    remoteProcessGroupsGrid.invalidate();

                    // update the stats last refreshed timestamp
                    $('#summary-last-refreshed').text(processGroupStatus.statsLastRefreshed);

                    // update the total number of processors
                    if ($('#processor-summary-table').is(':visible')) {
                        $('#total-items').text(nf.Common.formatInteger(processorItems.length));
                    } else if ($('#connection-summary-table').is(':visible')) {
                        $('#total-items').text(nf.Common.formatInteger(connectionItems.length));
                    } else if ($('#input-port-summary-table').is(':visible')) {
                        $('#total-items').text(nf.Common.formatInteger(inputPortItems.length));
                    } else if ($('#output-port-summary-table').is(':visible')) {
                        $('#total-items').text(nf.Common.formatInteger(outputPortItems.length));
                    } else {
                        $('#total-items').text(nf.Common.formatInteger(remoteProcessGroupItems.length));
                    }
                } else {
                    $('#total-items').text('0');
                }
            }).fail(nf.Common.handleAjaxError);
        }
    };
}());