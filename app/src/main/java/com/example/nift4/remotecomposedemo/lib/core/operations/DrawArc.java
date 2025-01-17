/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.example.nift4.remotecomposedemo.lib.core.Operation;
import com.example.nift4.remotecomposedemo.lib.core.Operations;
import com.example.nift4.remotecomposedemo.lib.core.PaintContext;

public class DrawArc extends DrawBase6 {
    public static final Companion COMPANION =
            new Companion(Operations.DRAW_ARC) {
                @Override
                public Operation construct(float v1,
                                           float v2,
                                           float v3,
                                           float v4,
                                           float v5,
                                           float v6) {
                    return new DrawArc(v1, v2, v3, v4, v5, v6);
                }
            };

    public DrawArc(float v1,
                   float v2,
                   float v3,
                   float v4,
                   float v5,
                   float v6) {
        super(v1, v2, v3, v4, v5, v6);
        mName = "DrawArc";
    }

    @Override
    public void paint(PaintContext context) {
        context.drawArc(mV1, mV2, mV3, mV4, mV5, mV6);
    }
}
