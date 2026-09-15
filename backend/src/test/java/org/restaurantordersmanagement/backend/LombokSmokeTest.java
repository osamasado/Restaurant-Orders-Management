package org.restaurantordersmanagement.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.Data;
import org.junit.jupiter.api.Test;

/**
 * Confirms Lombok's annotation processing is actually wired into the build
 * (compiler plugin annotationProcessorPaths), independent of any domain code.
 */
class LombokSmokeTest {

    @Data
    private static class SamplePoint {
        private final int x;
        private final int y;
    }

    @Test
    void lombokGeneratesConstructorGettersEqualsAndHashCode() {
        SamplePoint a = new SamplePoint(1, 2);
        SamplePoint b = new SamplePoint(1, 2);
        SamplePoint c = new SamplePoint(3, 4);

        assertEquals(1, a.getX());
        assertEquals(2, a.getY());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(a.toString().contains("x=1"));
        assertTrue(a.toString().contains("y=2"));
    }

}
