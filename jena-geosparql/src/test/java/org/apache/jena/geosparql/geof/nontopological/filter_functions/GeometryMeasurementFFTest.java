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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.expr.NodeValue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GeometryMeasurementFFTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";
    private static final String METRE = "<" + Unit_URI.METRE_URL + ">";
    private final String name;
    private final boolean metric;

    @Parameterized.Parameters(name = "function: {0}")
    public static List<Object[]> functions() {
        return List.of(new Object[] { "length", false }, new Object[] { "perimeter", false },
                       new Object[] { "metricLength", true }, new Object[] { "metricPerimeter", true });
    }

    public GeometryMeasurementFFTest(String name, boolean metric) {
        this.name = name;
        this.metric = metric;
    }

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void projectedLineReturnsADoubleInMetres() {
        assertEquals(NodeValue.makeDouble(5).asNode(), measure(PROJECTED + "LINESTRING (0 0, 3 4)"));
    }

    @Test
    public void polygonIncludesExteriorAndInteriorRings() {
        assertMeasure(PROJECTED + "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))", 48, 0);
    }

    @Test
    public void collectionsSumEveryMember() {
        assertMeasure(PROJECTED + "GEOMETRYCOLLECTION (LINESTRING (0 0, 3 4), POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0)))", 13, 0);
        assertMeasure(PROJECTED + "MULTILINESTRING ((0 0, 3 4), (100 100, 100 107))", 12, 0);
        assertMeasure(PROJECTED + "MULTIPOLYGON (((0 0, 2 0, 2 2, 0 2, 0 0)))", 8, 0);
    }

    @Test
    public void pointsContributeZero() {
        assertMeasure("POINT (1 2)", 0, 0);
        assertMeasure("MULTIPOINT ((1 2), (40 50))", 0, 0);
        assertMeasure(PROJECTED + "POINT (1 2)", 0, 0);
    }

    @Test
    public void allEmptyTypesReturnZero() {
        for (String type : new String[] { "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION" }) {
            assertMeasure(type + " EMPTY", 0, 0);
            assertMeasure(PROJECTED + type + " EMPTY", 0, 0);
        }
    }

    @Test
    public void zAndMDoNotContributeToHorizontalLength() {
        assertMeasure(PROJECTED + "LINESTRING Z (0 0 0, 3 4 1000)", 5, 0);
        assertMeasure(PROJECTED + "LINESTRING M (0 0 0, 3 4 1000)", 5, 0);
        assertMeasure(PROJECTED + "LINESTRING ZM (0 0 0 0, 3 4 1000 2000)", 5, 0);
    }

    @Test
    public void geographicSegmentsUseGreatCircleMetres() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertMeasure("LINESTRING (0 0, 1 0, 2 0)", 2 * degree, 0.001);
    }

    @Test
    public void geographicMembersAreNotJoinedByExtraSegments() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertMeasure("MULTILINESTRING ((0 0, 1 0), (100 0, 101 0))", 2 * degree, 0.001);
    }

    @Test
    public void authorityAxisOrderMatchesEquivalentCrs84Geometry() {
        Node crs84 = measure("LINESTRING (20 10, 21 10)");
        Node authority = measure("<http://www.opengis.net/def/crs/EPSG/0/4326> LINESTRING (10 20, 10 21)");
        assertEquals(crs84, authority);
        assertMeasure("LINESTRING (20 10, 21 10)", 109505.7, 0.1);
    }

    @Test
    public void antimeridianSegmentUsesTheShortArc() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        assertMeasure("LINESTRING (179 0, -179 0)", 2 * degree, 0.001);
    }

    @Test
    public void gmlUsesTheSameMeasurement() {
        String gml = """
            '<gml:LineString xmlns:gml="http://www.opengis.net/gml/3.2"
                srsName="http://www.opengis.net/def/crs/EPSG/0/27700">
              <gml:posList>0 0 3 4</gml:posList>
            </gml:LineString>'^^geo:gmlLiteral
            """.replace("\n", " ");
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(call(gml)));
    }

    @Test
    public void malformedOrUnboundGeometryLeavesBindUnbound() {
        for (String value : new String[] { "42", "'POINT (1 2)'", "<urn:geometry>", "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(value, evaluate(call(value)));
        }
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        String geometry = "'POINT EMPTY'^^geo:wktLiteral";
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "()"));
        String wrong = metric ? geometry + ", " + METRE : geometry;
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "(" + wrong + ")"));
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "(" + geometry + ", " + METRE + ", 1)"));
    }

    private String call(String geometry) {
        return "geof:" + name + "(" + geometry + (metric ? "" : ", " + METRE) + ")";
    }

    private Node measure(String wkt) {
        return evaluate(call("'" + wkt + "'^^geo:wktLiteral"));
    }

    private void assertMeasure(String wkt, double expected, double tolerance) {
        Node result = measure(wkt);
        assertNotNull(wkt, result);
        assertEquals(XSDDatatype.XSDdouble.getURI(), result.getLiteralDatatypeURI());
        assertEquals(wkt, expected, ((Number)result.getLiteralValue()).doubleValue(), tolerance);
    }

    static Node evaluate(String expression) {
        String query = """
            PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
            PREFIX geo: <http://www.opengis.net/ont/geosparql#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            SELECT ?result WHERE { BIND(%s AS ?result) }
            """.formatted(expression);
        try (QueryExecution execution = QueryExecution.create(query, ModelFactory.createDefaultModel())) {
            ResultSet results = execution.execSelect();
            assertTrue(expression, results.hasNext());
            QuerySolution solution = results.next();
            assertFalse(expression, results.hasNext());
            return solution.contains("result") ? solution.get("result").asNode() : null;
        }
    }
}
