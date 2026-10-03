package com.zeshanaslam.actionhealth.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void aHigherVersionIsNewer() {
        assertTrue(UpdateChecker.isNewer("3.9.0", "3.9.1"));
        assertTrue(UpdateChecker.isNewer("3.9.0", "4.0"));
        assertTrue(UpdateChecker.isNewer("3.9", "3.9.1"));
    }

    @Test
    void numbersAreComparedAsNumbersNotText() {
        assertTrue(UpdateChecker.isNewer("3.9.2", "3.10.0"));
        assertFalse(UpdateChecker.isNewer("3.10.0", "3.9.2"));
    }

    @Test
    void theSameOrALowerVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("3.9.0", "3.9.0"));
        assertFalse(UpdateChecker.isNewer("3.9.0", "3.9"));
        assertFalse(UpdateChecker.isNewer("3.9.0", "3.8.1"));
    }

    @Test
    void anAnswerThatIsNotAVersionIsIgnored() {
        assertFalse(UpdateChecker.isNewer("3.9.0", ""));
        assertFalse(UpdateChecker.isNewer("3.9.0", "<html>error</html>"));
        assertFalse(UpdateChecker.isNewer("3.9.0", "4.0-SNAPSHOT"));
        assertFalse(UpdateChecker.isNewer("3.9.0", "99999999999.0"));
        assertFalse(UpdateChecker.isNewer("dev", "3.9.1"));
    }
}
