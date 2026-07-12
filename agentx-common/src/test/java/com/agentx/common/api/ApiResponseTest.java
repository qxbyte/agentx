package com.agentx.common.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void okWrapsData() {
        ApiResponse<String> r = ApiResponse.ok("hi");
        assertThat(r.code()).isZero();
        assertThat(r.data()).isEqualTo("hi");
    }

    @Test
    void errorCarriesCode() {
        ApiResponse<Void> r = ApiResponse.error(ErrorCode.NOT_FOUND, "no such kb");
        assertThat(r.code()).isEqualTo(40400);
        assertThat(r.message()).isEqualTo("no such kb");
    }
}
