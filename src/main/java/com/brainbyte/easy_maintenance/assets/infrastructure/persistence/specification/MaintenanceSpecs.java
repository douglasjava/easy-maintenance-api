package com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class MaintenanceSpecs {

    private MaintenanceSpecs() {}

    public static Specification<Maintenance> filter(String orgId, Long itemId, LocalDate performedAt, String issuedBy) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<MaintenanceItem> itemRoot = subquery.from(MaintenanceItem.class);

            subquery.select(itemRoot.get("id"))
                    .where(
                            cb.equal(itemRoot.get("organizationCode"), orgId),
                            cb.equal(itemRoot.get("id"), root.get("itemId"))
                    );

            predicates.add(cb.exists(subquery));

            if (itemId != null) {
                predicates.add(cb.equal(root.get("itemId"), itemId));
            }

            if (performedAt != null) {
                predicates.add(cb.equal(root.get("performedAt"), performedAt));
            }

            if (isNotBlank(issuedBy)) {
                String pattern = "%" + issuedBy
                        .toLowerCase()
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";

                predicates.add(
                        cb.like(cb.lower(root.get("issuedBy")), pattern, '\\')
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
