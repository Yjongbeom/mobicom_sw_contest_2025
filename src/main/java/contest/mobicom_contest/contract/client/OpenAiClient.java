package contest.mobicom_contest.contract.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import contest.mobicom_contest.contract.dto.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private final RestTemplate restTemplate;
    
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 모델 이름을 상수로 관리하여 변경 용이성 확보
    private static final String GPT_MODEL = "gpt-4o";

    public List<Issue> detectUnfairClauses(String text) {
        // REFACTORED: 시스템 프롬프트 개선
        String systemPrompt = """
            당신은 대한민국 근로기준법 전문가입니다.
            주어진 근로계약서 내용에서 법적으로 문제가 될 수 있는 조항을 찾아내 분석해야 합니다.
            분석 결과는 반드시 아래의 JSON 형식에 맞춰서, JSON 객체만 응답해야 합니다. 다른 설명은 절대 추가하지 마세요.
            
            {
              "issues": [
                {
                  "type": "퇴직금|최저임금|근로시간|부당해고|계약해지|기타",
                  "reason": "어떤 법률 조항(예: 근로기준법 제 O조)에 위배되는지 구체적인 이유 설명",
                  "evidence": "계약서 내용 중 문제가 되는 부분의 정확한 인용문"
                }
              ]
            }
            """;
        
        // REFACTORED: 공통 API 호출 메서드 사용
        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(mapper.createObjectNode().put("role", "user").put("content", text));

        Map<String, Object> additionalParams = Map.of("response_format", Map.of("type", "json_object"));
        String responseContent = executeGptRequest(messages, additionalParams);
        
        return extractIssues(responseContent);
    }

    public String translateText(String text, String targetLanguage) {
        // REFACTORED: 프롬프트 강화
        String prompt = String.format("""
            당신은 다국어 번역 전문가입니다. 다음 텍스트를 %s(으)로 최대한 자연스럽고 정확하게 번역해주세요.
            
            --- 원문 ---
            %s
            """, targetLanguage, text);

        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put("role", "user").put("content", prompt));
        
        return executeGptRequest(messages, null); // 추가 파라미터 없음
    }

    public String summarizeAndTranslate(String lawContent, String targetLanguage) {
        // REFACTORED: 가장 문제가 되었던 요약/번역 프롬프트 대폭 강화
        String prompt = String.format("""
            당신은 법률 문서 분석을 전문으로 하는 AI 어시스턴트입니다.
            당신의 임무는 주어진 법률 원문을 지정된 언어로 번역하고, 오직 그 내용에만 근거하여 간결한 요약문을 생성하는 것입니다.
            외부 지식을 사용하거나 원문에 없는 내용을 절대 창작해서는 안 됩니다.

            --- [지시사항] ---
            1. 아래 '원문'을 '번역 대상 언어'로 번역합니다.
            2. 번역된 결과물을 바탕으로 핵심 내용을 2-4문장으로 요약합니다.

            --- [입력 정보] ---
            - 번역 대상 언어: %s
            - 원문: %s

            --- [결과 요약] ---
            """, targetLanguage, lawContent);
        
        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put("role", "user").put("content", prompt));
        
        return executeGptRequest(messages, null); // 추가 파라미터 없음
    }

    /**
     * NEW & REFACTORED: OpenAI API 호출 로직을 중앙에서 관리하는 메서드
     */
    private String executeGptRequest(ArrayNode messages, Map<String, Object> additionalParams) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", GPT_MODEL); // NEW: 상수화된 최신 모델 사용
            requestBody.set("messages", messages);
            requestBody.put("temperature", 0.2); // REFACTORED: 좀 더 사실 기반 응답을 위해 0.2로 조정

            if (additionalParams != null) {
                additionalParams.forEach((key, value) -> requestBody.set(key, mapper.valueToTree(value)));
            }

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = mapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText();
            } else {
                log.error("OpenAI API call failed with status: {}", response.getStatusCode());
                throw new RuntimeException("GPT 처리 실패: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error during GPT processing", e);
            throw new RuntimeException("GPT 처리 중 예외 발생", e);
        }
    }

    private List<Issue> extractIssues(String jsonContent) {
        List<Issue> issues = new ArrayList<>();
        if (jsonContent == null || jsonContent.isEmpty()) {
            return issues;
        }

        try {
            String cleanedContent = cleanJsonContent(jsonContent);
            JsonNode issuesRoot = mapper.readTree(cleanedContent);
            JsonNode issuesNode = issuesRoot.path("issues");
            
            if (issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    issues.add(mapper.treeToValue(issueNode, Issue.class));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse issues JSON: {}", jsonContent, e);
        }
        return issues;
    }

    private String cleanJsonContent(String content) {
        content = content.trim();
        if (content.startsWith("```json")) {
            return content.substring(7, content.length() - 3).trim();
        }
        if (content.startsWith("```")) {
            return content.substring(3, content.length() - 3).trim();
        }
        return content;
    }
}
