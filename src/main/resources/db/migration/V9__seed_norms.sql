INSERT INTO norms
(item_type, period_unit, period_qty, tolerance_days, authority, doc_url, notes)
VALUES
-- =========================
-- SPDA (ABNT NBR 5419)
-- =========================
('SPDA_INSPECAO_VISUAL', 'ANUAL', 1, 30, 'ABNT NBR 5419',
 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf',
 'NBR 5419 prevê inspeção visual anual.'),

('SPDA_INSPECAO_COMPLETA', 'ANUAL', 5, 60, 'ABNT NBR 5419',
 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf',
 'NBR 5419 cita inspeção completa periódica; ex.: 5 anos para estruturas residenciais/comerciais.'),

('ATERRAMENTO_MEDICAO_RESISTENCIA', 'ANUAL', 1, 30, 'ABNT NBR 5419',
 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf',
 'Medições e verificações conforme inspeções previstas na NBR 5419.'),

('EQUIPOTENCIALIZACAO_VERIFICACAO', 'ANUAL', 1, 30, 'ABNT NBR 5419',
 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf',
 'Verificar ligações equipotenciais e continuidade.'),

-- =========================
-- BRIGADA (CBMMG IT 12)
-- =========================
('BRIGADA_FORMACAO_RECICLAGEM', 'ANUAL', 2, 60, 'CBMMG IT 12 (Brigada de Incêndio)',
 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_12_2a_ed_portaria_35_2019.pdf',
 'Periodicidade máxima de 2 anos para formação/reciclagem do brigadista.'),

('SIMULADO_ABANDONO', 'ANUAL', 1, 60, 'CBMMG IT 12 (Brigada de Incêndio)',
 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_12_2a_ed_portaria_35_2019.pdf',
 'Simulado conforme plano de emergência e PSCIP.'),

-- =========================
-- ILUMINAÇÃO DE EMERGÊNCIA (IT 13)
-- =========================
('ILUMINACAO_EMERGENCIA_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 13',
 'https://bombeiros.mg.gov.br/images/stories/dat/it/it_13_iluminacao_de_emergencia.pdf',
 'Requisitos de projeto/instalação; manutenção conforme fabricante e PSCIP.'),

('ILUMINACAO_EMERGENCIA_BATERIAS', 'ANUAL', 0, 0, 'CBMMG IT 13',
 'https://bombeiros.mg.gov.br/images/stories/dat/it/it_13_iluminacao_de_emergencia.pdf',
 'Testes de autonomia e troca de baterias conforme fabricante.'),

-- =========================
-- HIDRANTES / MANGOTINHOS (IT 17)
-- =========================
('HIDRANTES_MANGOTINHOS_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 17',
 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf',
 'Manutenção conforme normas ABNT, RT e PSCIP.'),

('HIDRANTES_MANGUEIRAS_INSPECAO', 'ANUAL', 0, 0, 'CBMMG IT 17',
 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf',
 'Inspeções e ensaios conforme normas aplicáveis.'),

('BOMBAS_INCENDIO_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 17',
 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf',
 'Testes e manutenção conforme RT e contrato.'),

-- =========================
-- EXTINTORES (IT 16)
-- =========================
('EXTINTORES_SISTEMA_PROTECAO', 'ANUAL', 0, 0, 'CBMMG IT 16',
 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_16_2a_ed_portaria_17_2014.pdf',
 'Manutenção conforme normas ABNT de extintores.'),

-- =========================
-- SAÍDAS / SINALIZAÇÃO / PORTAS
-- =========================
('SAIDAS_EMERGENCIA_ROTAS', 'ANUAL', 0, 0, 'CBMMG IT',
 'https://bombeiros.mg.gov.br/normastecnicas',
 'Requisitos de saídas e rotas conforme PSCIP.'),

('SINALIZACAO_EMERGENCIA', 'ANUAL', 0, 0, 'CBMMG IT',
 'https://bombeiros.mg.gov.br/normastecnicas',
 'Verificação e reposição conforme uso.'),

('PORTAS_CORTA_FOGO', 'ANUAL', 0, 0, 'CBMMG IT',
 'https://bombeiros.mg.gov.br/normastecnicas',
 'Inspeções e ajustes conforme PSCIP.'),

-- =========================
-- NR / DEMAIS ITENS NORMATIVOS
-- =========================
('NR10_TREINAMENTO', 'ANUAL', 0, 0, 'NR-10 (MTE)', '', 'Treinamento conforme NR-10.'),
('NR13_CALDEIRAS_INSPECAO', 'ANUAL', 0, 0, 'NR-13 (MTE)', '', 'Inspeções conforme NR-13.'),
('NR23_PROTECAO_INCENDIO', 'ANUAL', 0, 0, 'NR-23 (MTE)', '', 'Proteção contra incêndio conforme NR-23.'),
('NR35_TREINAMENTO', 'ANUAL', 0, 0, 'NR-35 (MTE)', '', 'Treinamento conforme NR-35.'),

('ALARME_INCENDIO_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT',
 'https://bombeiros.mg.gov.br/normastecnicas',
 'Sistema de alarme conforme IT e ABNT correlatas.'),

('SPRINKLERS_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT',
 'https://bombeiros.mg.gov.br/normastecnicas',
 'Sistema de sprinklers conforme PSCIP e normas.');
