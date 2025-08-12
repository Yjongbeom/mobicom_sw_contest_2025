package contest.mobicom_contest.contract.client;

import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.law.model.LawInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
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

    // application.properties 등에서 'http://www.law.go.kr/DRF/lawSearch.do'로 설정
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

    /**
     * 법령 목록 조회 API를 호출합니다.
     */
    public List<LawInfo> searchRelatedLaws(String issueType, Contract contract) {
        List<LawInfo> laws = new ArrayList<>();
        String query = QUERY_MAP.getOrDefault(issueType, issueType);

        for (String target : TARGET_MAP.getOrDefault(issueType, List.of("law"))) {
            try {
                String url = lawApiUrl
                        + "?ServiceKey=" + apiKey
                        + "&target=" + target
                        + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&type=XML"
                        + "&numOfRows=3"; // 관련성 높은 3개만 가져오도록 제한

                log.info("법령 목록 API 호출: {}", url);
                RestTemplate restTemplate = new RestTemplate();
                String xmlResponse = restTemplate.getForObject(URI.create(url), String.class);
                
                if (xmlResponse != null) {
                    laws.addAll(parseLawSearchXml(xmlResponse, contract));
                }
            } catch (Exception e) {
                log.error("법령 목록 조회 실패 (target={}, 이슈={}): {}", target, issueType, e.getMessage());
            }
        }
        return laws;
    }

    /**
     * [신규] 법령 본문 조회 API를 호출하여 법률의 전체 내용을 가져옵니다.
     */
    public String fetchLawDetailByApi(String lawSerialNumber) {
        if (lawSerialNumber == null || lawSerialNumber.isBlank()) {
            log.warn("법령일련번호(MST)가 없어 상세 내용을 조회할 수 없습니다.");
            return "";
        }
        try {
            // API 명세에 따라 요청 URL을 'lawService.do'로 변경
            String url = lawApiUrl.replace("/lawSearch.do", "/lawService.do")
                    + "?ServiceKey=" + apiKey
                    + "&target=law"
                    + "&MST=" + lawSerialNumber // '법령 마스터 번호' 사용
                    + "&type=XML";

            log.info("법령 본문 API 호출: {}", url);
            RestTemplate restTemplate = new RestTemplate();
            String xmlResponse = restTemplate.getForObject(URI.create(url), String.class);

            if (xmlResponse != null) {
                return parseLawDetailXml(xmlResponse);
            }
        } catch (Exception e) {
            log.error("법령 본문 조회 실패 (MST={}): {}", lawSerialNumber, e.getMessage());
        }
        return "";
    }

    /**
     * [수정] 법령 목록 검색 결과를 파싱하여 '법령일련번호'를 LawInfo 객체에 저장합니다.
     */
    private List<LawInfo> parseLawSearchXml(String xml, Contract contract) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList nodes = doc.getElementsByTagName("law");
        List<LawInfo> laws = new ArrayList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Element elem = (Element) nodes.item(i);
            
            String lawSerialNumber = getElementText(elem, "법령일련번호");

            laws.add(LawInfo.builder()
                    .lawName(getElementText(elem, "법령명한글"))
                    .lawSerialNumber(lawSerialNumber) // LawInfo 객체에 법령일련번호 저장
                    .referenceNumber(getElementText(elem, "공포번호"))
                    .detailUrl(getElementText(elem, "법령상세링크"))
                    .contract(contract)
                    .build());
        }
        return laws;
    }

    /**
     * [신규] 법령 본문 조회 결과를 파싱하여 '조문내용'만 모두 합쳐서 반환합니다.
     */
    private String parseLawDetailXml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList contentNodes = doc.getElementsByTagName("조문내용");
        StringBuilder contentBuilder = new StringBuilder();
        
        for (int i = 0; i < contentNodes.getLength(); i++) {
            contentBuilder.append(contentNodes.item(i).getTextContent()).append("\n\n");
        }
        return contentBuilder.toString();
    }

    private String getElementText(Element elem, String tagName) {
        NodeList nodes = elem.getElementsByTagName(tagName);
        return (nodes.getLength() > 0) ? nodes.item(0).getTextContent().trim() : null;
    }
}
