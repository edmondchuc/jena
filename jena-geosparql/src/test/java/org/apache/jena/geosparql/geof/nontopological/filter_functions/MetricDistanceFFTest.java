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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.junit.BeforeClass;
import org.junit.Test;

public class MetricDistanceFFTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";
    private static final String WGS84 = "<http://www.opengis.net/def/crs/EPSG/0/4326> ";

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void projectedDistanceReturnsMetresAsDouble() {
        Node result = metricDistance(PROJECTED + "POINT (60 60)", PROJECTED + "POINT (63 64)");
        assertEquals(NodeValue.makeDouble(5).asNode(), result);
    }

    @Test
    public void compoundGeographicDistanceMatchesHorizontalControls() {
        double expected = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        for (int code : new int[] { 4326, 4979, 9707, 9518 }) {
            String crs = "<http://www.opengis.net/def/crs/EPSG/0/" + code + "> ";
            String first = crs + "POINT ZM (0 0 100 10)";
            String second = crs + "POINT ZM (0 1 900 90)";
            assertDistance(first, second, expected, 0.001);
            Node explicit = evaluate("geof:distance(" + literal(first) + ", " + literal(second)
                    + ", <" + Unit_URI.METRE_URL + ">)");
            assertNotNull(explicit);
            assertTrue(NodeValue.makeNode(explicit).isNumber());
            assertEquals(metricDistance(first, second), explicit);
            Node kilometres = evaluate("geof:distance(" + literal(first) + ", " + literal(second)
                    + ", <" + Unit_URI.KILOMETRE_URN + ">)");
            assertNotNull(kilometres);
            assertEquals(expected / 1000, NodeValue.makeNode(kilometres).getDouble(), 0.000001);
        }
        String projected = "<http://www.opengis.net/def/crs/EPSG/0/7405> ";
        assertDistance(projected + "POINT Z (0 0 100)", projected + "POINT Z (0 1 900)", 1, 0);
    }

    @Test
    public void compoundGeographicNonPointDistanceHandlesDatelineInEitherDirection() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        for (int code : new int[] { 9707, 9518 }) {
            String crs = "<http://www.opengis.net/def/crs/EPSG/0/" + code + "> ";
            String line = crs + "LINESTRING ZM (0 -179 100 10, 0 -170 200 20)";
            String point = crs + "POINT ZM (0 179 900 90)";
            assertDistance(line, point, 2 * degree, 0.001);
            assertDistance(point, line, 2 * degree, 0.001);
            assertDistance(crs + "LINESTRING Z (0 0 100, 0 10 200)", crs + "POINT Z (1 5 900)", degree, 0.001);
        }
    }

    @Test
    public void compoundGeographicSameCrsWorksWhenTransformationIsDisabled() {
        boolean previous = GeoSPARQLConfig.ALLOW_GEOMETRY_SRS_TRANSFORMATION;
        try {
            GeoSPARQLConfig.allowGeometrySRSTransformation(false);
            for (int code : new int[] { 9707, 9518 }) {
                String crs = "<http://www.opengis.net/def/crs/EPSG/0/" + code + "> ";
                assertDistance(crs + "POINT Z (0 0 100)", crs + "POINT Z (0 1 900)",
                               Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180, 0.001);
            }
        } finally {
            GeoSPARQLConfig.allowGeometrySRSTransformation(previous);
        }
    }

    @Test
    public void compoundGeographicEmptyArgumentsRaiseExplicitExpressionErrors() {
        for (int code : new int[] { 9707, 9518 }) {
            String crs = "<http://www.opengis.net/def/crs/EPSG/0/" + code + "> ";
            NodeValue empty = NodeValue.makeNode(crs + "POINT Z EMPTY", WKTDatatype.INSTANCE);
            NodeValue point = NodeValue.makeNode(crs + "POINT Z (0 1 900)", WKTDatatype.INSTANCE);
            MetricDistanceFF function = new MetricDistanceFF();
            ExprEvalException first = assertThrows(ExprEvalException.class, () -> function.exec(empty, point));
            ExprEvalException second = assertThrows(ExprEvalException.class, () -> function.exec(point, empty));
            assertTrue(first.getMessage().contains("empty geometry"));
            assertTrue(second.getMessage().contains("empty geometry"));
            assertThrows(ExprEvalException.class, () -> function.exec(empty, empty));
            assertNull(metricDistance(crs + "POINT Z EMPTY", crs + "POINT Z (0 1 900)"));
            assertNull(metricDistance(crs + "POINT Z (0 1 900)", crs + "POINT Z EMPTY"));
        }
    }

    @Test
    public void sourceSurveyFeetAreConvertedToMetres() {
        String surveyFeet = "<http://www.opengis.net/def/crs/EPSG/0/3438> ";
        assertDistance(surveyFeet + "POINT (0 0)", surveyFeet + "POINT (3000 4000)",
                       5000 * 1200.0 / 3937, 0.000001);
    }

    @Test
    public void geographicDistanceReturnsMetres() {
        assertDistance("POINT (11.41 53.63)", "POINT (11.57 48.13)", 611675.5, 0.1);
    }

    @Test
    public void geographicGradCoordinatesAreConvertedToDegrees() {
        String gradCrs = "<http://www.opengis.net/def/crs/EPSG/0/4807> ";
        double grad = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 200;
        assertDistance(gradCrs + "POINT (50 0)", gradCrs + "POINT (51 0)", grad, 0.001);
    }

    @Test
    public void geographicDistanceRespectsAuthorityAxisOrder() {
        assertDistance(WGS84 + "POINT (10 20)", WGS84 + "POINT (10 21)", 109505.7, 0.1);
    }

    @Test
    public void equivalentLocationsInDifferentCrsHaveZeroDistance() {
        assertDistance("POINT (20 10)", WGS84 + "POINT (10 20)", 0, 0.001);
        assertDistance(WGS84 + "POINT (10 20)", "POINT (20 10)", 0, 0.001);
    }

    @Test
    public void distanceUsesNearestGeometryPoints() {
        assertDistance(PROJECTED + "LINESTRING (0 0, 10 0)", PROJECTED + "POINT (5 3)", 3, 0);
    }

    @Test
    public void overlappingGeometriesHaveZeroDistance() {
        assertDistance(PROJECTED + "LINESTRING (0 0, 10 0)", PROJECTED + "POINT (5 0)", 0, 0);
        assertDistance("LINESTRING (0 0, 10 0)", "POINT (5 0)", 0, 0);
    }

    @Test
    public void gmlAndWktInputsCanBeCombined() {
        String point = """
            '<gml:Point xmlns:gml="http://www.opengis.net/gml/3.2"
                        srsName="http://www.opengis.net/def/crs/EPSG/0/27700">
               <gml:pos>60 60</gml:pos>
             </gml:Point>'^^geo:gmlLiteral
            """.replace("\n", " ");
        assertEquals(NodeValue.makeDouble(5).asNode(),
                     evaluate("geof:metricDistance(" + point + ", " + literal(PROJECTED + "POINT (63 64)") + ")"));
    }

    @Test
    public void metricDistanceMatchesExplicitMetreDistance() {
        String[][] pairs = {
            { PROJECTED + "POINT (60 60)", PROJECTED + "POINT (63 64)" },
            { "POINT (11.41 53.63)", "POINT (11.57 48.13)" },
            { "POINT (20 10)", WGS84 + "POINT (10 21)" }
        };
        for (String[] pair : pairs) {
            String arguments = literal(pair[0]) + ", " + literal(pair[1]);
            Node explicit = evaluate("geof:distance(" + arguments + ", <" + Unit_URI.METRE_URL + ">)");
            Node metric = metricDistance(pair[0], pair[1]);
            assertNotNull(arguments, explicit);
            assertNotNull(arguments, metric);
            assertTrue(arguments, NodeValue.makeNode(explicit).isNumber());
            assertTrue(arguments, NodeValue.makeNode(metric).isNumber());
            assertEquals(arguments, explicit, metric);
        }
        assertDistance("POINT (20 10)", WGS84 + "POINT (10 21)", 109505.7, 0.1);
    }

    @Test
    public void emptyProjectedInputsRetainExistingZeroDistance() {
        assertDistance(PROJECTED + "POINT EMPTY", PROJECTED + "POINT (60 60)", 0, 0);
        assertDistance(PROJECTED + "POINT (60 60)", PROJECTED + "POINT EMPTY", 0, 0);
    }

    @Test
    public void emptyGeographicInputsRaiseExpressionErrors() {
        NodeValue empty = NodeValue.makeNode("POINT EMPTY", WKTDatatype.INSTANCE);
        NodeValue point = NodeValue.makeNode("POINT (1 2)", WKTDatatype.INSTANCE);
        MetricDistanceFF function = new MetricDistanceFF();
        assertThrows(ExprEvalException.class, () -> function.exec(empty, point));
        assertThrows(ExprEvalException.class, () -> function.exec(point, empty));
        assertThrows(ExprEvalException.class, () -> function.exec(empty, empty));
        assertNull(metricDistance("POINT EMPTY", "POINT (1 2)"));
        assertNull(metricDistance("POINT (1 2)", "POINT EMPTY"));
        assertNull(metricDistance("LINESTRING EMPTY", "POINT (1 2)"));
    }

    @Test
    public void emptyGeographicInputsDoNotRequireCrsAlignment() {
        boolean previous = GeoSPARQLConfig.ALLOW_GEOMETRY_SRS_TRANSFORMATION;
        try {
            for (boolean allowTransformation : new boolean[] { true, false }) {
                GeoSPARQLConfig.allowGeometrySRSTransformation(allowTransformation);
                for (int code : new int[] { 4326, 4979, 9707, 9518 }) {
                    String emptyWkt = "<http://www.opengis.net/def/crs/EPSG/0/" + code + "> POINT Z EMPTY";
                    NodeValue empty = NodeValue.makeNode(emptyWkt, WKTDatatype.INSTANCE);
                    NodeValue point = NodeValue.makeNode("POINT (1 2)", WKTDatatype.INSTANCE);
                    MetricDistanceFF function = new MetricDistanceFF();
                    ExprEvalException first = assertThrows(ExprEvalException.class, () -> function.exec(empty, point));
                    ExprEvalException second = assertThrows(ExprEvalException.class, () -> function.exec(point, empty));
                    assertTrue(first.getMessage().contains("empty geometry"));
                    assertTrue(second.getMessage().contains("empty geometry"));
                    assertNull(metricDistance(emptyWkt, "POINT (1 2)"));
                    assertNull(metricDistance("POINT (1 2)", emptyWkt));
                }
            }
        } finally {
            GeoSPARQLConfig.allowGeometrySRSTransformation(previous);
        }
    }

    @Test
    public void invalidArgumentsRaiseExpressionErrorsInEitherPosition() {
        NodeValue valid = NodeValue.makeNode("POINT (1 2)", WKTDatatype.INSTANCE);
        NodeValue[] invalid = {
            NodeValue.makeNode(NodeFactory.createURI("urn:not-a-geometry")),
            NodeValue.makeInteger(42),
            NodeValue.makeString("POINT (1 2)"),
            NodeValue.makeNode("invalid", WKTDatatype.INSTANCE)
        };
        MetricDistanceFF function = new MetricDistanceFF();
        for (NodeValue value : invalid) {
            assertThrows(value.toString(), ExprEvalException.class, () -> function.exec(value, valid));
            assertThrows(value.toString(), ExprEvalException.class, () -> function.exec(valid, value));
        }
    }

    @Test
    public void invalidOrUnboundArgumentsLeaveBindUnbound() {
        for (String argument : new String[] { "<urn:not-a-geometry>", "42", "'POINT (1 2)'",
                                             "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(argument, evaluate("geof:metricDistance(" + argument + ", " + literal("POINT (1 2)") + ")"));
            assertNull(argument, evaluate("geof:metricDistance(" + literal("POINT (1 2)") + ", " + argument + ")"));
        }
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        String point = literal("POINT (1 2)");
        for (String arguments : new String[] { "", point, point + ", " + point + ", 1" }) {
            assertThrows(arguments, QueryBuildException.class,
                         () -> evaluate("geof:metricDistance(" + arguments + ")"));
        }
    }

    @Test
    public void disabledCrsTransformationRaisesExpressionError() {
        NodeValue first = NodeValue.makeNode("POINT (20 10)", WKTDatatype.INSTANCE);
        NodeValue second = NodeValue.makeNode(WGS84 + "POINT (10 20)", WKTDatatype.INSTANCE);
        boolean previous = GeoSPARQLConfig.ALLOW_GEOMETRY_SRS_TRANSFORMATION;
        try {
            GeoSPARQLConfig.allowGeometrySRSTransformation(false);
            assertThrows(ExprEvalException.class, () -> new MetricDistanceFF().exec(first, second));
            assertNull(metricDistance("POINT (20 10)", WGS84 + "POINT (10 20)"));
        } finally {
            GeoSPARQLConfig.allowGeometrySRSTransformation(previous);
        }
    }

    private static String literal(String wkt) {
        return "'" + wkt + "'^^geo:wktLiteral";
    }

    private static Node metricDistance(String first, String second) {
        return evaluate("geof:metricDistance(" + literal(first) + ", " + literal(second) + ")");
    }

    private static void assertDistance(String first, String second, double expected, double tolerance) {
        Node result = metricDistance(first, second);
        assertNotNull(first + " / " + second, result);
        assertEquals(XSDDatatype.XSDdouble.getURI(), result.getLiteralDatatypeURI());
        assertEquals(expected, ((Number)result.getLiteralValue()).doubleValue(), tolerance);
    }

    private static Node evaluate(String expression) {
        String query = """
            PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
            PREFIX geo: <http://www.opengis.net/ont/geosparql#>
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
