package com.dgcockpit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Assistant IA de rédaction documentaire.
 *
 * Deux modes contrôlés par ai.llm.auto-apply :
 *   false (défaut) → Mode SUGGESTION : l'IA produit une proposition qui sera postée
 *                    comme message système dans le fil d'instruction. L'utilisateur
 *                    applique lui-même dans Collabora. Aucun fichier MinIO modifié.
 *   true           → Mode AUTO-APPLY  : l'IA réécrit les paragraphes directement
 *                    dans le .docx (ré-injection non destructive via POI, préserve
 *                    styles, marges et signets Word).
 *
 * L'extraction du texte et la ré-injection utilisent Apache POI 5.3.0
 * (déjà présent dans le projet via DocxSignatureService).
 */
@Service
public class AiAssistService {

    @Value("${ai.llm.api-key:}")          private String apiKey;
    @Value("${ai.llm.endpoint:https://api.anthropic.com/v1/messages}") private String endpoint;
    @Value("${ai.llm.model:claude-haiku-4-5-20251001}") private String model;
    @Value("${ai.llm.enabled:true}")      private boolean enabled;
    @Value("${ai.llm.auto-apply:false}")  private boolean autoApply;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── Extraction ─────────────────────────────────────────────────────────

    /**
     * Extrait le texte des paragraphes non vides, indexés par leur position dans le
     * document. La clé (index) permet une ré-injection ciblée sans casser la structure.
     */
    public LinkedHashMap<Integer, String> extractParagraphs(byte[] docxBytes) throws Exception {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText();
                if (text != null && !text.isBlank()) {
                    map.put(i, text.trim());
                }
            }
        }
        return map;
    }

    /**
     * Retourne une représentation textuelle du document (tous paragraphes non vides,
     * séparés par des sauts de ligne) — utilisée pour le mode SUGGESTION.
     */
    public String extractFullText(byte[] docxBytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    // ── LLM ────────────────────────────────────────────────────────────────

    /**
     * Appelle Claude avec le texte complet du document. Retourne le texte amélioré
     * (utilisé pour le mode SUGGESTION).
     */
    public String callLlmFullText(String action, String fullText) {
        String systemPrompt = buildSystemPrompt(action);
        return callRaw(systemPrompt, fullText);
    }

    /**
     * Appelle Claude avec les paragraphes indexés (JSON). Retourne une map
     * {index → texte corrigé} pour le mode AUTO-APPLY.
     *
     * Le LLM reçoit : {"0": "...", "3": "..."} et DOIT renvoyer le même format.
     */
    public Map<Integer, String> callLlmIndexed(String action, Map<Integer, String> paragraphs)
            throws Exception {
        String systemPrompt = buildSystemPrompt(action) + """

            Tu reçois un objet JSON {"index": "texte"}. Renvoie UNIQUEMENT un objet JSON
            valide avec EXACTEMENT les mêmes clés entières, chaque valeur étant la version
            retravaillée du paragraphe. Ne fusionne, ne supprime, n'ajoute aucune clé.
            Réponds UNIQUEMENT avec le JSON, sans aucun texte avant ou après.
            """;
        String userJson = mapper.writeValueAsString(paragraphs);
        String raw = callRaw(systemPrompt, userJson);
        // Nettoyer les éventuels blocs markdown ```json ... ```
        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
        Map<String, String> stringMap = mapper.readValue(raw,
                new TypeReference<Map<String, String>>() {});
        Map<Integer, String> result = new LinkedHashMap<>();
        stringMap.forEach((k, v) -> {
            try { result.put(Integer.parseInt(k), v); } catch (NumberFormatException ignored) {}
        });
        return result;
    }

    // ── Application non destructive (Mode AUTO-APPLY) ───────────────────────

    /**
     * ⚠️ SÉCURISATION — ne jamais écraser le corps du document en bloc.
     * Stratégie : substitution du texte des Runs existants, paragraphe par paragraphe.
     * Préserve : styles (rPr/pPr), marges, polices, signets Word (BookmarkStart/End).
     *
     * Seul le premier Run de chaque paragraphe reçoit le nouveau texte ; les Runs
     * suivants sont vidés (leur contenu textuel est supprimé, leurs propriétés rPr
     * restent intactes pour conserver les sauts de mise en forme intentionnels).
     */
    public byte[] applyParagraphReplacements(byte[] original,
                                             Map<Integer, String> newTextByParagraph)
            throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(original));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (Map.Entry<Integer, String> entry : newTextByParagraph.entrySet()) {
                int idx = entry.getKey();
                if (idx < 0 || idx >= paragraphs.size()) continue;

                XWPFParagraph p = paragraphs.get(idx);
                List<XWPFRun> runs = p.getRuns();
                if (runs == null || runs.isEmpty()) continue;

                // Injecter le nouveau texte dans le premier Run, vider les suivants
                boolean first = true;
                for (XWPFRun run : runs) {
                    if (first) {
                        run.setText(entry.getValue(), 0);
                        // Supprimer les éventuels segments de texte supplémentaires (index > 0)
                        for (int i = run.getCTR().sizeOfTArray() - 1; i > 0; i--) {
                            run.getCTR().removeT(i);
                        }
                        first = false;
                    } else {
                        // Vider ce Run sans toucher à ses propriétés rPr
                        for (int i = run.getCTR().sizeOfTArray() - 1; i >= 0; i--) {
                            run.getCTR().removeT(i);
                        }
                    }
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public boolean isEnabled()    { return enabled && apiKey != null && !apiKey.isBlank(); }
    public boolean isAutoApply()  { return autoApply; }

    private String buildSystemPrompt(String action) {
        return switch (action) {
            case "IMPROVE_STYLE" -> """
                    Tu es un expert en rédaction administrative française pour l'administration djiboutienne.
                    Améliore le style du texte suivant en respectant les formules officielles (formules de politesse,
                    structure courrier administratif, ton formel). Retourne UNIQUEMENT le texte amélioré, sans commentaire.
                    """;
            case "CORRECT_GRAMMAR" -> """
                    Corrige uniquement les fautes de grammaire, d'orthographe et de syntaxe du texte suivant.
                    Ne modifie pas le style ni le fond du message. Retourne UNIQUEMENT le texte corrigé.
                    """;
            case "SUMMARIZE" -> """
                    Rédige un objet de courrier administratif (1 à 2 lignes maximum) résumant le texte suivant,
                    dans le style formel de l'administration djiboutienne.
                    Retourne UNIQUEMENT l'objet, sans phrase d'introduction ni commentaire.
                    """;
            default -> throw new IllegalArgumentException("Action IA inconnue : " + action);
        };
    }

    /** Appel HTTP bas niveau vers l'API Anthropic (Claude). */
    @SuppressWarnings("unchecked")
    private String callRaw(String systemPrompt, String userContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("system", systemPrompt.strip());
        body.put("messages", List.of(Map.of("role", "user", "content", userContent)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> response = rest.postForObject(endpoint, request, Map.class);

        if (response == null) throw new RuntimeException("Réponse LLM nulle");
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty())
            throw new RuntimeException("Réponse LLM vide");
        return String.valueOf(content.get(0).get("text"));
    }
}
