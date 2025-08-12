//package contest.mobicom_contest.contract.service;
//
//import contest.mobicom_contest.contract.client.PapagoClient;
//import contest.mobicom_contest.contract.client.S3Uploader;
//import contest.mobicom_contest.contract.model.Contract;
//import contest.mobicom_contest.contract.model.ContractRepository;
//import contest.mobicom_contest.member.model.Member;
//import contest.mobicom_contest.member.service.MemberService;
//import lombok.RequiredArgsConstructor;
//import org.json.JSONObject;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class ContractService {
//
//    private final PapagoClient papagoClient;
//    private final ContractRepository contractRepository;
//    private final S3Uploader s3Uploader;
//    private final MemberService memberService;
//
//    public Map<String, Object> analyze(Contract contract, MultipartFile contractImage) throws Exception {
//        try {
//            String originalImageUrl = s3Uploader.uploadFile(contractImage, "originals");
//
//            Member member = memberService.findById(contract.getMember().getId());
//            String targetLanguage = convertToLanguageCode(member.getLanguage());
//
//            JSONObject papagoResponse = papagoClient.translateImage(contractImage, "auto", targetLanguage);
//
//            String sourceText = papagoClient.extractSourceText(papagoResponse);
//            byte[] translatedImageBytes = papagoClient.extractTranslatedImage(papagoResponse);
//
//            String translatedImageUrl = s3Uploader.uploadBytes(translatedImageBytes, "contracts/translated",
//                    contract.getContractId() + "_translated.jpg");
//
//            contract.setOriginalImagePath(originalImageUrl);
//            contract.setTranslatedImagePath(translatedImageUrl);
//            contract.setOcrText(sourceText);
//            contractRepository.save(contract);
//
//            return Map.of(
//                    "originalImage", originalImageUrl,
//                    "translatedImage", translatedImageUrl
//            );
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("분석 오류 : " + e.getMessage());
//        }
//    }
//
//    private String convertToLanguageCode(String language) {
//        if (language == null) return "en";
//
//        return switch (language.toLowerCase()) {
//            case "korean" -> "ko";
//            case "vietnam" -> "vi";
//            case "china" -> "zh-CN";
//            default -> "en";
//        };
//    }
//
//    public Contract save(Long memberId, MultipartFile file) {
//        Member member = memberService.findById(memberId);
//        Contract contract = Contract.builder()
//                .member(member)
//                .build();
//        return contractRepository.save(contract);
//    }
//
//    public List<Contract> findAllByMemberId(Long memberId) {
//        return contractRepository.findByMemberId(memberId);
//    }
//
//    public Contract findById(Long contractId) {
//        return contractRepository.findById(contractId)
//                .orElseThrow(() -> new IllegalArgumentException("계약서를 찾을 수 없습니다."));
//    }
//}

package contest.mobicom_contest.contract.service;

