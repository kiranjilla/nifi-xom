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

nf.Funnel = (function () {

    var dimensions = {
        width: 48,
        height: 48
    };

    // -----------------------------
    // funnels currently on the graph
    // -----------------------------

    var funnelMap;

    // --------------------
    // component containers
    // --------------------

    var funnelContainer;

    // --------------------------
    // privately scoped functions
    // --------------------------

    /**
     * Selects the funnel elements against the current funnel map.
     */
    var select = function () {
        return funnelContainer.selectAll('g.funnel').data(funnelMap.values(), function (d) {
            return d.id;
        });
    };

    /**
     * Renders the funnels in the specified selection.
     *
     * @param {selection} entered           The selection of funnels to be rendered
     * @param {boolean} selected             Whether the element should be selected
     */
    var renderFunnels = function (entered, selected) {
        if (entered.empty()) {
            return entered;
        }

        var funnel = entered.append('g')
            .attr({
                'id': function (d) {
                    return 'id-' + d.id;
                },
                'class': 'funnel component'
            })
            .classed('selected', selected)
            .call(nf.CanvasUtils.position);

        // funnel border
        funnel.append('rect')
            .attr({
                'rx': 2,
                'ry': 2,
                'class': 'border',
                'width': function (d) {
                    return d.dimensions.width;
                },
                'height': function (d) {
                    return d.dimensions.height;
                },
                'fill': 'transparent',
                'stroke': 'transparent'
            });

        // funnel body
        funnel.append('rect')
            .attr({
                'rx': 2,
                'ry': 2,
                'class': 'body',
                'width': function (d) {
                    return d.dimensions.width;
                },
                'height': function (d) {
                    return d.dimensions.height;
                },
                'filter': 'url(#component-drop-shadow)',
                'stroke-width': 0
            });

        // funnel icon
        funnel.append('text')
            .attr({
                'class': 'funnel-icon',
                'x': 9,
                'y': 34
            })
            .text('\ue803');

        // always support selection
        funnel.call(nf.Selectable.activate).call(nf.ContextMenu.activate);
    };

    /**
     * Updates the funnels in the specified selection.
     *
     * @param {selection} updated               The funnels to be updated
     */
    var updateFunnels = function (updated) {
        if (updated.empty()) {
            return;
        }

        // funnel border authorization
        updated.select('rect.border')
            .classed('unauthorized', function (d) {
                return d.permissions.canRead === false;
            });

        // funnel body authorization
        updated.select('rect.body')
            .classed('unauthorized', function (d) {
                return d.permissions.canRead === false;
            });

        updated.each(function () {
            var funnel = d3.select(this);

            // update the component behavior as appropriate
            nf.CanvasUtils.editable(funnel);
        });
    };

    /**
     * Removes the funnels in the specified selection.
     *
     * @param {selection} removed               The funnels to be removed
     */
    var removeFunnels = function (removed) {
        removed.remove();
    };

    return {
        /**
         * Initializes of the Processor handler.
         */
        init: function () {
            funnelMap = d3.map();

            // create the funnel container
            funnelContainer = d3.select('#canvas').append('g')
                .attr({
                    'pointer-events': 'all',
                    'class': 'funnels'
                });
        },

        /**
         * Adds the specified funnel entity.
         *
         * @param funnelEntities       The funnel
         * @param options           Configuration options
         */
        add: function (funnelEntities, options) {
            var selectAll = false;
            if (nf.Common.isDefinedAndNotNull(options)) {
                selectAll = nf.Common.isDefinedAndNotNull(options.selectAll) ? options.selectAll : selectAll;
            }

            var add = function (funnelEntity) {
                // add the funnel
                funnelMap.set(funnelEntity.id, $.extend({
                    type: 'Funnel',
                    dimensions: dimensions
                }, funnelEntity));
            };

            // determine how to handle the specified funnel status
            if ($.isArray(funnelEntities)) {
                $.each(funnelEntities, function (_, funnelEntity) {
                    add(funnelEntity);
                });
            } else if (nf.Common.isDefinedAndNotNull(funnelEntities)) {
                add(funnelEntities);
            }

            // apply the selection and handle new funnels
            var selection = select();
            selection.enter().call(renderFunnels, selectAll);
            selection.call(updateFunnels);
        },
        
        /**
         * Populates the graph with the specified funnels.
         *
         * @argument {object | array} funnelEntities                    The funnels to add
         * @argument {object} options                Configuration options
         */
        set: function (funnelEntities, options) {
            var selectAll = false;
            var transition = false;
            if (nf.Common.isDefinedAndNotNull(options)) {
                selectAll = nf.Common.isDefinedAndNotNull(options.selectAll) ? options.selectAll : selectAll;
                transition = nf.Common.isDefinedAndNotNull(options.transition) ? options.transition : transition;
            }

            var set = function (funnelEntity) {
                // add the funnel
                funnelMap.set(funnelEntity.id, $.extend({
                    type: 'Funnel',
                    dimensions: dimensions
                }, funnelEntity));
            };

            // determine how to handle the specified funnel status
            if ($.isArray(funnelEntities)) {
                $.each(funnelMap.keys(), function (_, key) {
                    funnelMap.remove(key);
                });
                $.each(funnelEntities, function (_, funnelEntity) {
                    set(funnelEntity);
                });
            } else if (nf.Common.isDefinedAndNotNull(funnelEntities)) {
                set(funnelEntities);
            }

            // apply the selection and handle all new processors
            var selection = select();
            selection.enter().call(renderFunnels, selectAll);
            selection.call(updateFunnels).call(nf.CanvasUtils.position, transition);
            selection.exit().call(removeFunnels);
        },

        /**
         * If the funnel id is specified it is returned. If no funnel id
         * specified, all funnels are returned.
         *
         * @param {string} id
         */
        get: function (id) {
            if (nf.Common.isUndefined(id)) {
                return funnelMap.values();
            } else {
                return funnelMap.get(id);
            }
        },

        /**
         * If the funnel id is specified it is refresh according to the current
         * state. If not funnel id is specified, all funnels are refreshed.
         *
         * @param {string} id      Optional
         */
        refresh: function (id) {
            if (nf.Common.isDefinedAndNotNull(id)) {
                d3.select('#id-' + id).call(updateFunnels);
            } else {
                d3.selectAll('g.funnel').call(updateFunnels);
            }
        },

        /**
         * Reloads the funnel state from the server and refreshes the UI.
         * If the funnel is currently unknown, this function just returns.
         *
         * @param {string} id The funnel id
         */
        reload: function (id) {
            if (funnelMap.has(id)) {
                var funnelEntity = funnelMap.get(id);
                return $.ajax({
                    type: 'GET',
                    url: funnelEntity.uri,
                    dataType: 'json'
                }).done(function (response) {
                    nf.Funnel.set(response);
                });
            }
        },

        /**
         * Positions the component.
         *
         * @param {string} id   The id
         */
        position: function (id) {
            d3.select('#id-' + id).call(nf.CanvasUtils.position);
        },

        /**
         * Removes the specified funnel.
         *
         * @param {array|string} funnels      The funnel id
         */
        remove: function (funnels) {
            if ($.isArray(funnels)) {
                $.each(funnels, function (_, funnel) {
                    funnelMap.remove(funnel);
                });
            } else {
                funnelMap.remove(funnels);
            }

            // apply the selection and handle all removed funnels
            select().exit().call(removeFunnels);
        },

        /**
         * Removes all processors.
         */
        removeAll: function () {
            nf.Funnel.remove(funnelMap.keys());
        }
    };
}());