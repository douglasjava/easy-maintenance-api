CREATE TABLE maintenance_attachments (
                                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                         maintenance_id BIGINT NOT NULL,

    -- Tipo do anexo para UX e compliance
                                         attachment_type ENUM('PHOTO','REPORT','CERTIFICATE','ART','INVOICE','OTHER')
                                             NOT NULL DEFAULT 'OTHER',

    -- URL do arquivo no S3 (ou compatível)
                                         file_url VARCHAR(600) NOT NULL,

                                         file_name VARCHAR(255) DEFAULT NULL,
                                         content_type VARCHAR(120) DEFAULT NULL,
                                         size_bytes BIGINT DEFAULT NULL,

                                         uploaded_by_user_id BIGINT DEFAULT NULL,
                                         uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                         KEY idx_attach_maintenance (maintenance_id),
                                         KEY idx_attach_type (attachment_type),
                                         KEY idx_attach_uploaded_by (uploaded_by_user_id),

                                         CONSTRAINT fk_attach_maintenance
                                             FOREIGN KEY (maintenance_id) REFERENCES maintenances(id)
                                                 ON DELETE CASCADE
                                                 ON UPDATE CASCADE

    -- Se você quiser amarrar no usuário depois, descomente:
    -- ,CONSTRAINT fk_attach_user
    --   FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
    --   ON DELETE SET NULL
    --   ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
