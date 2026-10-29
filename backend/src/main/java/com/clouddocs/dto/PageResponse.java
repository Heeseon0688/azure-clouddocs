package com.clouddocs.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Spring Data Page 객체를 그대로 노출하지 않고 API 계약을 고정하기 위한 응답 DTO. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
