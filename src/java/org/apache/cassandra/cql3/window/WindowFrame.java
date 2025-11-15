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
package org.apache.cassandra.cql3.window;

/**
 * Defines the frame specification for window functions.
 *
 * A frame specifies which rows within the partition are included in the window
 * for the current row.
 */
public class WindowFrame
{
    private final FrameType type;
    private final FrameBound start;
    private final FrameBound end;

    public WindowFrame(FrameType type, FrameBound start, FrameBound end)
    {
        this.type = type;
        this.start = start;
        this.end = end;
    }

    /**
     * Create a default window frame (RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW).
     *
     * @return the default window frame
     */
    public static WindowFrame defaultFrame()
    {
        return new WindowFrame(
            FrameType.RANGE,
            new FrameBound(BoundType.UNBOUNDED_PRECEDING, 0),
            new FrameBound(BoundType.CURRENT_ROW, 0)
        );
    }

    public FrameType getType()
    {
        return type;
    }

    public FrameBound getStart()
    {
        return start;
    }

    public FrameBound getEnd()
    {
        return end;
    }

    @Override
    public String toString()
    {
        return String.format("%s BETWEEN %s AND %s", type, start, end);
    }

    /**
     * Type of window frame.
     */
    public enum FrameType
    {
        /** Physical rows - counts actual rows */
        ROWS,

        /** Logical range - based on value ranges */
        RANGE
    }

    /**
     * Represents a frame boundary.
     */
    public static class FrameBound
    {
        private final BoundType type;
        private final int offset;

        public FrameBound(BoundType type, int offset)
        {
            this.type = type;
            this.offset = offset;
        }

        public BoundType getType()
        {
            return type;
        }

        public int getOffset()
        {
            return offset;
        }

        @Override
        public String toString()
        {
            switch (type)
            {
                case UNBOUNDED_PRECEDING:
                    return "UNBOUNDED PRECEDING";
                case UNBOUNDED_FOLLOWING:
                    return "UNBOUNDED FOLLOWING";
                case CURRENT_ROW:
                    return "CURRENT ROW";
                case PRECEDING:
                    return offset + " PRECEDING";
                case FOLLOWING:
                    return offset + " FOLLOWING";
                default:
                    return type.toString();
            }
        }
    }

    /**
     * Type of frame boundary.
     */
    public enum BoundType
    {
        /** All rows before the current row in the partition */
        UNBOUNDED_PRECEDING,

        /** All rows after the current row in the partition */
        UNBOUNDED_FOLLOWING,

        /** The current row */
        CURRENT_ROW,

        /** N rows before the current row */
        PRECEDING,

        /** N rows after the current row */
        FOLLOWING
    }
}
