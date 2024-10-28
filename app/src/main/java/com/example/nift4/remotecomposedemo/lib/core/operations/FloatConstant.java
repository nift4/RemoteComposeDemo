/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.nift4.remotecomposedemo.lib.core.operations;

import com.example.nift4.remotecomposedemo.lib.core.CompanionOperation;
import com.example.nift4.remotecomposedemo.lib.core.Operation;
import com.example.nift4.remotecomposedemo.lib.core.Operations;
import com.example.nift4.remotecomposedemo.lib.core.RemoteContext;
import com.example.nift4.remotecomposedemo.lib.core.WireBuffer;

import java.util.List;

/**
 * Operation to deal with Text data
 */
public class FloatConstant implements Operation {
    public int mTextId;
    public float mValue;
    public static final Companion COMPANION = new Companion();
    public static final int MAX_STRING_SIZE = 4000;

    public FloatConstant(int textId, float value) {
        this.mTextId = textId;
        this.mValue = value;
    }

    @Override
    public void write(WireBuffer buffer) {
        COMPANION.apply(buffer, mTextId, mValue);
    }

    @Override
    public String toString() {
        return "FloatConstant[" + mTextId + "] = " + mValue + "";
    }

    public static class Companion implements CompanionOperation {
        private Companion() {}

        @Override
        public String name() {
            return "FloatExpression";
        }

        @Override
        public int id() {
            return Operations.DATA_FLOAT;
        }

        /**
         * Writes out the operation to the buffer
         * @param buffer
         * @param textId
         * @param value
         */
        public void apply(WireBuffer buffer, int textId, float value) {
            buffer.start(Operations.DATA_FLOAT);
            buffer.writeInt(textId);
            buffer.writeFloat(value);
        }

        @Override
        public void read(WireBuffer buffer, List<Operation> operations) {
            int textId = buffer.readInt();

            float value = buffer.readFloat();
            operations.add(new FloatConstant(textId, value));
        }
    }

    @Override
    public void apply(RemoteContext context) {
        context.loadFloat(mTextId, mValue);
    }

    @Override
    public String deepToString(String indent) {
        return indent + toString();
    }
}
