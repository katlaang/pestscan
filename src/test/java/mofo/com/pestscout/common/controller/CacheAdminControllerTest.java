package mofo.com.pestscout.common.controller;

import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CacheAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class CacheAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheService cacheService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void returnsCacheStats() throws Exception {
        CacheService.CacheStats stats = new CacheService.CacheStats(3, List.of("a", "b", "c"));
        when(cacheService.getCacheStats()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaches", is(3)));
    }

    @Test
    void clearsFarmCaches() throws Exception {
        UUID farmId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/cache/clear-farm/" + farmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cachesCleared", is(6)));
    }

    @Test
    void checksCacheKeyPresence() throws Exception {
        when(cacheService.isCached("analytics", "foo")).thenReturn(true);

        mockMvc.perform(get("/api/admin/cache/check")
                        .param("cacheName", "analytics")
                        .param("key", "foo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }
}
