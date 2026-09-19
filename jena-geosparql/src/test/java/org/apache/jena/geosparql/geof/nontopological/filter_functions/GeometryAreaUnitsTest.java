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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.junit.BeforeClass;
import org.junit.Test;

public class GeometryAreaUnitsTest {
    private final AreaFF function = new AreaFF();
    private static final String POLYGON = "'<http://www.opengis.net/def/crs/EPSG/0/27700> POLYGON ((0 0, 1000 0, 1000 1000, 0 1000, 0 0))'^^geo:wktLiteral";

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void explicitAreaUnitsConvertTheAreaQuantity() {
        Object[][] expected = {
                { Unit_URI.SQUARE_METRE_QUDT, 1_000_000.0 },
                { Unit_URI.SQUARE_KILOMETRE_QUDT, 1.0 },
                { Unit_URI.SQUARE_CENTIMETRE_QUDT, 10_000_000_000.0 },
                { Unit_URI.SQUARE_MILLIMETRE_QUDT, 1_000_000_000_000.0 },
                { Unit_URI.SQUARE_FOOT_QUDT, 10_763_910.416709722 },
                { Unit_URI.SQUARE_YARD_QUDT, 1_195_990.0463010803 },
                { Unit_URI.SQUARE_INCH_QUDT, 1_550_003_100.0062 },
                { Unit_URI.SQUARE_MILE_QUDT, 0.38610215854244585 },
                { Unit_URI.HECTARE_QUDT, 100.0 },
                { Unit_URI.ACRE_QUDT, 247.10538146716534 },
        };
        for (Object[] entry : expected) {
            String uri = (String) entry[0];
            double area = (double) entry[1];
            Node result = evaluate(POLYGON, "<" + uri + ">");
            assertEquals(uri, area, ((Number) result.getLiteralValue()).doubleValue(), area * 1e-12);
        }
    }

    @Test
    public void undeclaredOgcLookingAreaUriIsRejected() {
        String uri = "http://www.opengis.net/def/uom/OGC/1.0/squareMetre";
        assertThrows(ExprEvalException.class,
                () -> function.exec(NodeValue.makeNode("POINT EMPTY", WKTDatatype.INSTANCE),
                        NodeValue.makeNode(NodeFactory.createURI(uri))));
        assertNull(evaluate(POLYGON, "<" + uri + ">"));
    }

    @Test
    public void anyUriUnitLiteralIsAccepted() {
        assertEquals(NodeValue.makeDouble(1).asNode(), evaluate(POLYGON, "'" + Unit_URI.SQUARE_KILOMETRE_QUDT + "'^^xsd:anyURI"));
    }

    @Test
    public void invalidUnitsRaiseExpressionErrorsEvenForEmptyGeometry() {
        NodeValue empty = NodeValue.makeNode("POINT EMPTY", WKTDatatype.INSTANCE);
        for (NodeValue unit : new NodeValue[] { NodeValue.makeString(Unit_URI.SQUARE_METRE_QUDT), NodeValue.makeInteger(1),
                NodeValue.makeNode(NodeFactory.createURI("urn:unknown-unit")),
                NodeValue.makeNode(NodeFactory.createURI(Unit_URI.KILOMETRE_URL)) }) {
            assertThrows(ExprEvalException.class, () -> function.exec(empty, unit));
        }
    }

    @Test
    public void invalidUnitsLeaveBindUnboundForEmptyAndNonemptyInputs() {
        for (String unit : new String[] { "'" + Unit_URI.SQUARE_METRE_QUDT + "'", "1", "<urn:unknown-unit>",
                                        "<" + Unit_URI.KILOMETRE_URL + ">", "?missing" }) {
            assertNull(unit, evaluate(POLYGON, unit));
            assertNull(unit, evaluate("'POINT EMPTY'^^geo:wktLiteral", unit));
        }
    }

    @Test
    public void malformedGeometryRaisesExpressionError() {
        NodeValue invalid = NodeValue.makeNode("invalid", WKTDatatype.INSTANCE);
        NodeValue squareMetre = NodeValue.makeNode(NodeFactory.createURI(Unit_URI.SQUARE_METRE_QUDT));
        assertThrows(ExprEvalException.class, () -> function.exec(invalid, squareMetre));
    }

    private Node evaluate(String geometry, String unit) {
        return GeometryAreaFFTest.evaluate("geof:area(" + geometry + ", " + unit + ")");
    }
}
