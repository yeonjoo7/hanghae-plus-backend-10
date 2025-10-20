package io.hhplus.tdd.point;

import java.util.List;

public interface PointService {
    UserPoint getPoint(long id);
    List<PointHistory> getHistories(long id);
    UserPoint chargePoint(long id, long amount);
    UserPoint usePoint(long id, long amount);
}
