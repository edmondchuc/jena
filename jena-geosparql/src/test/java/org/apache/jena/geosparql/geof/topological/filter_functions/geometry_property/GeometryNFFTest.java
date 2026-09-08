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
package org.apache.jena.geosparql.geof.topological.filter_functions.geometry_property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
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

public class GeometryNFFTest {
    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void selectsFirstAndLastMembersUsingOneBasedIndices() {
        String wkt = "MULTIPOINT ((1 2), (3 4), (5 6))";
        assertGeometry("POINT (1 2)", select(wkt, "1"));
        assertGeometry("POINT (5 6)", select(wkt, "3"));
    }

    @Test
    public void acceptsLosslesslyIntegralNumericTypes() {
        for (String index : new String[] { "2", "2.0", "2e0", "'2'^^xsd:float", "'2'^^xsd:short" }) {
            assertGeometry("POINT (3 4)", select("MULTIPOINT ((1 2), (3 4))", index));
        }
    }

    @Test
    public void atomicIndexOnePreservesOriginalLiteral() {
        for (String wkt : new String[] { "POINT (1 2)", "LINESTRING (0 0, 1 1)",
                "POLYGON ((0 0, 1 0, 0 1, 0 0))", "POINT Z EMPTY", "POINT M EMPTY", "POINT ZM EMPTY" }) {
            assertEquals(wkt, NodeValue.makeNode(wkt, WKTDatatype.INSTANCE).asNode(), select(wkt, "1"));
        }
    }

    @Test
    public void selectedMemberHasItsOwnTopologicalDimension() {
        assertGeometry("LINESTRING (1 2, 3 4)", select(
            "GEOMETRYCOLLECTION (POINT (9 9), LINESTRING (1 2, 3 4))", "2"));
    }

    @Test
    public void selectsMembersOfMultiLinesAndMultiPolygons() {
        assertGeometry("LINESTRING (3 4, 5 6)", select("MULTILINESTRING ((0 0, 1 1), (3 4, 5 6))", "2"));
        assertGeometry("POLYGON ((0 0, 1 0, 0 1, 0 0))", select("MULTIPOLYGON (((0 0, 1 0, 0 1, 0 0)))", "1"));
    }

    @Test
    public void selectedMembersRetainZAndM() {
        for (String marker : new String[] { "Z", "M", "ZM" }) {
            String coordinates = marker.equals("ZM") ? "1 2 3 4" : "1 2 3";
            assertGeometry("POINT " + marker + " (" + coordinates + ")",
                select("GEOMETRYCOLLECTION (POINT " + marker + " (" + coordinates + "))", "1"));
        }
    }

    @Test
    public void emptySelectedMembersRetainCoordinateMarkers() {
        for (String marker : new String[] { "Z", "M", "ZM" }) {
            for (String type : new String[] { "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION" }) {
                String member = type + " " + marker + " EMPTY";
                assertGeometry(member, select("GEOMETRYCOLLECTION " + marker + " (" + member + ")", "1"));
            }
        }
    }

    @Test
    public void authorityAxisOrderAndZSurviveSerialization() {
        String crs = "<http://www.opengis.net/def/crs/EPSG/0/4979> ";
        assertGeometry(crs + "POINT Z (10 100 7)", select(crs + "MULTIPOINT Z ((10 100 7), (20 120 9))", "1"));
    }

    @Test
    public void gmlResultRetainsSourceDatatypeAndCrs() {
        String gml = """
            '<gml:MultiPoint xmlns:gml="http://www.opengis.net/gml/3.2"
                srsName="http://www.opengis.net/def/crs/EPSG/0/4979">
              <gml:pointMember><gml:Point><gml:pos srsDimension="3">10 100 7</gml:pos></gml:Point></gml:pointMember>
            </gml:MultiPoint>'^^geo:gmlLiteral
            """.replace("\n", " ");
        Node result = evaluate("geof:geometryN(" + gml + ", 1)");
        assertNotNull(result);
        assertEquals("http://www.opengis.net/ont/geosparql#gmlLiteral", result.getLiteralDatatypeURI());
        assertGeometry("<http://www.opengis.net/def/crs/EPSG/0/4979> POINT Z (10 100 7)", result);
    }

