package com.applyflow.dto;

import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record ApiError(int status, String error, String message, Instant timestamp,
                           Map<String, String> fieldErrors) {
        public static ApiError of(int status, String error, String message) {
            return new ApiError(status, error, message, Instant.now(), null);
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        public static <T> PageResponse<T> of(Page<?> page, List<T> mapped) {
            return new PageResponse<>(mapped, page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record CountResponse(long count) {
    }

    public record SyncStartedResponse(int started) {
    }

    public record HealthResponse(String status, String database, String version) {
    }

    public record IdName(Long id, String name) {
    }

    public record IdEmail(Long id, String email) {
    }
}
