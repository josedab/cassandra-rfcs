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
package org.apache.cassandra.tools.schemadesigner.migration;

/**
 * Available migration strategies.
 */
public enum MigrationStrategy
{
    /**
     * Online migration using dual writes.
     * No downtime, but requires careful coordination.
     */
    ONLINE_MIGRATION,

    /**
     * Blue-green deployment.
     * Maintain two environments, switch between them.
     */
    BLUE_GREEN,

    /**
     * Rolling upgrade.
     * Gradual node-by-node migration.
     */
    ROLLING_UPGRADE,

    /**
     * Big bang migration.
     * Requires downtime but simpler.
     */
    BIG_BANG
}
