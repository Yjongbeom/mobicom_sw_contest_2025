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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawApiClient {

    @Value("${law.api.key}")
    private String apiKey;

    @Value("${law.api.oc}") // application.properties에 OC 값 추가 (예: sapphire_5)
    private String oc;

    @Value("${law.api.url.search}") // 목록 검색 API URL (예: http://www.law.go.kr/DRF/lawSearch.do)
    private String lawSearchApiUrl;

    @Value("${law.api.url.service}") // 본문 조회 API URL (예: http://www.law.go.kr/DRF/lawService.do)
    private String lawServiceApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, List<String>> TARGET_MAP = Map.of(
            "퇴직금", List.of("law"),
            "최저임금", List.of("law"),
            "근로시간", List.of("law"),
            "부당해고", List.of("law"),
            "계약해지", List.of("law"),
            "기타", List.of("law")
    );

    private static final Map<String, String> QUERY_MAP = Map.of(
            "최저임금", "최저임금법",
            "근로시간", "근로기준법",
            "퇴직금", "근로자퇴직급여 보장법",
            "부당해고", "근로기준법",
            "계약해지", "근로기준법"
    );

    /**
     * 법령 목록 조회 JSON API를 호출합니다.
     */
    public List<LawInfo> searchRelatedLaws(String issueType, Contract contract) {
        String query = QUERY_MAP.getOrDefault(issueType, issueType);
        List<LawInfo> allLaws = new ArrayList<>();

        for (String target : TARGET_MAP.getOrDefault(issueType, List.of("law"))) {
            try {
                String url = lawSearchApiUrl
                        + "?ServiceKey=" + apiKey
                        + "&OC=" + oc
                        + "&target=" + target
                        + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&type=JSON" // 응답 타입을 JSON으로 변경
                        + "&numOfRows=3";

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

    /**
     * 법령 본문 조회 JSON API를 호출하여 법률의 전체 내용을 가져옵니다.
     */
    public String fetchLawDetailByApi(String lawSerialNumber) {
        if (lawSerialNumber == null || lawSerialNumber.isBlank()) {
            return "";
        }
        try {
            String url = lawServiceApiUrl
                    + "?ServiceKey=" + apiKey
                    + "&OC=" + oc
                    + "&target=law"
                    + "&MST=" + lawSerialNumber // 법령일련번호(MST) 사용
                    + "&type=JSON";

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
        JsonNode lawNodes = root.path("lawSearchList");
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

    private String parseLawDetailJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        StringBuilder contentBuilder = new StringBuilder();

        // 조문 내용 추출
        JsonNode joContentNodes = root.path("조문");
        if (joContentNodes.isArray()) {
            for (JsonNode jo : joContentNodes) {
                contentBuilder.append(jo.path("조문제목").asText()).append("\n");
                contentBuilder.append(jo.path("조문내용").asText().replaceAll("<br/>", "\n")).append("\n\n");

                // 항 내용 추출
                JsonNode hangNodes = jo.path("항");
                if(hangNodes.isArray()){
                    for(JsonNode hang : hangNodes){
                        contentBuilder.append(hang.path("항내용").asText().replaceAll("<br/>", "\n")).append("\n");
                    }
                }
                contentBuilder.append("\n");
            }
        }
        return contentBuilder.toString();
    }
}
