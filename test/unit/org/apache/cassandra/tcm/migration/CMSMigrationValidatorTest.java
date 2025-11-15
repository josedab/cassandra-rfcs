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

package org.apache.cassandra.tcm.migration;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CMSMigrationValidator.
 */
public class CMSMigrationValidatorTest
{
    @Test
    public void testValidationCheckSuccess()
    {
        CMSMigrationValidator.ValidationCheck check =
            CMSMigrationValidator.ValidationCheck.success("test_check", "Test passed");

        assertTrue(check.passed);
        assertEquals("test_check", check.name);
        assertEquals("Test passed", check.message);
    }

    @Test
    public void testValidationCheckFailure()
    {
        CMSMigrationValidator.ValidationCheck check =
            CMSMigrationValidator.ValidationCheck.failure("test_check", "Test failed");

        assertFalse(check.passed);
        assertEquals("test_check", check.name);
        assertEquals("Test failed", check.message);
    }

    @Test
    public void testValidationResultAggregation()
    {
        CMSMigrationValidator.ValidationResult result = new CMSMigrationValidator.ValidationResult();

        result.addCheck("check1", CMSMigrationValidator.ValidationCheck.success("check1", "Pass"));
        result.addCheck("check2", CMSMigrationValidator.ValidationCheck.success("check2", "Pass"));

        assertEquals(2, result.getTotalChecks());
        assertEquals(2, result.getPassedChecks());
        assertEquals(0, result.getFailedChecks());
        assertTrue(result.isValid());
    }

    @Test
    public void testValidationResultWithFailures()
    {
        CMSMigrationValidator.ValidationResult result = new CMSMigrationValidator.ValidationResult();

        result.addCheck("check1", CMSMigrationValidator.ValidationCheck.success("check1", "Pass"));
        result.addCheck("check2", CMSMigrationValidator.ValidationCheck.failure("check2", "Fail"));

        assertEquals(2, result.getTotalChecks());
        assertEquals(1, result.getPassedChecks());
        assertEquals(1, result.getFailedChecks());
        assertFalse(result.isValid());
    }

    @Test
    public void testValidationResultToString()
    {
        CMSMigrationValidator.ValidationResult result = new CMSMigrationValidator.ValidationResult();

        result.addCheck("check1", CMSMigrationValidator.ValidationCheck.success("check1", "Pass"));

        String output = result.toString();
        assertNotNull(output);
        assertTrue(output.contains("1/1"));
        assertTrue(output.contains("check1"));
    }
}
