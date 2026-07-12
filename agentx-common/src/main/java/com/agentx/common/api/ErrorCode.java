package com.agentx.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    OK(0, 200),
    BAD_REQUEST(40000, 400),
    UNAUTHORIZED(40100, 401),
    FORBIDDEN(40300, 403),
    NOT_FOUND(40400, 404),
    CONFLICT(40900, 409),
    INTERNAL_ERROR(50000, 500);

    private final int code;
    private final int httpStatus;
}
