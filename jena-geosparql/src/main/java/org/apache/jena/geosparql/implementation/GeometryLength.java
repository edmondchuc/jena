/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 *   SPDX-License-Identifier: Apache-2.0
 */
package org.apache.jena.geosparql.implementation;

import org.apache.jena.geosparql.implementation.great_circle.GreatCircleDistance;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;

final class GeometryLength {
    private GeometryLength() {
    }

    static double calculate(GeometryWrapper geometry, String targetUnitUri) {
        UnitsOfMeasure targetUnits = new UnitsOfMeasure(targetUnitUri);
        if (!targetUnits.isLinearUnits()) {
            throw new UnitsConversionException("Linear measurement requires linear target units.");
        }
        if (geometry.getSrsInfo().isGeographic()) {
            return UnitsOfMeasure.conversion(greatCircleLength(geometry.getXYGeometry()),
                    UnitsOfMeasure.METRE_UNITS, targetUnits);
        }
        double sourceLength = geometry.getXYGeometry().getLength();
        return UnitsOfMeasure.conversion(sourceLength,
                geometry.getUnitsOfMeasure(), targetUnits);
    }

    private static double greatCircleLength(Geometry geometry) {
        GreatCircleLengthFilter filter = new GreatCircleLengthFilter();
        geometry.apply(filter);
        return filter.length;
    }

    private static final class GreatCircleLengthFilter implements CoordinateSequenceFilter {
        private double length;

        @Override
        public void filter(CoordinateSequence sequence, int index) {
            if (index > 0) {
                length += GreatCircleDistance.haversineFormula(
                        sequence.getY(index - 1), sequence.getX(index - 1),
                        sequence.getY(index), sequence.getX(index));
            }
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean isGeometryChanged() {
            return false;
        }
    }
}
