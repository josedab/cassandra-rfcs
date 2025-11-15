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

import org.apache.cassandra.tools.schemadesigner.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts access patterns from parsed queries.
 */
public class AccessPatternExtractor
{
    public AccessPattern extract(ParsedQuery query)
    {
        List<String> partitionKeys = new ArrayList<>();
        List<String> clusteringKeys = new ArrayList<>();
        List<String> regularColumns = new ArrayList<>();
        List<Predicate> predicates = new ArrayList<>();

        // Extract partition and clustering keys from WHERE conditions
        boolean foundNonEquality = false;
        for (WhereCondition condition : query.getWhereConditions())
        {
            if (condition.isEquality() && !foundNonEquality)
            {
                // Equality conditions on partition key
                partitionKeys.add(condition.getColumn());
            }
            else
            {
                // Range or other conditions suggest clustering columns
                if (!foundNonEquality && !condition.isEquality())
                {
                    foundNonEquality = true;
                }
                clusteringKeys.add(condition.getColumn());
            }

            // Convert to predicate
            Predicate.Operator op = parseOperator(condition.getOperator());
            predicates.add(new Predicate(condition.getColumn(), op, condition.getValue()));
        }

        // Extract regular columns from SELECT
        if (!query.getSelectColumns().contains("*"))
        {
            regularColumns.addAll(query.getSelectColumns());
        }

        // Parse ORDER BY
        OrderBy ordering = parseOrderBy(query.getOrderBy());

        return new AccessPattern(
            query.getTable(),
            partitionKeys,
            clusteringKeys,
            regularColumns,
            predicates,
            ordering,
            1, // Default frequency
            query.isAllowFiltering()
        );
    }

    private Predicate.Operator parseOperator(String op)
    {
        return switch (op.toUpperCase())
        {
            case "=" -> Predicate.Operator.EQ;
            case ">" -> Predicate.Operator.GT;
            case ">=" -> Predicate.Operator.GTE;
            case "<" -> Predicate.Operator.LT;
            case "<=" -> Predicate.Operator.LTE;
            case "IN" -> Predicate.Operator.IN;
            case "CONTAINS" -> Predicate.Operator.CONTAINS;
            default -> Predicate.Operator.EQ;
        };
    }

    private OrderBy parseOrderBy(String orderByClause)
    {
        if (orderByClause == null || orderByClause.trim().isEmpty())
        {
            return null;
        }

        List<String> columns = new ArrayList<>();
        OrderBy.Direction direction = OrderBy.Direction.ASC;

        String[] parts = orderByClause.split(",");
        for (String part : parts)
        {
            part = part.trim();
            if (part.toUpperCase().endsWith(" DESC"))
            {
                direction = OrderBy.Direction.DESC;
                part = part.substring(0, part.length() - 5).trim();
            }
            else if (part.toUpperCase().endsWith(" ASC"))
            {
                part = part.substring(0, part.length() - 4).trim();
            }
            columns.add(part);
        }

        return new OrderBy(columns, direction);
    }
}
