package mofo.com.pestscout.common.dto;

import java.util.List;

// common DTO for paging
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
