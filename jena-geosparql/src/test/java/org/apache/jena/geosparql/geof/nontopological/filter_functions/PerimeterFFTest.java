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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.junit.BeforeClass;
import org.junit.Test;

public class PerimeterFFTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";
    private static final String METRE = "<" + Unit_URI.METRE_URL + ">";
    private static final String LINE = "'<http://www.opengis.net/def/crs/EPSG/0/27700> LINESTRING (0 0, 3000 4000)'^^geo:wktLiteral";
    private static final String SURVEY_FEET_LINE = "'<http://www.opengis.net/def/crs/EPSG/0/3438> LINESTRING (0 0, 3000 4000)'^^geo:wktLiteral";
    private final PerimeterFF function = new PerimeterFF();

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void projectedLineReturnsADoubleInMetres() {
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate("'" + PROJECTED + "LINESTRING (0 0, 3 4)'^^geo:wktLiteral", METRE));
    }

    @Test
    public void pointsContributeZero() {
        assertEquals(NodeValue.makeDouble(0).asNode(), evaluate("'POINT (1 2)'^^geo:wktLiteral", METRE));
    }

    @Test
    public void emptyLineReturnsZero() {
        assertEquals(NodeValue.makeDouble(0).asNode(), evaluate("'LINESTRING EMPTY'^^geo:wktLiteral", METRE));
    }

    @Test
    public void projectedPolygonReturnsItsPerimeter() {
        assertEquals(NodeValue.makeDouble(12).asNode(), evaluate("'" + PROJECTED + "POLYGON ((0 0, 3 0, 3 4, 0 0))'^^geo:wktLiteral", METRE));
    }

    @Test
    public void geographicSegmentsUseGreatCircleMetres() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        Node result = evaluate("'LINESTRING (0 0, 1 0, 2 0)'^^geo:wktLiteral", METRE);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDdouble.getURI(), result.getLiteralDatatypeURI());
        assertEquals(2 * degree, ((Number)result.getLiteralValue()).doubleValue(), 0.001);
    }

    @Test
    public void gmlUsesTheSameMeasurement() {
        String gml = """
            '<gml:LineString xmlns:gml="http://www.opengis.net/gml/3.2"
                srsName="http://www.opengis.net/def/crs/EPSG/0/27700">
              <gml:posList>0 0 3 4</gml:posList>
            </gml:LineString>'^^geo:gmlLiteral
            """.replace("\n", " ");
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(gml, METRE));
    }

    @Test
    public void malformedOrUnboundGeometryLeavesBindUnbound() {
        for (String value : new String[] { "42", "'POINT (1 2)'", "<urn:geometry>", "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(value, evaluate(value, METRE));
        }
    }

    @Test
    public void invalidGeometryArgumentsRaiseExpressionErrors() {
        NodeValue metre = NodeValue.makeNode(NodeFactory.createURI(Unit_URI.METRE_URL));
        for (NodeValue value : new NodeValue[] { NodeValue.makeInteger(42), NodeValue.makeString("POINT (1 2)"),
                NodeValue.makeNode(NodeFactory.createURI("urn:geometry")), NodeValue.makeNode("invalid", WKTDatatype.INSTANCE) }) {
            assertThrows(ExprEvalException.class, () -> function.exec(value, metre));
        }
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        String geometry = "'POINT EMPTY'^^geo:wktLiteral";
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:perimeter()"));
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:perimeter(" + geometry + ")"));
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:perimeter(" + geometry + ", " + METRE + ", 1)"));
    }

    @Test
    public void requestedKilometresAreConvertedFromSourceSurveyFeet() {
        assertEquals(NodeValue.makeDouble(1.524003).asNode(), evaluate(SURVEY_FEET_LINE, "<" + Unit_URI.KILOMETRE_URN + ">"));
    }

    @Test
    public void anyUriUnitLiteralIsAccepted() {
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(LINE, "'" + Unit_URI.KILOMETRE_URN + "'^^xsd:anyURI"));
    }

    @Test
    public void anyUriWhitespaceIsNormalizedInDirectExecution() {
        NodeValue geometry = NodeValue.makeNode(
                "<http://www.opengis.net/def/crs/EPSG/0/27700> LINESTRING (0 0, 3000 4000)", WKTDatatype.INSTANCE);
        for (String lexical : new String[] { "  " + Unit_URI.KILOMETRE_URN + "  ",
                "\t\r\n" + Unit_URI.KILOMETRE_URN + "\n\r\t" }) {
            NodeValue unit = NodeValue.makeNode(lexical, XSDDatatype.XSDanyURI);
            assertEquals(lexical, 5, function.exec(geometry, unit).getDouble(), 0);
        }
    }

    @Test
    public void anyUriWhitespaceIsNormalizedInRegisteredQueries() {
        for (String lexical : new String[] { "  " + Unit_URI.KILOMETRE_URN + "  ",
                "\\t\\r\\n" + Unit_URI.KILOMETRE_URN + "\\n\\r\\t" }) {
            assertEquals(lexical, NodeValue.makeDouble(5).asNode(), evaluate(LINE, "'" + lexical + "'^^xsd:anyURI"));
        }
    }

    @Test
    public void malformedAnyUriRaisesDatatypeExpressionError() {
        NodeValue geometry = NodeValue.makeNode(
                "<http://www.opengis.net/def/crs/EPSG/0/27700> LINESTRING (0 0, 3000 4000)", WKTDatatype.INSTANCE);
        NodeValue unit = NodeValue.makeNode("http://[", XSDDatatype.XSDanyURI);
        ExprEvalException error = assertThrows(ExprEvalException.class, () -> function.exec(geometry, unit));
        assertTrue(error.getCause() instanceof DatatypeFormatException);
        assertNull(evaluate(LINE, "'http://['^^xsd:anyURI"));
    }

    @Test
    public void invalidUnitsRaiseExpressionErrorsEvenForEmptyGeometry() {
        NodeValue empty = NodeValue.makeNode("POINT EMPTY", WKTDatatype.INSTANCE);
        for (NodeValue unit : new NodeValue[] { NodeValue.makeString(Unit_URI.METRE_URL), NodeValue.makeInteger(1),
                NodeValue.makeNode(NodeFactory.createURI("urn:unknown-unit")),
                NodeValue.makeNode(NodeFactory.createURI(Unit_URI.DEGREE_URL)) }) {
            assertThrows(ExprEvalException.class, () -> function.exec(empty, unit));
        }
    }

    @Test
    public void invalidUnitsLeaveBindUnboundForEmptyAndNonemptyInputs() {
        for (String unit : new String[] { "'" + Unit_URI.METRE_URL + "'", "1", "<urn:unknown-unit>",
                                        "<" + Unit_URI.DEGREE_URL + ">", "?missing" }) {
            assertNull(unit, evaluate(LINE, unit));
            assertNull(unit, evaluate("'POINT EMPTY'^^geo:wktLiteral", unit));
        }
    }

    private Node evaluate(String geometry, String unit) {
        return MeasurementFunctionTestSupport.evaluate("geof:perimeter(" + geometry + ", " + unit + ")");
    }
}
