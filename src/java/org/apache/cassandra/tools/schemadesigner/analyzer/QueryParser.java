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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CQL queries into structured representation.
 */
public class QueryParser
{
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?(?:\\s+ORDER\\s+BY\\s+(.+?))?(?:\\s+ALLOW\\s+FILTERING)?\\s*;?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern WHERE_CLAUSE_PATTERN = Pattern.compile(
        "(\\w+)\\s*([=<>]+|IN|CONTAINS)\\s*([^\\s,)]+)",
        Pattern.CASE_INSENSITIVE
    );

    public ParsedQuery parse(String query)
    {
        query = query.trim();

        Matcher matcher = SELECT_PATTERN.matcher(query);
        if (!matcher.find())
        {
            throw new IllegalArgumentException("Invalid SELECT query: " + query);
        }

        String selectClause = matcher.group(1);
        String table = matcher.group(2);
        String whereClause = matcher.group(3);
        String orderByClause = matcher.group(4);
        boolean allowFiltering = query.toUpperCase().contains("ALLOW FILTERING");

        ParsedQuery parsed = new ParsedQuery();
        parsed.setTable(table);
        parsed.setSelectColumns(parseSelectColumns(selectClause));
        parsed.setWhereConditions(parseWhereClause(whereClause));
        parsed.setOrderBy(orderByClause);
        parsed.setAllowFiltering(allowFiltering);

        return parsed;
    }

    private List<String> parseSelectColumns(String selectClause)
    {
        List<String> columns = new ArrayList<>();
        if (selectClause.trim().equals("*"))
        {
            columns.add("*");
        }
        else
        {
            String[] parts = selectClause.split(",");
            for (String part : parts)
            {
                columns.add(part.trim());
            }
        }
        return columns;
    }

    private List<WhereCondition> parseWhereClause(String whereClause)
    {
        List<WhereCondition> conditions = new ArrayList<>();

        if (whereClause == null || whereClause.trim().isEmpty())
        {
            return conditions;
        }

        // Split by AND (simple parser, doesn't handle OR)
        String[] parts = whereClause.split("\\s+AND\\s+", -1);

        for (String part : parts)
        {
            Matcher matcher = WHERE_CLAUSE_PATTERN.matcher(part.trim());
            if (matcher.find())
            {
                String column = matcher.group(1);
                String operator = matcher.group(2);
                String value = matcher.group(3);
                conditions.add(new WhereCondition(column, operator, value));
            }
        }

        return conditions;
    }
}
