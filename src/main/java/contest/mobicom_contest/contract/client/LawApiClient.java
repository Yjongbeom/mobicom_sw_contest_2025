package contest.mobicom_contest.contract.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.law.model.LawInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawApiClient {

    @Value("${law.api.key}")
    private String apiKey;

    @Value("${law.api.oc}")
    private String oc;

    @Value("${law.api.url.search}")
    private String lawSearchApiUrl;

    @Value("${law.api.url.service}")
    private String lawServiceApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ... (TARGET_MAP, QUERY_MAP, searchRelatedLaws, fetchLawDetailByApi, parseLawSearchJson 메서드는 기존과 동일)
    private static final Map<String, List<String>> TARGET_MAP = Map.of(
            "퇴직금", List.of("law"), "최저임금", List.of("law"),
            "근로시간", List.of("law"), "부당해고", List.of("law"),
            "계약해지", List.of("law"), "기타", List.of("law")
    );
    private static final Map<String, String> QUERY_MAP = Map.of(
            "최저임금", "최저임금법", "근로시간", "근로기준법",
            "퇴직금", "근로자퇴직급여 보장법", "부당해고", "근로기준법",
            "계약해지", "근로기준법"
    );
    public List<LawInfo> searchRelatedLaws(String issueType, Contract contract) {
        String query = QUERY_MAP.getOrDefault(issueType, issueType);
        List<LawInfo> allLaws = new ArrayList<>();

        for (String target : TARGET_MAP.getOrDefault(issueType, List.of("law"))) {
            try {
                String url = lawSearchApiUrl
                        + "?ServiceKey=" + apiKey + "&OC=" + oc + "&target=" + target
                        + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&type=JSON" + "&numOfRows=3";

                log.info("법령 목록 API 호출: {}", url);
                String jsonResponse = restTemplate.getForObject(URI.create(url), String.class);
                
                if (jsonResponse != null) {
                    allLaws.addAll(parseLawSearchJson(jsonResponse, contract));
                }
            } catch (Exception e) {
                log.error("법령 목록 조회 실패 (target={}, 이슈={}): {}", target, issueType, e.getMessage());
            }
        }
        return allLaws;
    }
    public String fetchLawDetailByApi(String lawSerialNumber) {
        if (lawSerialNumber == null || lawSerialNumber.isBlank()) return "";
        try {
            String url = lawServiceApiUrl
                    + "?ServiceKey=" + apiKey + "&OC=" + oc + "&target=law"
                    + "&MST=" + lawSerialNumber + "&type=JSON";

            log.info("법령 본문 API 호출: {}", url);
            String jsonResponse = restTemplate.getForObject(URI.create(url), String.class);

            if (jsonResponse != null) {
                return parseLawDetailJson(jsonResponse);
            }
        } catch (Exception e) {
            log.error("법령 본문 조회 실패 (MST={}): {}", lawSerialNumber, e.getMessage());
        }
        return "";
    }
    private List<LawInfo> parseLawSearchJson(String json, Contract contract) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode lawNodes = root.path("LawSearch").path("law");
        List<LawInfo> laws = new ArrayList<>();
        if (lawNodes.isArray()) {
            for (JsonNode node : lawNodes) {
                laws.add(LawInfo.builder()
                        .lawName(node.path("법령명한글").asText())
                        .lawSerialNumber(node.path("법령일련번호").asText())
                        .referenceNumber(node.path("공포번호").asText())
                        .detailUrl(node.path("법령상세링크").asText())
                        .contract(contract)
                        .build());
            }
        }
        return laws;
    }


    /**
     * [최종 수정] 여러 형태의 법령 본문 JSON 구조에 모두 대응하도록 수정
     */
    private String parseLawDetailJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        StringBuilder contentBuilder = new StringBuilder();
        
        JsonNode lawMainNode = root.path("법령");
        
        // 1. 조문 내용 추출 (핵심 법 조항)
        JsonNode articleNodes = lawMainNode.path("조문");
        if (articleNodes.has("조문단위") && articleNodes.get("조문단위").isArray()) {
            // "조문": { "조문단위": [...] } 구조일 경우
            articleNodes = articleNodes.path("조문단위");
        }
        
        if (articleNodes.isArray()) {
            for (JsonNode article : articleNodes) {
                contentBuilder.append("【").append(article.path("조문제목").asText()).append("】\n");
                contentBuilder.append(stripHtmlTags(article.path("조문내용").asText())).append("\n");

                JsonNode clauseNodes = article.path("항");
                if (clauseNodes.isArray()) {
                    for (JsonNode clause : clauseNodes) {
                        contentBuilder.append(stripHtmlTags(clause.path("항내용").asText())).append("\n");
                    }
                }
                contentBuilder.append("\n");
            }
        }

        // 2. 제개정 이유 추출
        JsonNode reasonNode = lawMainNode.path("제개정이유").path("제개정이유내용");
        if (reasonNode.isArray()) {
            contentBuilder.append("--- 제·개정 이유 ---\n");
            for (JsonNode lineArray : reasonNode) {
                if(lineArray.isArray()){
                    for(JsonNode line : lineArray){
                        contentBuilder.append(line.asText()).append("\n");
                    }
                }
            }
        }

        if (contentBuilder.length() == 0) {
            log.warn("API 응답에서 법령 본문 내용을 추출하지 못했습니다. JSON 구조를 확인해주세요.");
        }

        return contentBuilder.toString();
    }

    private String stripHtmlTags(String htmlText) {
        if (htmlText == null) return "";
        // <br/> 태그는 줄바꿈으로, 나머지 HTML 태그는 제거
        return htmlText.replaceAll("<br/>", "\n").replaceAll("<[^>]*>", "");
    }
}
