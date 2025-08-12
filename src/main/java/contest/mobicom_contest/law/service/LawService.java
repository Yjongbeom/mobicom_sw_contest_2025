package contest.mobicom_contest.law.service;

import contest.mobicom_contest.contract.client.LawApiClient;
import contest.mobicom_contest.contract.client.OpenAiClient;
import contest.mobicom_contest.contract.dto.Issue;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.contract.model.ContractRepository;
import contest.mobicom_contest.law.dto.LawAnalyzeDto;
import contest.mobicom_contest.law.dto.LawInfoDTO;
import contest.mobicom_contest.law.model.LawInfo;
import contest.mobicom_contest.law.model.LawInfoRepository;
import contest.mobicom_contest.member.model.Member;

// [추가] WebDriverManager import
import io.github.bonigarcia.wdm.WebDriverManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LawService {

    private final LawInfoRepository lawInfoRepository;
    private final OpenAiClient openAiClient;
    private final LawApiClient lawApiClient;
    private final ContractRepository contractRepository;

    public LawAnalyzeDto analyzeLegalIssues(Long contractId) throws Exception {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("계약서 없음"));

        Member member = contract.getMember();
        final String targetLanguage = (member.getLanguage() == null || member.getLanguage().isBlank())
                ? "English"
                : member.getLanguage();

        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        for (Issue issue : issues) {
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);

            laws.forEach(law -> {
                try {
                    String lawContent = fetchLawDetailContent(law.getDetailUrl());

                    if (lawContent == null || lawContent.isBlank()) {
                        log.warn("법률 '{}'의 내용을 가져오지 못해 처리를 건너뜁니다.", law.getLawName());
                        return;
                    }

                    law.setTranslatedLawName(
                            openAiClient.translateText(law.getLawName(), targetLanguage)
                    );

                    String summary = openAiClient.summarizeAndTranslate(lawContent, targetLanguage);
                    law.setTranslatedSummary(summary);
                    law.setContract(contract);
                } catch (Exception e) {
                    log.error("법령 상세 조회 또는 AI 처리 실패: law={}, error={}", law.getLawName(), e.getMessage());
                }
            });

            try {
                List<LawInfo> validLaws = laws.stream()
                        .filter(law -> law.getTranslatedSummary() != null && !law.getTranslatedSummary().isBlank())
                        .collect(Collectors.toList());
                lawInfoRepository.saveAll(validLaws);
                issueLawMap.put(issue, validLaws);
            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage());
                throw new RuntimeException("법령 정보 저장 실패", e);
            }
        }

        return new LawAnalyzeDto(
                contract.getContractId(),
                issues,
                issueLawMap.values().stream()
                        .flatMap(List::stream)
                        .map(LawInfoDTO::new)
                        .collect(Collectors.toList())
        );
    }

    private String fetchLawDetailContent(String detailPath) {
        if (detailPath == null || detailPath.isBlank()) {
            return "";
        }

        // ================== [최종 수정] ==================
        // 1. WebDriverManager가 자동으로 chromedriver를 다운로드하고 설정합니다.
        WebDriverManager.chromedriver().setup();
        // ===============================================

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        // User-Agent 추가 (봇으로 인식되는 것을 방지)
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            String pageUrl = "https://www.law.go.kr" + detailPath;
            log.info("Selenium으로 페이지 접속: {}", pageUrl);
            driver.get(pageUrl);

            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("lawService")));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#contentBody")));

            Document doc = Jsoup.parse(driver.getPageSource());
            Elements contentElements = doc.select("#contentBody");

            return contentElements.text();

        } catch (Exception e) {
            log.error("Selenium 크롤링 중 오류 발생: {}", e.getMessage());
            return "";
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}
