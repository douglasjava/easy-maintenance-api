package com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class MaintenanceItemSpecs {

    private MaintenanceItemSpecs() {}

    public static Specification<MaintenanceItem> filter(String orgId, ItemStatus status, String itemType, ItemCategory categoria) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("organizationCode"), orgId));

            if (status != null) {
                LocalDate today = LocalDate.now();
                var nextDue = root.<LocalDate>get("nextDueAt");

                Predicate statusPredicate = switch (status) {
                    case OVERDUE -> cb.and(
                            cb.isNotNull(nextDue),
                            cb.lessThan(nextDue, today)
                    );
                    case NEAR_DUE -> cb.and(
                            cb.isNotNull(nextDue),
                            cb.greaterThanOrEqualTo(nextDue, today),
                            cb.lessThanOrEqualTo(nextDue, today.plusDays(30))
                    );
                    case OK -> cb.or(
                            cb.isNull(nextDue),
                            cb.greaterThan(nextDue, today.plusDays(30))
                    );
                };
                predicates.add(statusPredicate);
            }

            if (isNotBlank(itemType)) {
                String pattern = "%" + itemType
                        .toLowerCase()
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";

                predicates.add(
                        cb.like(cb.lower(root.get("itemType")), pattern, '\\')
                );
            }

            if (categoria != null) {
                predicates.add(cb.equal(root.get("itemCategory"), categoria));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<MaintenanceItem> dueDateBetween(LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> cb.between(root.get("nextDueAt"), fromDate, toDate);
    }
}
