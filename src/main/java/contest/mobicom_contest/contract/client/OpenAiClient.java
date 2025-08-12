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
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode message1 = mapper.createObjectNode();
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

        ObjectNode message2 = mapper.createObjectNode();
        message2.put("role", "user");
        message2.put("content", text);

        ObjectNode requestBody = mapper.createObjectNode();
        // [수정 1] 모델을 gpt-4o로 업그레이드하여 성능 향상
        requestBody.put("model", "gpt-4o");
        requestBody.set("messages", mapper.createArrayNode().add(message1).add(message2));
        requestBody.put("temperature", 0.2); // 일관된 답변을 위해 temperature 조정

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        String requestJson = mapper.writeValueAsString(requestBody);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

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
            당신은 다국어 번역 전문가입니다. 다음 텍스트를 %s(으)로 최대한 자연스럽고 정확하게 번역해주세요.
            
            --- 원문 ---
            %s
            """, targetLanguage, text);
        return getGptResponse(prompt);
    }
    
    public String summarizeAndTranslate(String text, String targetLanguage) {
        // [수정 2] AI 환각을 방지하기 위해 프롬프트를 명확하고 구체적으로 변경
        String prompt = String.format("""
            당신은 법률 문서를 일반인이 이해하기 쉽게 설명하는 전문가입니다.
            아래에 제공된 법률 원문 내용을 바탕으로, 핵심적인 내용을 2-4문장으로 요약하고 %s(으)로 번역해주세요.
            반드시 원문에 있는 내용만을 근거로 설명해야 하며, 법률 용어는 쉬운 단어로 풀어써주세요.
            
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

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode requestBody = mapper.createObjectNode();
            // [수정 3] 이 메서드에서 호출하는 모델도 gpt-4o로 통일
            requestBody.put("model", "gpt-4o"); 

            ArrayNode messages = mapper.createArrayNode();
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);

            requestBody.set("messages", messages);
            requestBody.put("temperature", 0.2);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("GPT 처리 실패", e);
        }
    }

    // 아래는 기존 코드와 동일
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
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // 알 수 없는 속성은 무시
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
                issues.add(mapper.treeToValue(issueNode, Issue.class));
            }
        } catch (Exception e) {
            // 로깅 추가
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
