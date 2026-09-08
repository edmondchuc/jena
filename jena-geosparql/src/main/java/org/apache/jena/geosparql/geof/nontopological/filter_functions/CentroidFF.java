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

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GMLDatatype;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;

/** Returns the planar XY centroid of a geometry. */
public class CentroidFF extends FunctionBase1 {

    @Override
    public NodeValue exec(NodeValue v) {

        try {
            GeometryWrapper geometry = GeometryWrapper.extract(v);

            GeometryWrapper centroid = geometry.centroid();
            // GML derives its coordinate dimension from the CRS. It cannot encode
            // this XY result in a three-dimensional CRS without inventing Z.
            if (GMLDatatype.URI.equals(centroid.getGeometryDatatypeURI())
                    && centroid.getSrsInfo().getCrs().getCoordinateSystem().getDimension() != 2) {
                throw new ExprEvalException("A two-dimensional centroid cannot be represented as GML in the source CRS.");
            }
            return centroid.asNodeValue();
        } catch (DatatypeFormatException ex) {
            throw new ExprEvalException(ex.getMessage(), ex);
        }
    }

}
