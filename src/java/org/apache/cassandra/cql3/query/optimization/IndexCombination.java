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
package org.apache.cassandra.cql3.query.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a combination of indexes that can be used together
 * (either as intersection or union).
 */
public class IndexCombination
{
    private final List<String> indexes;
    private final CombinationType type;

    public IndexCombination(List<String> indexes, CombinationType type)
    {
        this.indexes = new ArrayList<>(indexes);
        this.type = type;
    }

    public static IndexCombination single(String index)
    {
        return new IndexCombination(Collections.singletonList(index), CombinationType.SINGLE);
    }

    public static IndexCombination intersection(List<String> indexes)
    {
        return new IndexCombination(indexes, CombinationType.INTERSECTION);
    }

    public static IndexCombination union(List<String> indexes)
    {
        return new IndexCombination(indexes, CombinationType.UNION);
    }

    public List<String> getIndexes()
    {
        return Collections.unmodifiableList(indexes);
    }

    public String getSingleIndex()
    {
        return isSingle() ? indexes.get(0) : null;
    }

    public CombinationType getType()
    {
        return type;
    }

    public boolean isSingle()
    {
        return type == CombinationType.SINGLE;
    }

    public boolean isIntersection()
    {
        return type == CombinationType.INTERSECTION;
    }

    public boolean isUnion()
    {
        return type == CombinationType.UNION;
    }

    public int size()
    {
        return indexes.size();
    }

    @Override
    public String toString()
    {
        return String.format("IndexCombination{type=%s, indexes=%s}", type, indexes);
    }

    public enum CombinationType
    {
        SINGLE,
        INTERSECTION,
        UNION
    }
}
