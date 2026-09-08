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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;

public class GeometryMemberTest {
    @Test
    public void atomicIndexOneReturnsTheOriginalWrapper() {
        GeometryWrapper point = geometry("POINT Z EMPTY");
        assertSame(point, point.getGeometryN(1));
        assertThrows(IllegalArgumentException.class, () -> point.getGeometryN(0));
        assertThrows(IllegalArgumentException.class, () -> point.getGeometryN(2));
    }

    @Test
    public void nestedCollectionsAreSelectedWithoutFlattening() {
        GeometryWrapper nested = collection(CoordinateSequenceDimensions.XY,
            geometry("POINT (1 2)").getParsingGeometry(), geometry("POINT (3 4)").getParsingGeometry());
        GeometryWrapper source = collection(CoordinateSequenceDimensions.XY,
            geometry("POINT (9 9)").getParsingGeometry(), nested.getParsingGeometry());
        GeometryWrapper selected = source.getGeometryN(2);
        assertEquals("GeometryCollection", selected.getGeometryType());
        assertEquals(2, selected.getParsingGeometry().getNumGeometries());
        assertEquals(3, selected.getGeometryN(2).getXYGeometry().getCoordinate().getX(), 0);
        GeometryWrapper reparsed = GeometryWrapper.extract(selected.asNodeValue());
        assertEquals(2, reparsed.getParsingGeometry().getNumGeometries());
        assertEquals(3, reparsed.getGeometryN(2).getXYGeometry().getCoordinate().getX(), 0);
    }

    @Test
    public void allEmptyCollectionMembersSurviveSerialization() {
        GeometryWrapper nested = collection(CoordinateSequenceDimensions.XYZ,
            geometry("POINT Z EMPTY").getParsingGeometry());
        GeometryWrapper source = collection(CoordinateSequenceDimensions.XYZ, nested.getParsingGeometry());
        GeometryWrapper selected = GeometryWrapper.extract(source.getGeometryN(1).asNodeValue());
        assertEquals(1, selected.getParsingGeometry().getNumGeometries());
        assertEquals(CoordinateSequenceDimensions.XYZ, selected.getGeometryN(1).getCoordinateSequenceDimensions());
    }

    @Test
    public void selectedMixedCollectionRetainsEachMemberLayout() {
        GeometryWrapper nested = collection(CoordinateSequenceDimensions.XY,
            geometry("POINT Z (1 2 3)").getParsingGeometry(), geometry("POINT M (4 5 6)").getParsingGeometry());
        GeometryWrapper source = collection(CoordinateSequenceDimensions.XY, nested.getParsingGeometry());
        GeometryWrapper selected = GeometryWrapper.extract(source.getGeometryN(1).asNodeValue());
        assertEquals(CoordinateSequenceDimensions.XYZ, selected.getGeometryN(1).getCoordinateSequenceDimensions());
        assertEquals(CoordinateSequenceDimensions.XYM, selected.getGeometryN(2).getCoordinateSequenceDimensions());
    }

    @Test
    public void memberMetadataComesFromTheSelectedCoordinateSequence() {
        GeometryWrapper source = geometry("GEOMETRYCOLLECTION (POINT Z (1 2 3), LINESTRING M (1 2 7, 3 4 9))");
        GeometryWrapper point = source.getGeometryN(1);
        GeometryWrapper line = source.getGeometryN(2);
        assertEquals(CoordinateSequenceDimensions.XYZ, point.getCoordinateSequenceDimensions());
        assertEquals(CoordinateSequenceDimensions.XYM, line.getCoordinateSequenceDimensions());
        assertEquals(0, point.getTopologicalDimension());
        assertEquals(1, line.getTopologicalDimension());
    }

    @Test
    public void emptyCollectionsRejectEveryIndex() {
        GeometryWrapper empty = geometry("GEOMETRYCOLLECTION EMPTY");
        assertThrows(IllegalArgumentException.class, () -> empty.getGeometryN(1));
        assertThrows(IllegalArgumentException.class, () -> empty.getGeometryN(-1));
        assertThrows(IllegalArgumentException.class, () -> empty.getGeometryN(Integer.MAX_VALUE));
    }

    private static GeometryWrapper geometry(String wkt) {
        return GeometryWrapper.extract(wkt, WKTDatatype.URI);
    }

    private static GeometryWrapper collection(CoordinateSequenceDimensions dimensions, Geometry... members) {
        Geometry collection = CustomGeometryFactory.theInstance().createGeometryCollection(members);
        return new GeometryWrapper(collection, SRS_URI.DEFAULT_WKT_CRS84, WKTDatatype.URI,
                                   new DimensionInfo(dimensions, collection.getDimension()));
    }
}
