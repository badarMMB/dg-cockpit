package com.dgcockpit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/type-documents"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutPermissionCannotCreateTypeDocument() throws Exception {
        String token = login("agent.douane", "agent1234");

        mockMvc.perform(post("/api/type-documents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SEC_FORBIDDEN",
                      "libelle": "Interdit",
                      "modeCircuit": "LIBRE",
                      "actionFinale": "ARCHIVER"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void userWithPermissionCanCreateTypeDocument() throws Exception {
        String token = login("dg", "dg1234");

        mockMvc.perform(post("/api/type-documents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SEC_ALLOWED",
                      "libelle": "Autorise",
                      "modeCircuit": "LIBRE",
                      "actionFinale": "ARCHIVER"
                    }
                    """))
            .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }
}
