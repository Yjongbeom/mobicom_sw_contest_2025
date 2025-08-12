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

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Issue> detectUnfairClauses(String text) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectNode message1 = objectMapper.createObjectNode();
        message1.put("role", "system");
        message1.put("content", """
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
            """);

        ObjectNode message2 = objectMapper.createObjectNode();
        message2.put("role", "user");
        message2.put("content", text);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-4o");
        requestBody.set("messages", objectMapper.createArrayNode().add(message1).add(message2));
        requestBody.put("temperature", 0.2);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.exchange(openaiApiUrl, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode jsonNode = parseJson(response.getBody());
            return extractIssues(jsonNode);
        } else {
            throw new Exception("OpenAI 요청 실패: " + response.getStatusCode());
        }
    }

    public String translateText(String text, String targetLanguage) {
        String prompt = String.format("""
            당신은 다국어 번역 전문가입니다. 다음 텍스트를 %s(으)로 최대한 자연스럽고 정확하게 번역해주세요. 다른 설명 없이 번역 결과만 응답해주세요.
            
            --- 원문 ---
            %s
            """, targetLanguage, text);
        return getGptResponse(prompt);
    }

    public String summarizeAndTranslate(String text, String targetLanguage) {
        String prompt = String.format("""
            당신은 법률 문서를 일반인이 이해하기 쉽게 설명하는 전문가입니다.
            당신의 임무는 아래 '법률 원문'의 핵심 내용을 먼저 한국어로 2-4문장으로 요약한 뒤, 그 요약문을 오직 '%s' 언어로만 번역하여 최종 결과를 제공하는 것입니다.
            최종 응답에는 번역된 요약문만 포함해야 하며, 다른 언어나 설명은 절대 추가하지 마세요.
            
            --- 법률 원문 ---
            %s
            """, targetLanguage, text);
        return getGptResponse(prompt);
    }

    private String getGptResponse(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o");

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);

            requestBody.set("messages", messages);
            requestBody.put("temperature", 0.2);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("GPT 처리 중 예외 발생", e);
            throw new RuntimeException("GPT 처리 실패", e);
        }
    }

    private JsonNode parseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("JSON 파싱 오류", e);
        }
    }

    private List<Issue> extractIssues(JsonNode root) {
        List<Issue> issues = new ArrayList<>();
        try {
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return issues;
            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isEmpty()) return issues;
            content = cleanJsonContent(content);
            JsonNode issuesRoot = objectMapper.readTree(content);
            JsonNode issuesNode = issuesRoot.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    issues.add(objectMapper.treeToValue(issueNode, Issue.class));
                }
            }
        } catch (Exception e) {
            log.error("이슈 추출 중 JSON 파싱 실패", e);
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
