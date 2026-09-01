package com.ondo.wholesale.common.error;

/**
 * 리소스를 찾지 못했을 때(404). {@link ErrorCode#RESOURCE_NOT_FOUND} 고정.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
