/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.tools.schemadesigner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents ORDER BY clause in a query.
 */
public class OrderBy
{
    public enum Direction
    {
        ASC, DESC
    }

    private final List<String> columns;
    private final Direction direction;

    public OrderBy(List<String> columns, Direction direction)
    {
        this.columns = new ArrayList<>(columns);
        this.direction = direction;
    }

    public List<String> getColumns() { return new ArrayList<>(columns); }
    public Direction getDirection() { return direction; }

    @Override
    public String toString()
    {
        return String.format("%s %s", String.join(", ", columns), direction);
    }
}