    @Test
    public void selectedEmptyGmlCollectionRetainsItsMember() {
        String gml = """
            '<gml:MultiGeometry xmlns:gml="http://www.opengis.net/gml/3.2"
                srsName="http://www.opengis.net/def/crs/EPSG/0/4979">
              <gml:geometryMember><gml:MultiGeometry>
                <gml:geometryMember><gml:Point/></gml:geometryMember>
              </gml:MultiGeometry></gml:geometryMember>
            </gml:MultiGeometry>'^^geo:gmlLiteral
            """.replace("\n", " ");
        Node result = evaluate("geof:geometryN(" + gml + ", 1)");
        assertNotNull(result);
        GeometryWrapper selected = GeometryWrapper.extract(result);
        assertEquals(1, selected.getParsingGeometry().getNumGeometries());
        assertEquals("Point", selected.getGeometryN(1).getGeometryType());
        assertEquals(3, selected.getGeometryN(1).getCoordinateDimension());
    }

    @Test
    public void invalidIndicesRaiseExpressionErrorsAndLeaveBindUnbound() {
        for (String index : new String[] { "0", "-1", "3", "1.5", "1.00000000000000000001",
                "2147483648", "999999999999999999999999999999", "'NaN'^^xsd:double",
                "'INF'^^xsd:double", "'-INF'^^xsd:float", "'1'", "true", "<urn:index>" }) {
            assertNull(index, select("MULTIPOINT ((1 2), (3 4))", index));
        }
        GeometryNFF function = new GeometryNFF();
        NodeValue geometry = NodeValue.makeNode("POINT (1 2)", WKTDatatype.INSTANCE);
        for (NodeValue index : new NodeValue[] { NodeValue.makeInteger(0), NodeValue.makeInteger(2),
                NodeValue.makeDecimal("1.5"), NodeValue.makeDouble(Double.NaN), NodeValue.makeString("1") }) {
            assertThrows(ExprEvalException.class, () -> function.exec(geometry, index));
        }
    }

    @Test
    public void emptyCollectionsHaveNoMemberAtIndexOne() {
        for (String wkt : new String[] { "MULTIPOINT EMPTY", "MULTILINESTRING EMPTY", "MULTIPOLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY" }) {
            assertNull(wkt, select(wkt, "1"));
        }
    }

    @Test
    public void invalidGeometriesRaiseExpressionErrors() {
        GeometryNFF function = new GeometryNFF();
        for (NodeValue value : new NodeValue[] { NodeValue.makeString("POINT (1 2)"), NodeValue.makeInteger(1),
                NodeValue.makeNode(NodeFactory.createURI("urn:geometry")), NodeValue.makeNode("invalid", WKTDatatype.INSTANCE) }) {
            assertThrows(ExprEvalException.class, () -> function.exec(value, NodeValue.makeInteger(1)));
        }
        for (String value : new String[] { "'POINT (1 2)'", "42", "<urn:geometry>", "'invalid'^^geo:wktLiteral", "?missing" }) {
            assertNull(evaluate("geof:geometryN(" + value + ", 1)"));
        }
        assertNull(select("POINT (1 2)", "?missing"));
    }

    @Test
    public void wrongArityIsRejectedAtQueryBuild() {
        for (String arguments : new String[] { "", "'POINT EMPTY'^^geo:wktLiteral", "'POINT EMPTY'^^geo:wktLiteral, 1, 2" }) {
            assertThrows(QueryBuildException.class, () -> evaluate("geof:geometryN(" + arguments + ")"));
        }
    }

    private static Node select(String wkt, String index) {
        return evaluate("geof:geometryN('" + wkt + "'^^geo:wktLiteral, " + index + ")");
    }

    private static void assertGeometry(String expectedWkt, Node result) {
        assertNotNull(expectedWkt, result);
        GeometryWrapper expected = GeometryWrapper.extract(expectedWkt, WKTDatatype.URI);
        GeometryWrapper actual = GeometryWrapper.extract(result);
        assertEquals(expected.getSrsURI(), actual.getSrsURI());
        assertEquals(expected.getCoordinateSequenceDimensions(), actual.getCoordinateSequenceDimensions());
        assertEquals(expected.getTopologicalDimension(), actual.getTopologicalDimension());
        assertEquals(expected.getParsingGeometry().toText(), actual.getParsingGeometry().toText());
        // Serialize both through WKT to compare Z/M ordinates across input datatypes.
        assertEquals(org.apache.jena.geosparql.implementation.parsers.wkt.WKTWriter.write(expected),
                     org.apache.jena.geosparql.implementation.parsers.wkt.WKTWriter.write(actual));
    }

    private static Node evaluate(String expression) {
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
