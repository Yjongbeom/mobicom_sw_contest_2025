package contest.mobicom_contest.contract.client;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import java.io.IOException;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class PapagoClient {

    @Value("${papago.api.client-id}")
    private String clientId;

    @Value("${papago.api.client-secret}")
    private String clientSecret;

    @Value("${papago.api.url}")
    private String papagoApiUrl;

    private final RestTemplate restTemplate;

    public JSONObject translateImage(MultipartFile file, String sourceLanguage, String targetLanguage) throws Exception {
        byte[] resizedImageBytes = resizeImageToA4Portrait(file);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
        headers.set("X-NCP-APIGW-API-KEY", clientSecret);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(resizedImageBytes) {
            @Override
            public String getFilename() {
                return "resized_" + file.getOriginalFilename();
            }
        };

        body.add("image", resource);
        body.add("source", sourceLanguage);
        body.add("target", targetLanguage);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                papagoApiUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            return new JSONObject(response.getBody());
        } else {
            throw new Exception("Papago API Error: " + response.getBody());
        }
    }

    public byte[] extractTranslatedImage(JSONObject response) {
        String base64Image = response.getJSONObject("data").getString("renderedImage");
        return Base64.getDecoder().decode(base64Image);
    }

    public String extractSourceText(JSONObject response) {
        return response.getJSONObject("data").getString("sourceText");
    }

    public byte[] resizeImageToA4Portrait(MultipartFile file) throws IOException, IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        int targetHeight = 1960;
        int targetWidth = (int) Math.round(targetHeight / Math.sqrt(2)); // 약 1386

        if (originalWidth <= targetWidth && originalHeight <= targetHeight) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(originalImage, "jpg", baos);
            return baos.toByteArray();
        }

        double widthRatio = (double) targetWidth / originalWidth;
        double heightRatio = (double) targetHeight / originalHeight;
        double scale = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", baos);
        return baos.toByteArray();
    }
}
