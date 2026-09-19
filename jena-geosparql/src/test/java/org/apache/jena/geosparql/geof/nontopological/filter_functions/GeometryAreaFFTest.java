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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GeometryAreaFFTest {
    private static final String PROJECTED = "<http://www.opengis.net/def/crs/EPSG/0/27700> ";
    private final String name;
    private final boolean metric;

    @Parameterized.Parameters(name = "function: {0}")
    public static List<Object[]> functions() {
        return List.of(new Object[] { "area", false }, new Object[] { "metricArea", true });
    }

    public GeometryAreaFFTest(String name, boolean metric) {
        this.name = name;
        this.metric = metric;
    }

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void polygonReturnsDoubleSquareMetres() {
        assertArea(PROJECTED + "POLYGON ((0 0, 3 0, 3 4, 0 4, 0 0))", 12);
    }

    @Test
    public void holesAreSubtractedAndMultiPolygonsAreSummed() {
        assertArea(PROJECTED + "MULTIPOLYGON (((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2)), "
            + "((20 0, 23 0, 23 4, 20 4, 20 0)))", 108);
    }

    @Test
    public void otherTypesIncludingPolygonCollectionsReturnZero() {
        for (String wkt : new String[] { "POINT (1 2)", "LINESTRING (0 0, 3 4)",
                "MULTIPOINT ((0 0), (1 1))", "MULTILINESTRING ((0 0, 1 1))",
                "GEOMETRYCOLLECTION (POLYGON ((0 0, 1 0, 1 1, 0 0)))" }) {
            assertArea(wkt, 0);
            assertArea(PROJECTED + wkt, 0);
        }
    }

    @Test
    public void emptyInputsReturnZeroIncludingGeographicPolygons() {
        for (String type : new String[] { "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION" }) {
            assertArea(type + " EMPTY", 0);
            assertArea(PROJECTED + type + " EMPTY", 0);
        }
    }

    @Test
    public void zAndMDoNotContributeToArea() {
        assertArea(PROJECTED + "POLYGON Z ((0 0 1, 3 0 2, 3 4 3, 0 4 4, 0 0 1))", 12);
        assertArea(PROJECTED + "POLYGON M ((0 0 1, 3 0 2, 3 4 3, 0 4 4, 0 0 1))", 12);
        assertArea(PROJECTED + "POLYGON ZM ((0 0 1 9, 3 0 2 8, 3 4 3 7, 0 4 4 6, 0 0 1 9))", 12);
    }

    @Test
    public void geographicPolygonsRaiseExpressionErrors() {
        for (String wkt : new String[] { "POLYGON ((0 0, 1 0, 1 1, 0 0))", "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))" }) {
            assertNull(evaluate(call("'" + wkt + "'^^geo:wktLiteral")));
            assertThrows(ExprEvalException.class, () -> exec(NodeValue.makeNode(wkt, WKTDatatype.INSTANCE)));
        }
    }

    @Test
    public void gmlPolygonUsesTheSameAreaCalculation() {
        String gml = """
            '<gml:Polygon xmlns:gml="http://www.opengis.net/gml/3.2"
                srsName="http://www.opengis.net/def/crs/EPSG/0/27700">
              <gml:exterior><gml:LinearRing><gml:posList>0 0 3 0 3 4 0 4 0 0</gml:posList></gml:LinearRing></gml:exterior>
            </gml:Polygon>'^^geo:gmlLiteral
            """.replace("\n", " ");
        assertEquals(NodeValue.makeDouble(12).asNode(), evaluate(call(gml)));
    }

    @Test
    public void malformedOrUnboundGeometryLeavesBindUnbound() {
        for (String value : new String[] { "42", "'POINT (1 2)'", "<urn:geometry>", "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(value, evaluate(call(value)));
        }
    }

    @Test
    public void invalidGeometryArgumentsRaiseExpressionErrors() {
        for (NodeValue value : new NodeValue[] { NodeValue.makeInteger(42), NodeValue.makeString("POINT (1 2)"),
                NodeValue.makeNode(NodeFactory.createURI("urn:geometry")), NodeValue.makeNode("invalid", WKTDatatype.INSTANCE) }) {
            assertThrows(ExprEvalException.class, () -> exec(value));
        }
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "()"));
        String geometry = "'POINT EMPTY'^^geo:wktLiteral";
        String wrong = metric ? geometry + ", 1" : geometry;
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "(" + wrong + ")"));
        assertThrows(QueryBuildException.class, () -> evaluate("geof:" + name + "(" + geometry + ", 1, 2)"));
    }

    private NodeValue exec(NodeValue value) {
        return metric ? new MetricAreaFF().exec(value)
            : new AreaFF().exec(value, NodeValue.makeNode(NodeFactory.createURI(Unit_URI.SQUARE_METRE_QUDT)));
    }

    private String call(String geometry) {
        return "geof:" + name + "(" + geometry + (metric ? "" : ", <" + Unit_URI.SQUARE_METRE_QUDT + ">") + ")";
    }

    private void assertArea(String wkt, double expected) {
        assertEquals(wkt, NodeValue.makeDouble(expected).asNode(), evaluate(call("'" + wkt + "'^^geo:wktLiteral")));
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
