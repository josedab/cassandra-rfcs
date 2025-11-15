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

/**
 * Represents a query predicate (WHERE clause condition).
 */
public class Predicate
{
    public enum Operator
    {
        EQ, GT, GTE, LT, LTE, IN, CONTAINS
    }

    private final String column;
    private final Operator operator;
    private final Object value;

    public Predicate(String column, Operator operator, Object value)
    {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public String getColumn() { return column; }
    public Operator getOperator() { return operator; }
    public Object getValue() { return value; }

    public boolean isRangePredicate()
    {
        return operator == Operator.GT || operator == Operator.GTE ||
               operator == Operator.LT || operator == Operator.LTE;
    }

    @Override
    public String toString()
    {
        return String.format("%s %s %s", column, operator, value);
    }
}
