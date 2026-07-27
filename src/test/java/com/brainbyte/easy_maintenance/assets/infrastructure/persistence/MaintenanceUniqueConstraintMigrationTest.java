package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida em SQL puro (H2 em modo MySQL, sem Spring/Hibernate) o comportamento da constraint
 * uq_maint_item_date criada na V24 — ela não considera deleted_at, então bloqueia reaproveitar
 * (item_id, performed_at) de uma manutenção cancelada (achado no QA manual, TASK-QA-MAN-011 C1):
 * soft-delete não remove a linha fisicamente, então a UNIQUE antiga conflita com o cadastro de
 * uma nova manutenção pro mesmo item/dia mesmo depois de cancelar a anterior.
 *
 * A V85 troca a constraint por uma versão que ignora canceladas: todas as manutenções ATIVAS
 * compartilham active_dedup_key = 0 (preservando a unicidade entre elas); ao cancelar,
 * MaintenanceService.cancel() seta active_dedup_key = próprio id, que nunca colide. (Descartamos
 * a alternativa "coluna gerada" porque o MySQL proíbe coluna gerada referenciar AUTO_INCREMENT —
 * erro 3109, confirmado contra o MySQL real do projeto antes de trocar de abordagem.)
 *
 * Replicado aqui com DDL puro (sem rodar o histórico completo de migrations, que usa bastante
 * sintaxe MySQL-only incompatível com H2) só pra validar a lógica da constraint em si.
 */
class MaintenanceUniqueConstraintMigrationTest {

    @Test
    void oldConstraint_blocksReregistrationOfCancelledDay() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:uq_old;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            createBaseTable(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE maintenances ADD UNIQUE (item_id, performed_at)");
                st.execute("INSERT INTO maintenances (item_id, performed_at, deleted_at) VALUES (1, '2026-07-26', NULL)");
                st.execute("UPDATE maintenances SET deleted_at = NOW() WHERE item_id = 1");

                assertThatThrownBy(() ->
                        st.execute("INSERT INTO maintenances (item_id, performed_at, deleted_at) VALUES (1, '2026-07-26', NULL)")
                ).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void fixedConstraint_allowsReregistrationAfterCancellationButBlocksActiveDuplicate() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:uq_fixed;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            createBaseTable(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE maintenances ADD COLUMN active_dedup_key BIGINT NOT NULL DEFAULT 0");
                st.execute("ALTER TABLE maintenances ADD UNIQUE (item_id, performed_at, active_dedup_key)");

                // M1: registrada ativa (active_dedup_key = 0, o default)
                st.execute("INSERT INTO maintenances (item_id, performed_at, deleted_at) VALUES (1, '2026-07-26', NULL)",
                        Statement.RETURN_GENERATED_KEYS);
                long m1Id;
                try (var keys = st.getGeneratedKeys()) {
                    keys.next();
                    m1Id = keys.getLong(1);
                }
                // cancelar M1: exatamente o que MaintenanceService.cancel() faz — seta deleted_at
                // E active_dedup_key = próprio id, junto com cancelledAt/By/Reason
                st.execute("UPDATE maintenances SET deleted_at = NOW(), active_dedup_key = " + m1Id + " WHERE id = " + m1Id);

                // M2: depois de cancelar M1, registrar de novo no mesmo dia deve funcionar
                st.execute("INSERT INTO maintenances (item_id, performed_at, deleted_at) VALUES (1, '2026-07-26', NULL)");

                // M3: mas duplicar uma manutenção ATIVA (M2) no mesmo dia continua bloqueado
                assertThatThrownBy(() ->
                        st.execute("INSERT INTO maintenances (item_id, performed_at, deleted_at) VALUES (1, '2026-07-26', NULL)")
                ).isInstanceOf(SQLException.class);
            }
        }
    }

    private void createBaseTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE maintenances (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "item_id BIGINT NOT NULL, " +
                    "performed_at DATE NOT NULL, " +
                    "deleted_at TIMESTAMP NULL)");
        }
    }
}
