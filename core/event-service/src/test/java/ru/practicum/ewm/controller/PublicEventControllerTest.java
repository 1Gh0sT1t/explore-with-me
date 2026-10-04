package ru.practicum.ewm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.controller.publicapi.PublicEventController;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.service.EventService;
import ru.practicum.ewm.dto.internal.UserEventKey;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicEventController.class)
class PublicEventControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private EventService service;

    @Test
    void requiresUserHeaderForSingleEvent() throws Exception {
        mvc.perform(get("/events/1")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsRatingInsteadOfViews() throws Exception {
        when(service.getPublicEvent(1L, 2L)).thenReturn(EventFullDto.builder().id(1L).rating(0.8).build());
        mvc.perform(get("/events/1").header("X-EWM-USER-ID", 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(0.8)).andExpect(jsonPath("$.views").doesNotExist());
    }

    @Test
    void routesRecommendationsWithoutParsingItAsEventId() throws Exception {
        mvc.perform(get("/events/recommendations").header("X-EWM-USER-ID", 2)).andExpect(status().isOk());
        verify(service).getRecommendations(2L, 10);
    }

    @Test
    void acceptsLikeWithHeaderUserAndReturnsNoContent() throws Exception {
        mvc.perform(put("/events/1/like").header("X-EWM-USER-ID", 2)).andExpect(status().isNoContent());
        verify(service).likeEvent(new UserEventKey(2L, 1L));
    }

    @Test
    void rejectsInvalidRecommendationLimit() throws Exception {
        mvc.perform(get("/events/recommendations").header("X-EWM-USER-ID", 2).param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonNumericUserHeader() throws Exception {
        mvc.perform(get("/events/1").header("X-EWM-USER-ID", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
