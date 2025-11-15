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

import java.util.Objects;

/**
 * Represents a table column.
 */
public class Column
{
    private final String name;
    private final String type;
    private boolean isStatic;

    public Column(String name, String type)
    {
        this.name = name;
        this.type = type;
        this.isStatic = false;
    }

    public Column(String name, String type, boolean isStatic)
    {
        this.name = name;
        this.type = type;
        this.isStatic = isStatic;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isStatic() { return isStatic; }

    public boolean isCollection()
    {
        String upperType = type.toUpperCase();
        return upperType.startsWith("LIST<") ||
               upperType.startsWith("SET<") ||
               upperType.startsWith("MAP<");
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Column column = (Column) o;
        return Objects.equals(name, column.name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name);
    }

    @Override
    public String toString()
    {
        return name + " " + type + (isStatic ? " STATIC" : "");
    }
}
