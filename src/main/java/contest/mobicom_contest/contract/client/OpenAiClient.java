package contest.mobicom_contest.contract.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import contest.mobicom_contest.contract.dto.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private final RestTemplate restTemplate;

    public List<Issue> detectUnfairClauses(String text) throws Exception {
        String url = openaiApiUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode message1 = mapper.createObjectNode();
        message1.put("role", "system");
        message1.put("content", """
        다음 근로계약서 문장에서 법적 문제가 있는 조항을 분류하세요. 반드시 아래 JSON 형식으로만 응답:
        
        {
          "issues": [
            {
              "type": "퇴직금|최저임금|근로시간|부당해고|계약해지|기타",
              "reason": "구체적인 법률 조항 포함 설명",
              "evidence": "계약서 문장 중 정확한 인용문"
            }
          ]
        }
        
        응답은 JSON만 포함해야 하며 다른 텍스트는 금지됨""");

        ObjectNode message2 = mapper.createObjectNode();
        message2.put("role", "user");
        message2.put("content", text);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.set("messages", mapper.createArrayNode().add(message1).add(message2));
        requestBody.put("temperature", 0.3);

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        String requestJson = mapper.writeValueAsString(requestBody);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode jsonNode = parseJson(response.getBody());
            return extractIssues(jsonNode);
        } else {
            throw new Exception("OpenAI 요청 실패");
        }
    }

    private JsonNode parseJson(String responseBody) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("JSON 파싱 오류", e);
        }
    }

    private List<Issue> extractIssues(JsonNode root) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<Issue> issues = new ArrayList<>();
        try {
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return issues;

            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isEmpty()) return issues;

            content = cleanJsonContent(content);

            JsonNode issuesRoot = mapper.readTree(content);
            JsonNode issuesNode = issuesRoot.path("issues");
            if (!issuesNode.isArray()) return issues;

            for (JsonNode issueNode : issuesNode) {
                Issue issue = mapper.treeToValue(issueNode, Issue.class);
                issues.add(issue);
            }
        } catch (Exception e) {
        }
        return issues;
    }

    public String translateText(String text, String targetLanguage) {
        String prompt = String.format("다음 텍스트를 %s로 자연스럽게 번역하세요:\n\n%s", targetLanguage, text);
        return getGptResponse(prompt);
    }


    private String cleanJsonContent(String content) {
        content = content.trim();

        if (content.startsWith("```json")) {
            return content.substring(7, content.length() - 3).trim();
        }
        if (content.startsWith("```")) {
            return content.substring(3, content.length() - 3).trim();
        }
        if (content.startsWith("`") && content.endsWith("`")) {
            return content.substring(1, content.length() - 1).trim();
        }
        return content;
    }


    public String summarizeAndTranslate(String text, String targetLanguage) {
        String prompt = String.format("""
        다음 법령 내용을 %s 언어로 요약하세요:
        - 3문장 이내
        - 500자(공백 포함) 미만
        - JSON 형식 사용 금지
        내용: 
        %s
        """, targetLanguage, text);

        String result = getGptResponse(prompt);
        return result.length() > 500 ? result.substring(0, 500) : result;
    }

    private String getGptResponse(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", "gpt-3.5-turbo");

            ArrayNode messages = mapper.createArrayNode();
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);

            requestBody.set("messages", messages);
            requestBody.put("temperature", 0.3);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("GPT 처리 실패", e);
        }
    }
}
