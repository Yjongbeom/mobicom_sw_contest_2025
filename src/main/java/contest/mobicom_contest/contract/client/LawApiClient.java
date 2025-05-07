package contest.mobicom_contest.contract.client;

import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.law.model.LawInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawApiClient {

    @Value("${law.api.key}")
    private String apiKey;

    @Value("${law.api.url}")
    private String lawApiUrl;

    private static final Map<String, List<String>> TARGET_MAP = Map.of(
            "퇴직금", List.of("law", "expc"),
            "최저임금", List.of("law", "expc"),
            "근로시간", List.of("law", "expc", "admrul"),
            "부당해고", List.of("law", "expc", "admrul", "detc"),
            "계약해지", List.of("law", "expc", "ordin"),
            "기타", List.of("law", "expc")
    );

    private static final Map<String, String> QUERY_MAP = Map.of(
            "최저임금", "최저임금법",
            "근로시간", "근로기준법",
            "퇴직금", "근로자퇴직급여",
            "부당해고", "근로기준법 제23조",
            "계약해지", "근로계약 해지"
    );

    public List<LawInfo> searchRelatedLaws(String issueType, Contract contract) {
        List<LawInfo> laws = new ArrayList<>();
        String query = QUERY_MAP.getOrDefault(issueType, issueType);

        String encodedKey = apiKey;

        for (String target : TARGET_MAP.getOrDefault(issueType, List.of("law"))) {
            try {
                String url = lawApiUrl
                        + "?ServiceKey=" + encodedKey
                        + "&target=" + target
                        + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&numOfRows=10&pageNo=1";

                log.debug("법령 API 호출: {}", url);

                URI uri = URI.create(url);
                RestTemplate restTemplate = createRestTemplate();
                String xmlResponse = restTemplate.getForObject(uri, String.class);


                log.debug("XML 응답(앞 300자): {}", xmlResponse != null ? xmlResponse.substring(0, Math.min(300, xmlResponse.length())) : "null");

                if (xmlResponse != null && !xmlResponse.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
                    laws.addAll(parseXml(xmlResponse, contract));
                } else {
                    log.warn("API 오류 응답: {}", xmlResponse);
                }

            } catch (Exception e) {
                log.error("법령 조회 실패 (target={}, 이슈={}): {}", target, issueType, e.getMessage());
            }
        }

        return laws;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    private List<LawInfo> parseXml(String xml, Contract contract) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        String resultCode = doc.getElementsByTagName("resultCode").item(0).getTextContent();
        if (!"00".equals(resultCode)) {
            throw new Exception("API 오류: " + doc.getElementsByTagName("resultMsg").item(0).getTextContent());
        }

        List<LawInfo> laws = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("law");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element elem = (Element) nodes.item(i);

            String lawName = getElementText(elem, "법령명한글");
            String pubNo = getElementText(elem, "공포번호");
            String detailUrl = getElementText(elem, "법령상세링크");

            if (lawName == null || pubNo == null || detailUrl == null) {
                System.err.println("⚠️ 필수 필드 누락 - 법령명: " + lawName);
                continue;
            }

            laws.add(LawInfo.builder()
                    .lawName(lawName)
                    .referenceNumber(pubNo)
                    .detailUrl(detailUrl)
                    .contract(contract)
                    .build());
        }
        return laws;
    }

    private String getElementText(Element elem, String tagName) {
        NodeList nodes = elem.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }
}
