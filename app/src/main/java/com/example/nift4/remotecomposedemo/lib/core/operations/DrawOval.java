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

public class DrawOval extends DrawBase4 {
    public static final Companion COMPANION =
            new Companion(Operations.DRAW_OVAL) {
                @Override
                public Operation construct(float x1,
                                           float y1,
                                           float x2,
                                           float y2) {
                    return new DrawOval(x1, y1, x2, y2);
                }
            };

    public DrawOval(
            float left,
            float top,
            float right,
            float bottom) {
        super(left, top, right, bottom);
        mName = "DrawOval";
    }

    @Override
    public void paint(PaintContext context) {
        context.drawOval(mX1, mY1, mX2, mY2);
    }
}