import contest.mobicom_contest.contract.client.PapagoClient;
import contest.mobicom_contest.contract.client.S3Uploader;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.contract.model.ContractRepository;
import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final S3Uploader s3Uploader;
    private final MemberService memberService;

    private static final String EXAMPLE_ORIGINAL_URL =
            "https://mobicom-contest-bucket-2025.s3.ap-northeast-2.amazonaws.com/originals/0be4aa49-4025-4997-9398-ce2d9fe9df08_1000006919.png";
    private static final String EXAMPLE_TRANSLATED_URL =
            "https://mobicom-contest-bucket-2025.s3.ap-northeast-2.amazonaws.com/contracts/translated/02521fe4-2421-4494-b06e-8bf971ddade2_11_translated.jpg";

    private static final String HARDCODED_OCR_TEXT =
            "표준근로계약서\n" +
                    "김사장 (이하 \"사업주\"라 함)과(와) 이노동 (이하 \"근로\n" +
                    "자\"라 함)은 다음과 같이 근로계약을 체결한다.\n" +
                    "1. 근로계약기간 : 2023년 3월 23일부터 2024년 3월 22일까지\n" +
                    "※ 근로계약기간을 정하지 않는 경우에는 \"근로개시일\"만 기재\n" +
                    "2. 근 무 장 소 : 경북 영천시 천문로 349\n" +
                    "3. 업무의 내용 : 송무, 기타 사무보조에 필요한 일\n" +
                    "4. 소정근로시간 : 09시 00분부터 18시 00분까지(휴게시간 :12시 00분 ~ 13시 00분)\n" +
                    "5. 근무일/휴일 : 매주 평일(또는 매일단위)근무, 주휴일 매주 일요일\n" +
                    "6. 임 금\n" +
                    "- 월(일, 시간)급 : 시급 10,000원 (주휴수당 제외)\n" +
                    "- 상여금 : 있음 ( ) 원, 없음 ( ○ )\n" +
                    "- 기타급여(제수당 등) : 있음 ( ), 없음 ( ○ )\n" +
                    "원, 원\n" +
                    "원, 원\n" +
                    "- 임금지급일 : 매월(매주 또는 매일) 25일(휴일의 경우는 전일 지급)\n" +
                    "- 지급방법 : 근로자에게 직접지급( ), 근로자 명의 예금통장에 입금( ○)\n" +
                    "7. 연차유급휴가\n" +
                    "- 연차유급휴가는 근로기준법에서 정하는 바에 따라 부여함\n" +
                    "8. 근로계약서 교부\n" +
                    "- 사업주는 근로계약을 체결함과 동시에 본 계약서를 사본하여 근로자의\n" +
                    "교부요구와 관계없이 근로자에게 교부함(근로기준법 제17조 이행)\n" +
                    "9. 기 타\n" +
                    "- 이 계약에 정함이 없는 사항은 근로기준법령에 의함";

    /**
     * Papago 호출을 하지 않고 S3에 이미 올라가 있는 이미지/텍스트를 사용해서 분석 결과를 채워주는 하드코딩 버전.
     *
     * 사용법:
     * - 빠른 테스트용: contractImage 를 null 로 넘기고 EXAMPLE_* 상수 사용
     * - 실제 배포용(일반화): 원본 S3 URL을 업로드하거나 contractImage로 업로드한 뒤 deriveTranslatedUrl 로 번역본 URL을 만들 수 있음
     */
    public Map<String, Object> analyze(Contract contract, MultipartFile contractImage) throws Exception {
        try {
            String originalImageUrl;

            // 1) 이미 S3에 올라가 있는 파일을 테스트하려면 contractImage == null 로 호출하고 상수 사용
            if (contractImage == null) {
                // 예제 하드코딩 URL 사용
                originalImageUrl = EXAMPLE_ORIGINAL_URL;
            } else {
                // 파일이 들어왔으면 기존대로 originals에 업로드
                originalImageUrl = s3Uploader.uploadFile(contractImage, "originals");
            }

            // 2) 번역된(합성된) 이미지 URL 결정 — 두 가지 선택지 제공
            // A) 예제 그대로 하드코딩 사용 (테스트 전용)
            String translatedImageUrl = EXAMPLE_TRANSLATED_URL;

            // B) 일반화: 원본 URL에서 경로/파일명 규칙으로 파생 (원하면 주석 해제하여 사용)
            // translatedImageUrl = deriveTranslatedUrl(originalImageUrl);

            // 3) OCR 텍스트는 하드코딩된 값 사용 (주어진 OCRText)
            String sourceText = HARDCODED_OCR_TEXT;

            // 4) 멤버랑 계약 객체 저장
            Member member = memberService.findById(contract.getMember().getId());
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

    /**
     * 원본 S3 URL을 보고 규칙적으로 번역본 URL을 파생하는 예시 함수.
     * 구현 규칙은 프로젝트의 S3 네이밍 규칙에 맞추어 수정하세요.
     *
     * 예:
     *  https://.../originals/{filename}.png
     * ->
     *  https://.../contracts/translated/{filenameWithoutExt}_translated.jpg
     */
    private String deriveTranslatedUrl(String originalUrl) {
        if (originalUrl == null) return null;

        // 쉬운 변환 예: "/originals/" -> "/contracts/translated/"
        String translatedUrl = originalUrl.replace("/originals/", "/contracts/translated/");

        // 파일명 끝을 _translated.jpg 로 바꾸는 로직 (확장자 변환 포함)
        int lastSlash = translatedUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < translatedUrl.length() - 1) {
            String filename = translatedUrl.substring(lastSlash + 1);
            int dot = filename.lastIndexOf('.');
            String base = (dot == -1) ? filename : filename.substring(0, dot);
            String newFilename = base + "_translated.jpg";
            translatedUrl = translatedUrl.substring(0, lastSlash + 1) + newFilename;
        }
        return translatedUrl;
    }

    // 기존 다른 메서드들 유지
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
