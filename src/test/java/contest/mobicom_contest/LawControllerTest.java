package contest.mobicom_contest;

import contest.mobicom_contest.law.controller.LawController;
import contest.mobicom_contest.law.service.LawService;
import contest.mobicom_contest.contract.service.ContractService;
import contest.mobicom_contest.law.model.LawInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.BDDMockito.given;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LawController.class)
class LawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LawService lawService;

    @MockBean
    private ContractService contractService;

    @Test
    @DisplayName("GET /api/contracts/1/lawinfo - 특정 계약서 법률 정보 조회")
    void testGetLawsByContract() throws Exception {
        given(lawService.getLawsByContractId(1L)).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/contracts/1/lawinfo"))
                .andExpect(status().isOk());
    }

}
