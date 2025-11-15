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
package org.apache.cassandra.cql3.selection;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.window.WindowDefinition;
import org.apache.cassandra.cql3.window.WindowFunctionProcessor;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.transport.ProtocolVersion;

/**
 * Selector for window functions.
 *
 * Handles evaluation of window functions like ROW_NUMBER, RANK, LAG, etc.
 */
public class WindowFunctionSelector extends Selector
{
    private final WindowFunctionProcessor.WindowFunction function;
    private final WindowDefinition windowDef;
    private final List<Selector> arguments;
    private final AbstractType<?> returnType;

    protected WindowFunctionSelector(WindowFunctionProcessor.WindowFunction function,
                                     WindowDefinition windowDef,
                                     List<Selector> arguments,
                                     AbstractType<?> returnType)
    {
        this.function = function;
        this.windowDef = windowDef;
        this.arguments = arguments;
        this.returnType = returnType;
    }

    @Override
    public void addInput(ProtocolVersion protocolVersion, ResultSetBuilder rs)
    {
        // Collect input for window function evaluation
        // This will be processed after all rows are collected
    }

    @Override
    public ByteBuffer getOutput(ProtocolVersion protocolVersion)
    {
        // Return the computed window function value
        // This would be populated after window processing
        return null;
    }

    @Override
    public AbstractType<?> getType()
    {
        return returnType;
    }

    @Override
    public void reset()
    {
        // Reset state for next partition
    }

    /**
     * Factory for creating window function selectors.
     */
    public static class Factory extends Selector.Factory
    {
        private final WindowFunctionProcessor.WindowFunction function;
        private final WindowDefinition windowDef;
        private final List<Selector.Factory> argumentFactories;
        private final AbstractType<?> returnType;

        public Factory(WindowFunctionProcessor.WindowFunction function,
                       WindowDefinition windowDef,
                       List<Selector.Factory> argumentFactories,
                       AbstractType<?> returnType)
        {
            this.function = function;
            this.windowDef = windowDef;
            this.argumentFactories = argumentFactories;
            this.returnType = returnType;
        }

        @Override
        protected String getColumnName()
        {
            return function.name() + "()";
        }

        @Override
        protected AbstractType<?> getReturnType()
        {
            return returnType;
        }

        @Override
        public Selector newInstance(QueryOptions options)
        {
            List<Selector> arguments = argumentFactories.stream()
                .map(f -> f.newInstance(options))
                .collect(java.util.stream.Collectors.toList());

            return new WindowFunctionSelector(function, windowDef, arguments, returnType);
        }

        @Override
        public boolean isAggregateSelectorFactory()
        {
            // Window functions are similar to aggregates but process differently
            return false;
        }

        @Override
        public boolean isWindowFunctionSelector()
        {
            return true;
        }
    }

    /**
     * Raw window function for parsing.
     */
    public static class Raw implements Selectable.Raw
    {
        private final String functionName;
        private final List<Selectable.Raw> arguments;
        private final RawWindowSpec windowSpec;

        public Raw(String functionName,
                   List<Selectable.Raw> arguments,
                   RawWindowSpec windowSpec)
        {
            this.functionName = functionName;
            this.arguments = arguments;
            this.windowSpec = windowSpec;
        }

        @Override
        public Selectable prepare(ColumnSpecification receiver)
        {
            throw new InvalidRequestException("Window functions not yet fully integrated");
        }

        @Override
        public TestResult testAssignment(String keyspace, ColumnSpecification receiver)
        {
            return TestResult.NOT_ASSIGNABLE;
        }

        public String getFunctionName()
        {
            return functionName;
        }

        public List<Selectable.Raw> getArguments()
        {
            return arguments;
        }

        public RawWindowSpec getWindowSpec()
        {
            return windowSpec;
        }
    }

    /**
     * Raw window specification from OVER clause.
     */
    public static class RawWindowSpec
    {
        private final List<Selectable.Raw> partitionBy;
        private final List<OrderingRaw> orderBy;
        private final RawWindowFrame frame;

        public RawWindowSpec(List<Selectable.Raw> partitionBy,
                             List<OrderingRaw> orderBy,
                             RawWindowFrame frame)
        {
            this.partitionBy = partitionBy;
            this.orderBy = orderBy;
            this.frame = frame;
        }

        public List<Selectable.Raw> getPartitionBy()
        {
            return partitionBy;
        }

        public List<OrderingRaw> getOrderBy()
        {
            return orderBy;
        }

        public RawWindowFrame getFrame()
        {
            return frame;
        }
    }

    /**
     * Raw ordering clause.
     */
    public static class OrderingRaw
    {
        private final Selectable.Raw column;
        private final boolean ascending;

        public OrderingRaw(Selectable.Raw column, boolean ascending)
        {
            this.column = column;
            this.ascending = ascending;
        }

        public Selectable.Raw getColumn()
        {
            return column;
        }

        public boolean isAscending()
        {
            return ascending;
        }
    }

    /**
     * Raw window frame specification.
     */
    public static class RawWindowFrame
    {
        private final String type; // "ROWS" or "RANGE"
        private final String startType;
        private final Integer startOffset;
        private final String endType;
        private final Integer endOffset;

        public RawWindowFrame(String type,
                              String startType,
                              Integer startOffset,
                              String endType,
                              Integer endOffset)
        {
            this.type = type;
            this.startType = startType;
            this.startOffset = startOffset;
            this.endType = endType;
            this.endOffset = endOffset;
        }

        public String getType()
        {
            return type;
        }

        public String getStartType()
        {
            return startType;
        }

        public Integer getStartOffset()
        {
            return startOffset;
        }

        public String getEndType()
        {
            return endType;
        }

        public Integer getEndOffset()
        {
            return endOffset;
        }
    }
}
