package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PointControllerTest {

    private final MockMvc mockMvc;
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    @Autowired
    public PointControllerTest(MockMvc mockMvc, UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.mockMvc = mockMvc;
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    @BeforeEach
    void setUp() {
        // 테스트 데이터 셋업 (Fake 객체 초기화)
        // UserPointTable과 PointHistoryTable은 실제로 in-memory이므로 직접 초기화
        userPointTable.insertOrUpdate(1L, 1000L);
        userPointTable.insertOrUpdate(2L, 500L);

        pointHistoryTable.insert(1L, 1000L, TransactionType.CHARGE, System.currentTimeMillis());
        pointHistoryTable.insert(1L, 200L, TransactionType.USE, System.currentTimeMillis());
    }

    @Test
    @DisplayName("포인트 조회 - 성공")
    void getPoint_Success() throws Exception {
        mockMvc.perform(get("/point/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.point").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("포인트 조회 - 존재하지 않는 유저는 0 포인트 반환")
    void getPoint_NotExistUser() throws Exception {
        mockMvc.perform(get("/point/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(999))
            .andExpect(jsonPath("$.point").value(0));
    }

    @Test
    @DisplayName("포인트 내역 조회 - 성공")
    void getHistories_Success() throws Exception {
        mockMvc.perform(get("/point/1/histories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    @DisplayName("포인트 내역 조회 - 내역이 없는 유저")
    void getHistories_NoHistory() throws Exception {
        mockMvc.perform(get("/point/999/histories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("포인트 충전 - 성공")
    void chargePoint_Success() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.point").value(greaterThan(1000)));
    }

    @Test
    @DisplayName("포인트 충전 - 0 이하 금액으로 실패")
    void chargePoint_InvalidAmount() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("0보다 커야")));
    }

    @Test
    @DisplayName("포인트 충전 - 음수 금액으로 실패")
    void chargePoint_NegativeAmount() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("-100"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("0보다 커야")));
    }

    @Test
    @DisplayName("포인트 사용 - 성공")
    void usePoint_Success() throws Exception {
        mockMvc.perform(patch("/point/1/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("300"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.point").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("포인트 사용 - 잔고 부족으로 실패")
    void usePoint_InsufficientBalance() throws Exception {
        mockMvc.perform(patch("/point/2/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("1000"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("잔고가 부족")));
    }

    @Test
    @DisplayName("포인트 사용 - 정확히 잔고만큼 사용 성공")
    void usePoint_ExactBalance() throws Exception {
        // 먼저 현재 포인트 확인
        String pointResponse = mockMvc.perform(get("/point/2"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // 정확히 잔고만큼 사용
        mockMvc.perform(patch("/point/2/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.point").value(0));
    }

    @Test
    @DisplayName("포인트 사용 - 0 이하 금액으로 실패")
    void usePoint_InvalidAmount() throws Exception {
        mockMvc.perform(patch("/point/1/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("0보다 커야")));
    }

    @Test
    @DisplayName("포인트 충전 후 내역 확인")
    void chargePoint_ThenCheckHistory() throws Exception {
        // 포인트 충전
        mockMvc.perform(patch("/point/1/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("1000"))
            .andExpect(status().isOk());

        // 내역 확인
        mockMvc.perform(get("/point/1/histories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThan(2))));
    }

    @Test
    @DisplayName("포인트 사용 후 내역 확인")
    void usePoint_ThenCheckHistory() throws Exception {
        // 포인트 사용
        mockMvc.perform(patch("/point/1/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("200"))
            .andExpect(status().isOk());

        // 내역 확인
        mockMvc.perform(get("/point/1/histories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThan(2))));
    }
}
