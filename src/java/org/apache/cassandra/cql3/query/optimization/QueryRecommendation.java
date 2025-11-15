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

/**
 * Recommendation to modify a query for better performance.
 */
public class QueryRecommendation extends Recommendation
{
    private final String title;
    private final String suggestedQuery;

    public QueryRecommendation(String title, String description, Priority priority)
    {
        this(title, description, priority, null, 0.5);
    }

    public QueryRecommendation(String title,
                              String description,
                              Priority priority,
                              String suggestedQuery,
                              double estimatedImprovement)
    {
        super("MODIFY_QUERY",
              priority,
              description,
              estimatedImprovement,
              0.7); // Default confidence

        this.title = title;
        this.suggestedQuery = suggestedQuery;
    }

    @Override
    public String getSuggestedAction()
    {
        if (suggestedQuery != null)
        {
            return suggestedQuery;
        }
        return getDescription();
    }

    public String getTitle()
    {
        return title;
    }
}
