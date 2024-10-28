package com.example.nift4.remotecomposedemo.lib.core.operations;

import com.example.nift4.remotecomposedemo.lib.core.Operation;
import com.example.nift4.remotecomposedemo.lib.core.Operations;
import com.example.nift4.remotecomposedemo.lib.core.PaintContext;

public class DrawCircle extends DrawBase3 {
    public static final Companion COMPANION =
            new Companion(Operations.DRAW_CIRCLE) {
                @Override
                public Operation construct(float x1,
                                           float y1,
                                           float x2
                ) {
                    return new DrawCircle(x1, y1, x2);
                }
            };

    public DrawCircle(
            float left,
            float top,
            float right) {
        super(left, top, right);
        mName = "DrawCircle";
    }

    @Override
    public void paint(PaintContext context) {
        context.drawCircle(mV1, mV2, mV3);
    }
}
