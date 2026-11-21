package io.bookmind.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpiVersionTest {

    @Test
    void compatibleWhenSameMajorAndMinorLessOrEqual() {
        assertThat(SpiVersion.isCompatibleWithHost("1.0", "1.0")).isTrue();
        assertThat(SpiVersion.isCompatibleWithHost("1.0", "1.2")).isTrue();
    }

    @Test
    void incompatibleWhenDifferentMajorOrNewerMinor() {
        assertThat(SpiVersion.isCompatibleWithHost("2.0", "1.0")).isFalse();
        assertThat(SpiVersion.isCompatibleWithHost("1.3", "1.1")).isFalse();
    }

    @Test
    void rejectsMalformedVersionString() {
        assertThatThrownBy(() -> SpiVersion.parse("v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("major.minor");
    }
}
