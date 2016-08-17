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

/* global nf, d3 */

nf.ng.ProcessorComponent = function (serviceProvider) {
    'use strict';

    /**
     * Filters the processor type table.
     */
    var applyFilter = function () {
        // get the dataview
        var processorTypesGrid = $('#processor-types-table').data('gridInstance');

        // ensure the grid has been initialized
        if (nf.Common.isDefinedAndNotNull(processorTypesGrid)) {
            var processorTypesData = processorTypesGrid.getData();

            // update the search criteria
            processorTypesData.setFilterArgs({
                searchString: getFilterText()
            });
            processorTypesData.refresh();

            // update the selection if possible
            if (processorTypesData.getLength() > 0) {
                processorTypesGrid.setSelectedRows([0]);
            }
        }
    };

    /**
     * Determines if the item matches the filter.
     *
     * @param {object} item     The item to filter.
     * @param {object} args     The filter criteria.
     * @returns {boolean}       Whether the item matches the filter.
     */
    var matchesRegex = function (item, args) {
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

        // determine if the item matches the filter
        var matchesLabel = item['label'].search(filterExp) >= 0;
        var matchesTags = item['tags'].search(filterExp) >= 0;
        return matchesLabel || matchesTags;
    };

    /**
     * Performs the filtering.
     *
     * @param {object} item     The item subject to filtering.
     * @param {object} args     Filter arguments.
     * @returns {Boolean}       Whether or not to include the item.
     */
    var filter = function (item, args) {
        // determine if the item matches the filter
        var matchesFilter = matchesRegex(item, args);

        // determine if the row matches the selected tags
        var matchesTags = true;
        if (matchesFilter) {
            var tagFilters = $('#processor-tag-cloud').tagcloud('getSelectedTags');
            var hasSelectedTags = tagFilters.length > 0;
            if (hasSelectedTags) {
                matchesTags = matchesSelectedTags(tagFilters, item['tags']);
            }
        }

        // determine if this row should be visible
        var matches = matchesFilter && matchesTags;

        // if this row is currently selected and its being filtered
        if (matches === false && $('#selected-processor-type').text() === item['type']) {
            // clear the selected row
            $('#processor-type-description').text('');
            $('#processor-type-name').text('');
            $('#selected-processor-name').text('');
            $('#selected-processor-type').text('');

            // clear the active cell the it can be reselected when its included
            var processTypesGrid = $('#processor-types-table').data('gridInstance');
            processTypesGrid.resetActiveCell();
        }

        return matches;
    };

    /**
     * Determines if the specified tags match all the tags selected by the user.
     *
     * @argument {string[]} tagFilters      The tag filters.
     * @argument {string} tags              The tags to test.
     */
    var matchesSelectedTags = function (tagFilters, tags) {
        var selectedTags = [];
        $.each(tagFilters, function (_, filter) {
            selectedTags.push(filter);
        });

        // normalize the tags
        var normalizedTags = tags.toLowerCase();

        var matches = true;
        $.each(selectedTags, function (i, selectedTag) {
            if (normalizedTags.indexOf(selectedTag) === -1) {
                matches = false;
                return false;
            }
        });

        return matches;
    };

    /**
     * Sorts the specified data using the specified sort details.
     *
     * @param {object} sortDetails
     * @param {object} data
     */
    var sort = function (sortDetails, data) {
        // defines a function for sorting
        var comparer = function (a, b) {
            var aString = nf.Common.isDefinedAndNotNull(a[sortDetails.columnId]) ? a[sortDetails.columnId] : '';
            var bString = nf.Common.isDefinedAndNotNull(b[sortDetails.columnId]) ? b[sortDetails.columnId] : '';
            return aString === bString ? 0 : aString > bString ? 1 : -1;
        };

        // perform the sort
        data.sort(comparer, sortDetails.sortAsc);
    };

    /**
     * Get the text out of the filter field. If the filter field doesn't
     * have any text it will contain the text 'filter list' so this method
     * accounts for that.
     */
    var getFilterText = function () {
        return $('#processor-type-filter').val();
    };

    /**
     * Resets the filtered processor types.
     */
    var resetProcessorDialog = function () {
        // clear the selected tag cloud
        $('#processor-tag-cloud').tagcloud('clearSelectedTags');

        // clear any filter strings
        $('#processor-type-filter').val('');

        // reapply the filter
        applyFilter();

        // clear the selected row
        $('#processor-type-description').text('');
        $('#processor-type-name').text('');
        $('#selected-processor-name').text('');
        $('#selected-processor-type').text('');

        // unselect any current selection
        var processTypesGrid = $('#processor-types-table').data('gridInstance');
        processTypesGrid.setSelectedRows([]);
        processTypesGrid.resetActiveCell();
    };

    /**
     * Create the processor and add to the graph.
     *
     * @argument {string} name              The processor name.
     * @argument {string} processorType     The processor type.
     * @argument {object} pt                The point that the processor was dropped.
     */
    var createProcessor = function (name, processorType, pt) {
        var processorEntity = {
            'revision': nf.Client.getRevision({
                'revision': {
                    'version': 0
                }
            }),
            'component': {
                'type': processorType,
                'name': name,
                'position': {
                    'x': pt.x,
                    'y': pt.y
                }
            }
        };

        // create a new processor of the defined type
        $.ajax({
            type: 'POST',
            url: serviceProvider.headerCtrl.toolboxCtrl.config.urls.api + '/process-groups/' + encodeURIComponent(nf.Canvas.getGroupId()) + '/processors',
            data: JSON.stringify(processorEntity),
            dataType: 'json',
            contentType: 'application/json'
        }).done(function (response) {
            // add the processor to the graph
            nf.Graph.add({
                'processors': [response]
            }, {
                'selectAll': true
            });

            // update component visibility
            nf.Canvas.View.updateVisibility();

            // update the birdseye
            nf.Birdseye.refresh();
        }).fail(nf.Common.handleAjaxError);
    };

    function ProcessorComponent() {

        this.icon = 'icon icon-processor';

        this.hoverIcon = 'icon icon-processor-add';

        /**
         * The processor component's modal.
         */
        this.modal = {

            /**
             * The processor component modal's filter.
             */
            filter: {

                /**
                 * Initialize the filter.
                 */
                init: function () {
                    // initialize the processor type table
                    var processorTypesColumns = [
                        {id: 'type', name: 'Type', field: 'label', sortable: true, resizable: true},
                        {id: 'tags', name: 'Tags', field: 'tags', sortable: true, resizable: true}
                    ];
                    var processorTypesOptions = {
                        forceFitColumns: true,
                        enableTextSelectionOnCells: true,
                        enableCellNavigation: true,
                        enableColumnReorder: false,
                        autoEdit: false,
                        multiSelect: false,
                        rowHeight: 24
                    };

                    // initialize the dataview
                    var processorTypesData = new Slick.Data.DataView({
                        inlineFilters: false
                    });
                    processorTypesData.setItems([]);
                    processorTypesData.setFilterArgs({
                        searchString: getFilterText()
                    });
                    processorTypesData.setFilter(filter);

                    // initialize the sort
                    sort({
                        columnId: 'type',
                        sortAsc: true
                    }, processorTypesData);

                    // initialize the grid
                    var processorTypesGrid = new Slick.Grid('#processor-types-table', processorTypesData, processorTypesColumns, processorTypesOptions);
                    processorTypesGrid.setSelectionModel(new Slick.RowSelectionModel());
                    processorTypesGrid.registerPlugin(new Slick.AutoTooltips());
                    processorTypesGrid.setSortColumn('type', true);
                    processorTypesGrid.onSort.subscribe(function (e, args) {
                        sort({
                            columnId: args.sortCol.field,
                            sortAsc: args.sortAsc
                        }, processorTypesData);
                    });
                    processorTypesGrid.onSelectedRowsChanged.subscribe(function (e, args) {
                        if ($.isArray(args.rows) && args.rows.length === 1) {
                            var processorTypeIndex = args.rows[0];
                            var processorType = processorTypesGrid.getDataItem(processorTypeIndex);

                            // set the processor type description
                            if (nf.Common.isDefinedAndNotNull(processorType)) {
                                if (nf.Common.isBlank(processorType.description)) {
                                    $('#processor-type-description').attr('title', '').html('<span class="unset">No description specified</span>');
                                } else {
                                    $('#processor-type-description').html(processorType.description).ellipsis();
                                }

                                // populate the dom
                                $('#processor-type-name').text(processorType.label).ellipsis();
                                $('#selected-processor-name').text(processorType.label);
                                $('#selected-processor-type').text(processorType.type);
                            }
                        }
                    });

                    // wire up the dataview to the grid
                    processorTypesData.onRowCountChanged.subscribe(function (e, args) {
                        processorTypesGrid.updateRowCount();
                        processorTypesGrid.render();

                        // update the total number of displayed processors
                        $('#displayed-processor-types').text(args.current);
                    });
                    processorTypesData.onRowsChanged.subscribe(function (e, args) {
                        processorTypesGrid.invalidateRows(args.rows);
                        processorTypesGrid.render();
                    });
                    processorTypesData.syncGridSelection(processorTypesGrid, false);

                    // hold onto an instance of the grid
                    $('#processor-types-table').data('gridInstance', processorTypesGrid);

                    // load the available processor types, this select is shown in the
                    // new processor dialog when a processor is dragged onto the screen
                    $.ajax({
                        type: 'GET',
                        url: serviceProvider.headerCtrl.toolboxCtrl.config.urls.processorTypes,
                        dataType: 'json'
                    }).done(function (response) {
                        var tags = [];

                        // begin the update
                        processorTypesData.beginUpdate();

                        // go through each processor type
                        $.each(response.processorTypes, function (i, documentedType) {
                            var type = documentedType.type;

                            // create the row for the processor type
                            processorTypesData.addItem({
                                id: i,
                                label: nf.Common.substringAfterLast(type, '.'),
                                type: type,
                                description: nf.Common.escapeHtml(documentedType.description),
                                tags: documentedType.tags.join(', ')
                            });

                            // count the frequency of each tag for this type
                            $.each(documentedType.tags, function (i, tag) {
                                tags.push(tag.toLowerCase());
                            });
                        });

                        // end the udpate
                        processorTypesData.endUpdate();

                        // set the total number of processors
                        $('#total-processor-types, #displayed-processor-types').text(response.processorTypes.length);

                        // create the tag cloud
                        $('#processor-tag-cloud').tagcloud({
                            tags: tags,
                            select: applyFilter,
                            remove: applyFilter
                        });
                    }).fail(nf.Common.handleAjaxError);
                }
            },

            /**
             * Gets the modal element.
             *
             * @returns {*|jQuery|HTMLElement}
             */
            getElement: function () {
                return $('#new-processor-dialog');
            },

            /**
             * Initialize the modal.
             */
            init: function () {
                var self = this;
                
                this.filter.init();

                // configure the new processor dialog
                this.getElement().modal({
                    scrollableContentStyle: 'scrollable',
                    headerText: 'Add Processor'
                });
            },

            /**
             * Updates the modal config.
             *
             * @param {string} name             The name of the property to update.
             * @param {object|array} config     The config for the `name`.
             */
            update: function (name, config) {
                this.getElement().modal(name, config);
            },

            /**
             * Show the modal
             */
            show: function () {
                this.getElement().modal('show');
            },

            /**
             * Hide the modal
             */
            hide: function () {
                this.getElement().modal('hide');
            }
        };
    }

    ProcessorComponent.prototype = {
        constructor: ProcessorComponent,

        /**
         * Gets the component.
         *
         * @returns {*|jQuery|HTMLElement}
         */
        getElement: function () {
            return $('#processor-component');
        },

        /**
         * Enable the component.
         */
        enabled: function () {
            this.getElement().attr('disabled', false);
        },

        /**
         * Disable the component.
         */
        disabled: function () {
            this.getElement().attr('disabled', true);
        },

        /**
         * Handler function for when component is dropped on the canvas.
         *
         * @argument {object} pt        The point that the component was dropped
         */
        dropHandler: function (pt) {
            this.promptForProcessorType(pt);
        },

        /**
         * The drag icon for the toolbox component.
         * 
         * @param event
         * @returns {*|jQuery|HTMLElement}
         */
        dragIcon: function (event) {
            return $('<div class="icon icon-processor-add"></div>');
        },

        /**
         * Prompts the user to select the type of new processor to create.
         *
         * @argument {object} pt        The point that the processor was dropped
         */
        promptForProcessorType: function (pt) {
            var self = this;
            // handles adding the selected processor at the specified point
            var addProcessor = function () {
                // get the type of processor currently selected
                var name = $('#selected-processor-name').text();
                var processorType = $('#selected-processor-type').text();

                // ensure something was selected
                if (name === '' || processorType === '') {
                    nf.Dialog.showOkDialog({
                        headerText: 'Add Processor',
                        dialogContent: 'The type of processor to create must be selected.'
                    });
                } else {
                    // create the new processor
                    createProcessor(name, processorType, pt);
                }

                // hide the dialog
                self.modal.hide();
            };

            // get the grid reference
            var grid = $('#processor-types-table').data('gridInstance');

            // add the processor when its double clicked in the table
            var gridDoubleClick = function (e, args) {
                var processorType = grid.getDataItem(args.row);

                $('#selected-processor-name').text(processorType.label);
                $('#selected-processor-type').text(processorType.type);

                addProcessor();
            };

            // register a handler for double click events
            grid.onDblClick.subscribe(gridDoubleClick);

            // update the button model
            this.modal.update('setButtonModel', [{
                buttonText: 'Add',
                color: {
                    base: '#728E9B',
                    hover: '#004849',
                    text: '#ffffff'
                },
                handler: {
                    click: addProcessor
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
                            $('#new-processor-dialog').modal('hide');
                        }
                    }
                }]);

            // set a new handler for closing the the dialog
            this.modal.update('setCloseHandler', function () {
                // remove the handler
                grid.onDblClick.unsubscribe(gridDoubleClick);

                // clear the current filters
                resetProcessorDialog();
            });

            // show the dialog
            this.modal.show();

            // setup the filter
            $('#processor-type-filter').focus().off('keyup').on('keyup', function (e) {
                var code = e.keyCode ? e.keyCode : e.which;
                if (code === $.ui.keyCode.ENTER) {
                    addProcessor();
                } else {
                    applyFilter();
                }
            });

            // adjust the grid canvas now that its been rendered
            grid.resizeCanvas();
            grid.setSelectedRows([0]);
        }
    }

    var processorComponent = new ProcessorComponent();
    return processorComponent;
};