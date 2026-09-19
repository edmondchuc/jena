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
package org.apache.jena.geosparql.geof.nontopological.filter_functions;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/** Checks that a transformed geometry survives its source datatype's serialization. */
final class GeometryTransformResult {
    private GeometryTransformResult() {
    }

    static NodeValue asNodeValue(GeometryWrapper transformed, String targetURI) {
        if (!targetURI.equals(transformed.getSrsURI())) {
            throw new ExprEvalException("Transform result has a different CRS from the requested target.");
        }
        checkFinite(transformed.getParsingGeometry());
        NodeValue result = transformed.asNodeValue();
        GeometryWrapper parsed = GeometryWrapper.extract(result);
        if (!targetURI.equals(parsed.getSrsURI())
                || !transformed.getGeometryDatatypeURI().equals(parsed.getGeometryDatatypeURI())
                || transformed.getCoordinateSequenceDimensions() != parsed.getCoordinateSequenceDimensions()) {
            throw new ExprEvalException("Transform result loses its CRS, datatype, or coordinate layout when serialized.");
        }
        checkGeometry(transformed.getParsingGeometry(), parsed.getParsingGeometry());
        return result;
    }

    private static void checkFinite(Geometry geometry) {
        geometry.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence sequence, int index) {
                if (!Double.isFinite(sequence.getX(index)) || !Double.isFinite(sequence.getY(index))
                        || (sequence.hasZ() && !Double.isFinite(sequence.getZ(index)))
                        || (sequence.hasM() && !Double.isFinite(sequence.getM(index)))) {
                    throw new ExprEvalException("Transform result contains a non-finite coordinate.");
                }
            }
            @Override
            public boolean isDone() { return false; }

            @Override
            public boolean isGeometryChanged() { return false; }
        });
    }

    private static void checkGeometry(Geometry expected, Geometry parsed) {
        if (!expected.getGeometryType().equals(parsed.getGeometryType())
                || expected.isEmpty() != parsed.isEmpty()
                || expected.getNumGeometries() != parsed.getNumGeometries()) {
            throw new ExprEvalException("Transform result changes geometry structure when serialized.");
        }
        if (expected instanceof GeometryCollection collection) {
            GeometryCollection parsedCollection = (GeometryCollection) parsed;
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                checkGeometry(collection.getGeometryN(i), parsedCollection.getGeometryN(i));
            }
        } else if (expected instanceof Polygon polygon) {
            Polygon parsedPolygon = (Polygon) parsed;
            if (polygon.getNumInteriorRing() != parsedPolygon.getNumInteriorRing()) {
                throw new ExprEvalException("Transform result loses a polygon ring when serialized.");
            }
            checkSequence(polygon.getExteriorRing().getCoordinateSequence(),
                          parsedPolygon.getExteriorRing().getCoordinateSequence());
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                checkSequence(polygon.getInteriorRingN(i).getCoordinateSequence(),
                              parsedPolygon.getInteriorRingN(i).getCoordinateSequence());
            }
        } else if (expected instanceof LineString line) {
            checkSequence(line.getCoordinateSequence(), ((LineString) parsed).getCoordinateSequence());
        } else if (expected instanceof Point point) {
            checkSequence(point.getCoordinateSequence(), ((Point) parsed).getCoordinateSequence());
        }
    }

    private static void checkSequence(CoordinateSequence expected, CoordinateSequence parsed) {
        if (expected.size() != parsed.size() || expected.getDimension() != parsed.getDimension()
                || expected.getMeasures() != parsed.getMeasures()) {
            throw new ExprEvalException("Transform result loses coordinate layout when serialized.");
        }
        for (int i = 0; i < expected.size(); i++) {
            if (expected.getX(i) != parsed.getX(i) || expected.getY(i) != parsed.getY(i)
                    || (expected.hasZ() && expected.getZ(i) != parsed.getZ(i))
                    || (expected.hasM() && expected.getM(i) != parsed.getM(i))) {
                throw new ExprEvalException("Transform result changes a coordinate when serialized.");
            }
        }
    }
}
