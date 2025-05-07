package contest.mobicom_contest;

import contest.mobicom_contest.contract.controller.ContractController;
import contest.mobicom_contest.contract.service.ContractService;
import contest.mobicom_contest.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContractController.class)
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContractService contractService;

    @MockBean
    private MemberService memberService;

    @Test
    @DisplayName("GET /api/contract?memberId=1 - 계약서 목록 조회")
    void testGetAllContracts() throws Exception {
        mockMvc.perform(get("/api/contract")
                        .param("memberId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/contract/1/translate - 계약서 OCR 요청")
    void testAnalyzeContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", MediaType.IMAGE_PNG_VALUE, "test image".getBytes());

        mockMvc.perform(multipart("/api/contract/1/translate")
                        .file(file))
                .andExpect(status().isOk());
    }
}
