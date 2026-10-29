package com.clouddocs.exception;

/** Azure Blob Storage 호출 실패를 도메인 예외로 감싼다. SDK 예외가 상위 계층으로 새지 않도록 한다. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
