package com.brainbyte.easy_maintenance.dashboard.infrastructure.persistence;

import java.time.LocalDate;

public interface DashboardAggregations {

    interface CountByStatus {
        String getStatus();
        long getCnt();
    }

    interface CountByCategory {
        String getItemCategory();
        long getCnt();
    }

    interface CountByItemType {
        String getItemType();
        long getCnt();
    }

    interface CalendarBucket {
        LocalDate getDt();
        long getCnt();
    }
}
