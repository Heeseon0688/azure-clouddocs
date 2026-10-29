package com.clouddocs.exception;

public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(String message) {
        super(message);
    }

    public static FileNotFoundException ofId(Long id) {
        return new FileNotFoundException("파일을 찾을 수 없습니다. id=" + id);
    }
}
