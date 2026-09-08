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

import java.util.List;

import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase2;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GeometryMeasurementUnitsTest {
    private final String name;
    private final FunctionBase2 function;
    private static final String LINE = "'<http://www.opengis.net/def/crs/EPSG/0/27700> LINESTRING (0 0, 3000 4000)'^^geo:wktLiteral";

    @Parameterized.Parameters(name = "function: {0}")
    public static List<Object[]> functions() {
        return List.of(new Object[] { "length", new LengthFF() }, new Object[] { "perimeter", new PerimeterFF() });
    }

    public GeometryMeasurementUnitsTest(String name, FunctionBase2 function) {
        this.name = name;
        this.function = function;
    }

    @BeforeClass
    public static void setup() {
        GeoSPARQLConfig.setupNoIndex();
    }

    @Test
    public void requestedKilometresAreConvertedFromSourceMetres() {
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(LINE, "<" + Unit_URI.KILOMETRE_URN + ">"));
    }

    @Test
    public void anyUriUnitLiteralIsAccepted() {
        assertEquals(NodeValue.makeDouble(5).asNode(), evaluate(LINE, "'" + Unit_URI.KILOMETRE_URN + "'^^xsd:anyURI"));
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

    @Test
    public void malformedGeometryRaisesExpressionError() {
        NodeValue invalid = NodeValue.makeNode("invalid", WKTDatatype.INSTANCE);
        NodeValue metre = NodeValue.makeNode(NodeFactory.createURI(Unit_URI.METRE_URL));
        assertThrows(ExprEvalException.class, () -> function.exec(invalid, metre));
    }

    private org.apache.jena.graph.Node evaluate(String geometry, String unit) {
        return GeometryMeasurementFFTest.evaluate("geof:" + name + "(" + geometry + ", " + unit + ")");
    }
}
