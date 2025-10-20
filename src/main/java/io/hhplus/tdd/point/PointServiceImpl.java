package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointServiceImpl implements PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointServiceImpl(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    @Override
    public UserPoint getPoint(long id) {
        return userPointTable.selectById(id);
    }

    @Override
    public List<PointHistory> getHistories(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    @Override
    public UserPoint chargePoint(long id, long amount) {
        validateAmount(amount);

        UserPoint currentPoint = userPointTable.selectById(id);
        long newAmount = currentPoint.point() + amount;

        UserPoint updatedPoint = userPointTable.insertOrUpdate(id, newAmount);
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, updatedPoint.updateMillis());

        return updatedPoint;
    }

    @Override
    public UserPoint usePoint(long id, long amount) {
        validateAmount(amount);

        UserPoint currentPoint = userPointTable.selectById(id);

        if (currentPoint.point() < amount) {
            throw new InsufficientPointException(
                String.format("잔고가 부족합니다. 현재 포인트: %d, 사용 요청: %d", currentPoint.point(), amount)
            );
        }

        long newAmount = currentPoint.point() - amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(id, newAmount);
        pointHistoryTable.insert(id, amount, TransactionType.USE, updatedPoint.updateMillis());

        return updatedPoint;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("포인트 금액은 0보다 커야 합니다.");
        }
    }
}
