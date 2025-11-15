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
package org.apache.cassandra.tools.schemadesigner.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed CQL query.
 */
public class ParsedQuery
{
    private String table;
    private List<String> selectColumns;
    private List<WhereCondition> whereConditions;
    private String orderBy;
    private boolean allowFiltering;

    public ParsedQuery()
    {
        this.selectColumns = new ArrayList<>();
        this.whereConditions = new ArrayList<>();
    }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public List<String> getSelectColumns() { return selectColumns; }
    public void setSelectColumns(List<String> columns) { this.selectColumns = columns; }

    public List<WhereCondition> getWhereConditions() { return whereConditions; }
    public void setWhereConditions(List<WhereCondition> conditions) { this.whereConditions = conditions; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public boolean isAllowFiltering() { return allowFiltering; }
    public void setAllowFiltering(boolean allowFiltering) { this.allowFiltering = allowFiltering; }
}
