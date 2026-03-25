package com.pitabletbridge;

import org.junit.Assert;
import org.junit.Test;

public class GeometryHelperTest {
    @Test
    public void mapsTouchInsideLetterboxedFrame() {
        GeometryHelper.NormalizedPoint point = GeometryHelper.mapTouchToFrame(640.0f, 400.0f, 1280, 800, 640, 360);
        Assert.assertNotNull(point);
        Assert.assertEquals(0.5f, point.x, 0.01f);
        Assert.assertEquals(0.5f, point.y, 0.01f);
    }

    @Test
    public void rejectsTouchOutsideFrame() {
        GeometryHelper.NormalizedPoint point = GeometryHelper.mapTouchToFrame(10.0f, 10.0f, 1280, 800, 640, 360);
        Assert.assertNull(point);
    }
}

