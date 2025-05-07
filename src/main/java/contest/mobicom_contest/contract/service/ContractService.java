package contest.mobicom_contest.contract.service;

import contest.mobicom_contest.contract.client.PapagoClient;
import contest.mobicom_contest.contract.client.S3Uploader;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.contract.model.ContractRepository;
import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final PapagoClient papagoClient;
    private final ContractRepository contractRepository;
    private final S3Uploader s3Uploader;
    private final MemberService memberService;

    public Map<String, Object> analyze(Contract contract, MultipartFile contractImage) throws Exception {
        try {
            String originalImageUrl = s3Uploader.uploadFile(contractImage, "originals");

            Member member = memberService.findById(contract.getMember().getId());
            String targetLanguage = convertToLanguageCode(member.getLanguage());

            JSONObject papagoResponse = papagoClient.translateImage(contractImage, "auto", targetLanguage);

            String sourceText = papagoClient.extractSourceText(papagoResponse);
            byte[] translatedImageBytes = papagoClient.extractTranslatedImage(papagoResponse);

            String translatedImageUrl = s3Uploader.uploadBytes(translatedImageBytes, "contracts/translated",
                    contract.getContractId() + "_translated.jpg");

            contract.setOriginalImagePath(originalImageUrl);
            contract.setTranslatedImagePath(translatedImageUrl);
            contract.setOcrText(sourceText);
            contractRepository.save(contract);

            return Map.of(
                    "originalImage", originalImageUrl,
                    "translatedImage", translatedImageUrl
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("분석 오류 : " + e.getMessage());
        }
    }

    private String convertToLanguageCode(String language) {
        if (language == null) return "en";

        return switch (language.toLowerCase()) {
            case "korean" -> "ko";
            case "vietnam" -> "vi";
            case "china" -> "zh-CN";
            default -> "en";
        };
    }

    public Contract save(Long memberId, MultipartFile file) {
        Member member = memberService.findById(memberId);
        Contract contract = Contract.builder()
                .member(member)
                .build();
        return contractRepository.save(contract);
    }

    public List<Contract> findAllByMemberId(Long memberId) {
        return contractRepository.findByMemberId(memberId);
    }

    public Contract findById(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("계약서를 찾을 수 없습니다."));
    }
}
