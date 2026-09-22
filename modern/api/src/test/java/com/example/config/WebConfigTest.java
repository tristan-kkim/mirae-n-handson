package com.example.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.item.UnitController;
import com.example.item.UnitService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** CORS — 프론트엔드 개발 서버 출처의 GET 만 허용. */
@WebMvcTest(UnitController.class)
@ActiveProfiles("test")
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitService unitService;

    @Test
    @DisplayName("5173 출처의 GET 은 Access-Control-Allow-Origin 을 받는다")
    void allowsWebDevOriginForGet() throws Exception {
        Mockito.when(unitService.listUnits()).thenReturn(List.of());

        mockMvc.perform(get("/api/units").header(HttpHeaders.ORIGIN, WebConfig.WEB_DEV_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WebConfig.WEB_DEV_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("다른 출처의 preflight 는 거부된다(403)")
    void rejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/units")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("허용 출처라도 POST preflight 는 거부된다(403)")
    void rejectsNonGetMethods() throws Exception {
        mockMvc.perform(options("/api/units")
                .header(HttpHeaders.ORIGIN, WebConfig.WEB_DEV_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }
}
