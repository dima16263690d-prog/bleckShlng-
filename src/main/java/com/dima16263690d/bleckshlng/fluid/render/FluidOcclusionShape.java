package com.dima16263690d.bleckshlng.fluid.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Face occlusion geometry for fluid rendering.
 *
 * This is not a collision shape. It describes only the area of a particular
 * face that hides a fluid surface, matching the separate occlusion-shape
 * contract used by modern LiquidBlockRenderer.
 */
public final class FluidOcclusionShape {
    public static final FluidOcclusionShape EMPTY =
            new FluidOcclusionShape(Collections.<FluidOcclusionRect>emptyList());
    public static final FluidOcclusionShape FULL =
            new FluidOcclusionShape(Collections.singletonList(
                    new FluidOcclusionRect(0.0D, 1.0D, 0.0D, 1.0D)));

    private final List<FluidOcclusionRect> rectangles;

    public FluidOcclusionShape(List<FluidOcclusionRect> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) {
            this.rectangles = Collections.emptyList();
        } else {
            this.rectangles = Collections.unmodifiableList(
                    new ArrayList<FluidOcclusionRect>(rectangles));
        }
    }

    public static FluidOcclusionShape of(FluidOcclusionRect rectangle) {
        return rectangle == null ? EMPTY
                : new FluidOcclusionShape(Collections.singletonList(rectangle));
    }

    public List<FluidOcclusionRect> getRectangles() {
        return rectangles;
    }

    public boolean isEmpty() {
        return rectangles.isEmpty();
    }
}
