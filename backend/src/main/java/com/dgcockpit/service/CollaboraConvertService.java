package com.dgcockpit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Conversion .docx → PDF via l'API REST de Collabora Online.
 * Endpoint : POST {collabora.internal-url}/cool/convert-to/pdf
 */
@Service
public class CollaboraConvertService {

    @Value("${collabora.internal-url:http://localhost:9980}")
    private String collaboraInternalUrl;

    private final RestTemplate rest = new RestTemplate();

    /**
     * Convertit un fichier .docx en PDF via Collabora.
     *
     * @param docxBytes  contenu binaire du .docx
     * @param filename   nom du fichier (ex. "note_service.docx")
     * @return bytes du PDF résultant
     */
    public byte[] docxToPdf(byte[] docxBytes, String filename) {
        String url = collaboraInternalUrl + "/cool/convert-to/pdf";

        // ByteArrayResource avec getFilename() pour que Collabora reçoive le nom
        // du fichier dans Content-Disposition — indispensable pour qu'il détecte
        // le format .docx et sélectionne le bon filtre de conversion.
        final String fname = filename;
        ByteArrayResource fileResource = new ByteArrayResource(docxBytes) {
            @Override public String getFilename() { return fname; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        body.add("data", new HttpEntity<>(fileResource, fileHeaders));

        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, reqHeaders);

        ResponseEntity<byte[]> response = rest.exchange(url, HttpMethod.POST, request, byte[].class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Collabora conversion échouée : " + response.getStatusCode());
        }
        return response.getBody();
    }
}
