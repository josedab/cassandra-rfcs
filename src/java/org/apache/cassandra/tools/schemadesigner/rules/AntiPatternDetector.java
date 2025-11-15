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
package org.apache.cassandra.tools.schemadesigner.rules;

import org.apache.cassandra.tools.schemadesigner.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects common anti-patterns in Cassandra schemas.
 */
public class AntiPatternDetector
{
    private final List<AntiPatternRule> rules;

    public AntiPatternDetector()
    {
        this.rules = new ArrayList<>();
        initializeRules();
    }

    private void initializeRules()
    {
        rules.add(AntiPatternRule.LARGE_PARTITION);
        rules.add(AntiPatternRule.HOT_PARTITION);
        rules.add(AntiPatternRule.UNBOUNDED_COLLECTION);
        rules.add(AntiPatternRule.ALLOW_FILTERING_OVERUSE);
    }

    /**
     * Detects anti-patterns in a schema.
     *
     * @param schema Schema to analyze
     * @param accessPatterns Access patterns
     * @return List of detected anti-patterns
     */
    public List<AntiPattern> detect(Schema schema, List<AccessPattern> accessPatterns)
    {
        List<AntiPattern> detected = new ArrayList<>();

        for (AntiPatternRule rule : rules)
        {
            if (rule.matches(schema, accessPatterns))
            {
                detected.add(rule.createAntiPattern());
            }
        }

        return detected;
    }
}
