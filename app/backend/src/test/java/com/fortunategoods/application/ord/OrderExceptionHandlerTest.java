package com.fortunategoods.application.ord;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class OrderExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ExampleController()).setControllerAdvice(new OrderExceptionHandler()).build();
    }

    @Test
    void successResponseContainsMessage() throws Exception {
        mvc.perform(get("/test/success")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("accepted"))
        .andExpect(jsonPath("$.message").value("Order accepted."))
        .andExpect(jsonPath("$.msg").doesNotExist());
    }

    @Test
    void conflict409() throws Exception {
        mvc.perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Order is already accepted."))
        .andExpect(jsonPath("$.msg").doesNotExist());
    }

    @Test
    void notFound404() throws Exception {
        mvc.perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Order was not found."));
    }

    @Test
    void invalid400() throws Exception {
        mvc.perform(get("/test/orders/abc")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Request is invalid"));
    }

    @Test
    void invalidJson400() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Request is invalid"));
    }

    @Test
    void missingParams400() throws Exception {
        mvc.perform(get("/test/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Request is invalid"));
    }

    // These endpoints simulate what the controllers handling request would look like
    @RestController
    static class ExampleController {

        @GetMapping("/test/success")
        public CommandSuccess<String> success() {
            return CommandSuccess.success("accepted", "Order accepted.");
        }

        @GetMapping("/test/conflict")
        public String conflict() {
            throw new CommandExecutionException(409, "CONFLICT", "Order is already accepted.", null);
        }

        @GetMapping("/test/not-found")
        public String notFound() {
            throw new CommandExecutionException(404, "NOT_FOUND", "Order was not found.", null);
        }

        @GetMapping("/test/orders/{id}")
        public String order(@PathVariable("id") int id) {
            return "Order " + id;
        }

        @PostMapping("/test/body")
        public String readBody(@RequestBody Map<String, Object> body) {
            return "Received";
        }

        @GetMapping("/test/search")
        public String search(@RequestParam("name") String name) {
            return name;
        }

    }
}