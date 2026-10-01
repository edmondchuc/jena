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

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.junit.BeforeClass;
import org.junit.Test;

public class MetricLengthFFTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";
    private final MetricLengthFF function = new MetricLengthFF();

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void projectedLineReturnsADoubleInMetres() {
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate("'" + PROJECTED + "LINESTRING (0 0, 3 4)'^^geo:wktLiteral"));
    }

    @Test
    public void pointsContributeZero() {
        assertEquals(NodeValue.makeDouble(0).asNode(), evaluate("'POINT (1 2)'^^geo:wktLiteral"));
    }

    @Test
    public void emptyLineReturnsZero() {
        assertEquals(NodeValue.makeDouble(0).asNode(), evaluate("'LINESTRING EMPTY'^^geo:wktLiteral"));
    }

    @Test
    public void projectedPolygonReturnsItsPerimeter() {
        assertEquals(NodeValue.makeDouble(12).asNode(), evaluate("'" + PROJECTED + "POLYGON ((0 0, 3 0, 3 4, 0 0))'^^geo:wktLiteral"));
    }

    @Test
    public void geographicSegmentsUseGreatCircleMetres() {
        double degree = Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 180;
        Node result = evaluate("'LINESTRING (0 0, 1 0, 2 0)'^^geo:wktLiteral");
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
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(gml));
    }

    @Test
    public void malformedOrUnboundGeometryLeavesBindUnbound() {
        for (String value : new String[] { "42", "'POINT (1 2)'", "<urn:geometry>", "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(value, evaluate(value));
        }
    }

    @Test
    public void invalidGeometryArgumentsRaiseExpressionErrors() {
        for (NodeValue value : new NodeValue[] { NodeValue.makeInteger(42), NodeValue.makeString("POINT (1 2)"),
                NodeValue.makeNode(NodeFactory.createURI("urn:geometry")), NodeValue.makeNode("invalid", WKTDatatype.INSTANCE) }) {
            assertThrows(ExprEvalException.class, () -> function.exec(value));
        }
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        String geometry = "'POINT EMPTY'^^geo:wktLiteral";
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:metricLength()"));
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:metricLength(" + geometry + ", 1)"));
        assertThrows(QueryBuildException.class, () -> MeasurementFunctionTestSupport.evaluate("geof:metricLength(" + geometry + ", 1, 2)"));
    }

    private Node evaluate(String geometry) {
        return MeasurementFunctionTestSupport.evaluate("geof:metricLength(" + geometry + ")");
    }
}
