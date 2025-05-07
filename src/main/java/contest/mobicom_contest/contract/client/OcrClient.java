//package contest.mobicom_contest.contract.client;
//
//import lombok.RequiredArgsConstructor;
//import org.json.JSONObject;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.multipart.MultipartFile;
//
//@Component
//@RequiredArgsConstructor
//public class OcrClient {
//
//    @Value("${upstage.api.key}")
//    private String apiKey;
//
//    @Value("${upstage.api.url}")
//    private String ocrApiUrl;
//
//    private final RestTemplate restTemplate;
//
//    public JSONObject extractOcrJson(MultipartFile file) throws Exception {
//        System.out.println("ocrApiUrl = " + ocrApiUrl);
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + apiKey);
//        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
//            @Override
//            public String getFilename() {
//                return file.getOriginalFilename();
//            }
//        };
//
//        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//        body.add("document", resource);
//        body.add("model", "ocr");
//
//        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
//
//        ResponseEntity<String> response = restTemplate.exchange(
//                ocrApiUrl,
//                HttpMethod.POST,
//                entity,
//                String.class
//        );
//
//        if (response.getStatusCode() == HttpStatus.OK) {
//            System.out.println(response.getBody());
//            return new JSONObject(response.getBody());
//        } else {
//            throw new Exception("OCR API Error: " + response.getBody());
//        }
//    }
//}
