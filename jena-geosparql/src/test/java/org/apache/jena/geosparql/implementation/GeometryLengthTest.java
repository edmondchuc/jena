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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

public class GeometryLengthTest {
    @Test
    public void geographicPolygonIncludesItsClosingSegment() {
        GeometryWrapper geometry = GeometryWrapper.extract("POLYGON ((0 0, 1 0, 0 1, 0 0))", WKTDatatype.URI);
        double radians = Math.PI / 180;
        double expected = UnitsOfMeasure.EARTH_MEAN_RADIUS * (2 * radians + Math.acos(Math.cos(radians) * Math.cos(radians)));
        assertEquals(expected, geometry.length(), 0.001);
        assertEquals(expected, geometry.perimeter(), 0.001);
    }

    @Test
    public void nestedCollectionsSumSegmentsWithoutConnectingMembers() {
        GeometryFactory factory = new GeometryFactory();
        Geometry first = factory.createLineString(new Coordinate[] { new Coordinate(0, 0), new Coordinate(1, 0) });
        Geometry second = factory.createLineString(new Coordinate[] { new Coordinate(100, 0), new Coordinate(101, 0) });
        Geometry nested = factory.createGeometryCollection(new Geometry[] { second, factory.createPoint() });
        GeometryWrapper geometry = new GeometryWrapper(factory.createGeometryCollection(new Geometry[] { first, nested }), WKTDatatype.URI);
        double expected = 2 * Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertEquals(expected, geometry.length(), 0.001);
        assertEquals(expected / 1000, geometry.perimeter(Unit_URI.KILOMETRE_URN), 0.000001);
    }

    @Test
    public void unitValidationPrecedesEmptyResult() {
        GeometryWrapper empty = GeometryWrapper.extract("LINESTRING EMPTY", WKTDatatype.URI);
        assertThrows(org.apache.jena.geosparql.implementation.registry.UnitsURIException.class,
                     () -> empty.length("urn:unknown-unit"));
        assertThrows(UnitsConversionException.class, () -> empty.perimeter(Unit_URI.DEGREE_URL));
    }
}
