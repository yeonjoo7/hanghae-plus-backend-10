package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointServiceImpl pointService;

    @Test
    @DisplayName("유저 포인트 조회 - 성공")
    void getPoint_Success() {
        // given
        long userId = 1L;
        UserPoint expectedPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(expectedPoint);

        // when
        UserPoint result = pointService.getPoint(userId);

        // then
        assertThat(result).isEqualTo(expectedPoint);
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);
        verify(userPointTable, times(1)).selectById(userId);
    }

    @Test
    @DisplayName("포인트 내역 조회 - 성공")
    void getHistories_Success() {
        // given
        long userId = 1L;
        List<PointHistory> expectedHistories = List.of(
            new PointHistory(1L, userId, 500L, TransactionType.CHARGE, System.currentTimeMillis()),
            new PointHistory(2L, userId, 200L, TransactionType.USE, System.currentTimeMillis())
        );
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistories);

        // when
        List<PointHistory> result = pointService.getHistories(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedHistories);
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("포인트 충전 - 성공")
    void chargePoint_Success() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long chargeAmount = 500L;
        long expectedAmount = 1500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(currentPoint);
        when(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).thenReturn(updatedPoint);
        when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
            .thenReturn(new PointHistory(1L, userId, chargeAmount, TransactionType.CHARGE, updatedPoint.updateMillis()));

        // when
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        // then
        assertThat(result.point()).isEqualTo(expectedAmount);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트 충전 - 0 이하 금액으로 실패")
    void chargePoint_InvalidAmount() {
        // given
        long userId = 1L;
        long invalidAmount = 0L;

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(userId, invalidAmount))
            .isInstanceOf(InvalidAmountException.class)
            .hasMessage("포인트 금액은 0보다 커야 합니다.");

        verify(userPointTable, never()).selectById(anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 - 성공")
    void usePoint_Success() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 300L;
        long expectedAmount = 700L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(currentPoint);
        when(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).thenReturn(updatedPoint);
        when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
            .thenReturn(new PointHistory(1L, userId, useAmount, TransactionType.USE, updatedPoint.updateMillis()));

        // when
        UserPoint result = pointService.usePoint(userId, useAmount);

        // then
        assertThat(result.point()).isEqualTo(expectedAmount);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 - 잔고 부족으로 실패")
    void usePoint_InsufficientBalance() {
        // given
        long userId = 1L;
        long currentAmount = 500L;
        long useAmount = 1000L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(currentPoint);

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
            .isInstanceOf(InsufficientPointException.class)
            .hasMessageContaining("잔고가 부족합니다");

        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 - 0 이하 금액으로 실패")
    void usePoint_InvalidAmount() {
        // given
        long userId = 1L;
        long invalidAmount = -100L;

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, invalidAmount))
            .isInstanceOf(InvalidAmountException.class)
            .hasMessage("포인트 금액은 0보다 커야 합니다.");

        verify(userPointTable, never()).selectById(anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 - 정확히 잔고만큼 사용 성공")
    void usePoint_ExactBalance() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 1000L;
        long expectedAmount = 0L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(currentPoint);
        when(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).thenReturn(updatedPoint);
        when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
            .thenReturn(new PointHistory(1L, userId, useAmount, TransactionType.USE, updatedPoint.updateMillis()));

        // when
        UserPoint result = pointService.usePoint(userId, useAmount);

        // then
        assertThat(result.point()).isEqualTo(0L);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedAmount);
    }
}
