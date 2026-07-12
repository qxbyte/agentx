package com.agentx.common.util;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {
    @Test
    void versionIs7() {
        assertThat(UuidV7.next().version()).isEqualTo(7);
    }

    @Test
    void timeOrdered() throws InterruptedException {
        UUID a = UuidV7.next();
        Thread.sleep(2);
        UUID b = UuidV7.next();
        assertThat(a.toString().compareTo(b.toString())).isLessThan(0);
    }
}
