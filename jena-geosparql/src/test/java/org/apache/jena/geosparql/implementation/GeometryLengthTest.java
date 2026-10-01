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

import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

public class GeometryLengthTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

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
    public void geographicCompoundCrsUsesHorizontalGreatCircleLength() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        GeometryWrapper horizontal = GeometryWrapper.extract(
                "<http://www.opengis.net/def/crs/EPSG/0/4326> LINESTRING (0 0, 0 1)", WKTDatatype.URI);
        for (int code : new int[] { 9707, 9518 }) {
            GeometryWrapper geometry = GeometryWrapper.extract("<http://www.opengis.net/def/crs/EPSG/0/" + code
                    + "> LINESTRING ZM (0 0 100 10, 0 1 900 90)", WKTDatatype.URI);
            assertEquals(degree, GeometryLength.calculate(geometry, Unit_URI.METRE_URL), 0.001);
            assertEquals(horizontal.length(), geometry.length(), 0.001);
            assertEquals(degree / 1000, geometry.perimeter(Unit_URI.KILOMETRE_URN), 0.000001);
        }
    }

    @Test
    public void projectedCompoundCrsUsesHorizontalPlanarLength() {
        GeometryWrapper geometry = GeometryWrapper.extract(
                "<http://www.opengis.net/def/crs/EPSG/0/7405> LINESTRING ZM (0 0 100 10, 3 4 900 90)", WKTDatatype.URI);
        assertEquals(5, GeometryLength.calculate(geometry, Unit_URI.METRE_URL), 0);
        GeometryWrapper horizontal = GeometryWrapper.extract(
                "<http://www.opengis.net/def/crs/EPSG/0/27700> LINESTRING (0 0, 3 4)", WKTDatatype.URI);
        assertEquals(horizontal.length(), geometry.length(), 0);
        assertEquals(0.005, geometry.perimeter(Unit_URI.KILOMETRE_URN), 0);
    }

    @Test
    public void unitValidationPrecedesEmptyResult() {
        GeometryWrapper empty = GeometryWrapper.extract("LINESTRING EMPTY", WKTDatatype.URI);
        assertThrows(org.apache.jena.geosparql.implementation.registry.UnitsURIException.class,
                     () -> empty.length("urn:unknown-unit"));
        assertThrows(UnitsConversionException.class, () -> empty.perimeter(Unit_URI.DEGREE_URL));
    }

    @Test
    public void polygonIncludesExteriorAndInteriorRings() {
        assertLength(PROJECTED + "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))", 48, 0);
    }

    @Test
    public void collectionsSumEveryMember() {
        assertLength(PROJECTED + "GEOMETRYCOLLECTION (LINESTRING (0 0, 3 4), POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0)))", 13, 0);
        assertLength(PROJECTED + "MULTILINESTRING ((0 0, 3 4), (100 100, 100 107))", 12, 0);
        assertLength(PROJECTED + "MULTIPOLYGON (((0 0, 2 0, 2 2, 0 2, 0 0)))", 8, 0);
    }

    @Test
    public void sourceSurveyFeetAreConvertedToMetres() {
        assertLength("<http://www.opengis.net/def/crs/EPSG/0/3438> LINESTRING (0 0, 3000 4000)",
                      5000 * 1200.0 / 3937, 0.000001);
    }

    @Test
    public void geographicPolygonIncludesItsHole() {
        double radians = Math.PI / 180;
        // Meridian sides and great-circle arcs at each ring's northern/southern latitude.
        double outer = 6 * radians + Math.acos(Math.pow(Math.sin(2 * radians), 2)
                + Math.pow(Math.cos(2 * radians), 2) * Math.cos(2 * radians));
        double inner = radians
                + Math.acos(Math.pow(Math.sin(radians / 2), 2)
                        + Math.pow(Math.cos(radians / 2), 2) * Math.cos(radians / 2))
                + Math.acos(Math.pow(Math.sin(radians), 2)
                        + Math.pow(Math.cos(radians), 2) * Math.cos(radians / 2));
        assertLength("POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0), (0.5 0.5, 0.5 1, 1 1, 1 0.5, 0.5 0.5))",
                      UnitsOfMeasure.EARTH_MEAN_RADIUS * (outer + inner), 0.001);
    }

    @Test
    public void geographicMultipolygonMembersAreMeasuredSeparately() {
        double radians = Math.PI / 180;
        double triangle = UnitsOfMeasure.EARTH_MEAN_RADIUS
                * (2 * radians + Math.acos(Math.pow(Math.cos(radians), 2)));
        assertLength("MULTIPOLYGON (((0 0, 1 0, 0 1, 0 0)), ((100 0, 101 0, 100 1, 100 0)))",
                      2 * triangle, 0.001);
    }

    @Test
    public void allEmptyTypesReturnZero() {
        for (String type : new String[] { "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION" }) {
            assertLength(type + " EMPTY", 0, 0);
            assertLength(PROJECTED + type + " EMPTY", 0, 0);
        }
    }

    @Test
    public void zAndMDoNotContributeToHorizontalLength() {
        assertLength(PROJECTED + "LINESTRING Z (0 0 0, 3 4 1000)", 5, 0);
        assertLength(PROJECTED + "LINESTRING M (0 0 0, 3 4 1000)", 5, 0);
        assertLength(PROJECTED + "LINESTRING ZM (0 0 0 0, 3 4 1000 2000)", 5, 0);
    }

    @Test
    public void geographicGradCoordinatesAreConvertedToDegrees() {
        double grad = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 200;
        assertLength("<http://www.opengis.net/def/crs/EPSG/0/4807> LINESTRING (50 0, 51 0)", grad, 0.001);
    }

    @Test
    public void nearAntipodalSegmentHasFiniteLength() {
        double halfCircumference = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS;
        assertLength("LINESTRING (0 -70, 180 70.00000001)", halfCircumference, 0.01);
    }

    @Test
    public void geographicMembersAreNotJoinedByExtraSegments() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertLength("MULTILINESTRING ((0 0, 1 0), (100 0, 101 0))", 2 * degree, 0.001);
    }

    @Test
    public void authorityAxisOrderMatchesEquivalentCrs84Geometry() {
        double crs84 = GeometryLength.calculate(geometry("LINESTRING (20 10, 21 10)"), Unit_URI.METRE_URL);
        double authority = GeometryLength.calculate(geometry("<http://www.opengis.net/def/crs/EPSG/0/4326> LINESTRING (10 20, 10 21)"), Unit_URI.METRE_URL);
        assertEquals(crs84, authority, 0);
        assertLength("LINESTRING (20 10, 21 10)", 109505.7, 0.1);
    }

    @Test
    public void antimeridianSegmentUsesTheShortArc() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertLength("LINESTRING (179 0, -179 0)", 2 * degree, 0.001);
    }

    @Test
    public void multipointsContributeZero() {
        assertLength("MULTIPOINT ((1 2), (40 50))", 0, 0);
        assertLength(PROJECTED + "POINT (1 2)", 0, 0);
    }

    private static GeometryWrapper geometry(String wkt) {
        return GeometryWrapper.extract(wkt, WKTDatatype.URI);
    }

    private static void assertLength(String wkt, double expected, double tolerance) {
        assertEquals(wkt, expected, GeometryLength.calculate(geometry(wkt), Unit_URI.METRE_URL), tolerance);
    }
}
