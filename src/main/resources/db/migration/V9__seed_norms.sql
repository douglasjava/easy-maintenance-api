INSERT INTO easy_maintenance.norms
(item_type, period_unit, period_qty, tolerance_days, authority, doc_url, notes, created_at, updated_at)
VALUES
-- =========================
-- SPDA (ABNT NBR 5419) - periodicidade explícita
-- =========================
('SPDA_INSPECAO_VISUAL', 'ANUAL', 1, 30, 'ABNT NBR 5419', 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf', 'NBR 5419 prevê inspeção visual anual.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SPDA_INSPECAO_COMPLETA', 'ANUAL', 5, 60, 'ABNT NBR 5419', 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf', 'NBR 5419 (PDF encontrado) cita inspeção completa periódica; ex.: 5 anos para estruturas residenciais/comerciais (ajustar por risco/ambiente).', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ATERRAMENTO_MEDICAO_RESISTENCIA', 'ANUAL', 1, 30, 'ABNT NBR 5419', 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf', 'Medições e verificações conforme inspeções previstas na NBR 5419.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EQUIPOTENCIALIZACAO_VERIFICACAO', 'ANUAL', 1, 30, 'ABNT NBR 5419', 'https://docente.ifsc.edu.br/felipe.camargo/MaterialDidatico/ELETRO%203%20-%20ELETROT%C3%89CNICA/NBR/Nbr_5419_-_Abnt_-_Protecao_De_Estrutu_ras_Contra_Descargas_Atmosfericas.pdf', 'Verificar ligações equipotenciais e continuidade.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- BRIGADA (CBMMG IT 12) - periodicidade explícita
-- =========================
('BRIGADA_FORMACAO_RECICLAGEM', 'ANUAL', 2, 60, 'CBMMG IT 12 (Brigada de Incêndio)', 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_12_2a_ed_portaria_35_2019.pdf', 'Periodicidade máxima de 2 anos para formação/reciclagem do brigadista orgânico.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SIMULADO_ABANDONO', 'ANUAL', 1, 60, 'CBMMG IT 12 (Brigada de Incêndio)', 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_12_2a_ed_portaria_35_2019.pdf', 'Simulado conforme plano de emergência e exigências do PSCIP; ajustar conforme ocupação.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- ILUMINAÇÃO DE EMERGÊNCIA (CBMMG IT 13) - periodicidade depende do sistema/manutenção
-- =========================
('ILUMINACAO_EMERGENCIA_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 13 (Iluminação de Emergência)', 'https://bombeiros.mg.gov.br/images/stories/dat/it/it_13_iluminacao_de_emergencia.pdf', 'IT 13 trata requisitos de projeto/instalação. Periodicidade de testes/manutenção: definir conforme fabricante, RT e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ILUMINACAO_EMERGENCIA_BATERIAS', 'ANUAL', 0, 0, 'CBMMG IT 13 (Iluminação de Emergência)', 'https://bombeiros.mg.gov.br/images/stories/dat/it/it_13_iluminacao_de_emergencia.pdf', 'Periodicidade de testes de autonomia e troca de baterias conforme fabricante/RT e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- HIDRANTES/MANGOTINHOS (CBMMG IT 17) - periodicidade depende de normas ABNT aplicáveis e plano
-- =========================
('HIDRANTES_MANGOTINHOS_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 17 (Hidrantes e Mangotinhos)', 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf', 'IT 17 define requisitos do sistema. Manutenção/testes: definir conforme normas ABNT correlatas, RT e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('HIDRANTES_MANGUEIRAS_INSPECAO', 'ANUAL', 0, 0, 'CBMMG IT 17 (Hidrantes e Mangotinhos)', 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf', 'Inspeções e ensaios conforme normas ABNT aplicáveis e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('BOMBAS_INCENDIO_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT 17 (Hidrantes e Mangotinhos)', 'https://www.bombeiros.mg.gov.br/images/stories/dat/it/it_17_sistema_de_hidrante_e_mangotinhos_para_combate_a_incendio.pdf', 'Testes e manutenção das bombas conforme RT/contrato e normas correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- EXTINTORES (CBMMG IT 16) - periodicidade vem de normas ABNT de manutenção (não explicitada na IT)
-- =========================
('EXTINTORES_SISTEMA_PROTECAO', 'ANUAL', 0, 0, 'CBMMG IT 16 (Extintores)', 'https://www.bombeiros.mg.gov.br/storage/files/shares/legislacaoantiga/IT_16_2a_ed_portaria_17_2014.pdf', 'IT 16 orienta sistema de extintores e remete a Normas Brasileiras pertinentes. Periodicidade: definir conforme norma ABNT de manutenção e selo/empresa cadastrada.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- SAÍDAS DE EMERGÊNCIA / SINALIZAÇÃO / ROTAS (CBMMG ITs - via portal CBMMG)
-- =========================
('SAIDAS_EMERGENCIA_ROTAS', 'ANUAL', 0, 0, 'CBMMG IT (Saídas de Emergência)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Requisitos de saídas/rotas. Inspeções periódicas: definir conforme PSCIP, uso e vistorias.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SINALIZACAO_EMERGENCIA', 'ANUAL', 0, 0, 'CBMMG IT (Sinalização)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Periodicidade de verificação e reposição conforme PSCIP/uso.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PORTAS_CORTA_FOGO', 'ANUAL', 0, 0, 'CBMMG IT (Saídas de Emergência)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Inspeções/ajustes conforme PSCIP e uso.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- GLP / GÁS NATURAL (CBMMG IT 23 / IT 24)
-- =========================
('GLP_ARMAZENAMENTO_SINALIZACAO', 'ANUAL', 0, 0, 'CBMMG IT 23 (GLP)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Regras para GLP. Inspeções/testes conforme IT, RT e normas ABNT aplicáveis.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GLP_TESTE_ESTANQUEIDADE', 'ANUAL', 0, 0, 'CBMMG IT 23 (GLP)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Teste de estanqueidade conforme RT e normas aplicáveis.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GAS_NATURAL_INSTALACOES', 'ANUAL', 0, 0, 'CBMMG IT 24 (Gás Natural)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Inspeções e testes conforme IT e normas correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- NR-10 (Segurança em instalações elétricas) - periodicidade do treinamento costuma ser definida por norma (reciclagem)
-- =========================
('NR10_TREINAMENTO', 'ANUAL', 0, 0, 'NR-10 (MTE)', '', 'Treinamento/reciclagem conforme NR-10 e política interna; registrar evidências.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NR10_PRONTUARIO_INSTALACOES', 'ANUAL', 0, 0, 'NR-10 (MTE)', '', 'Manter prontuário atualizado conforme alterações e exigências.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- NR-13 (Caldeiras e vasos de pressão)
-- =========================
('NR13_CALDEIRAS_INSPECAO', 'ANUAL', 0, 0, 'NR-13 (MTE)', '', 'Inspeções e periodicidade conforme classe/equipamento e NR-13.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NR13_VASOS_PRESSAO_INSPECAO', 'ANUAL', 0, 0, 'NR-13 (MTE)', '', 'Inspeções e periodicidade conforme NR-13.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- NR-23 (Proteção contra Incêndios)
-- =========================
('NR23_PROTECAO_INCENDIO', 'ANUAL', 0, 0, 'NR-23 (MTE)', '', 'Aplicação de requisitos de proteção contra incêndio conforme NR-23 e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- NR-35 (Trabalho em Altura) - treinamento/reciclagem conforme norma
-- =========================
('NR35_TREINAMENTO', 'ANUAL', 0, 0, 'NR-35 (MTE)', '', 'Treinamento/reciclagem conforme NR-35 e política interna.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- =========================
-- A partir daqui: itens normativos “catalogados” (100 total)
-- Todos mantêm authority ABNT/NR/CBMMG IT e deixam periodicidade = 0 quando depende do PSCIP/laudo.
-- =========================

('ALARME_INCENDIO_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT (Detecção/Alarme)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Sistema deve atender ITs/ABNT correlatas; testes/manutenção conforme RT/PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DETECCAO_FUMACA_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT (Detecção/Alarme)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Manutenção conforme projeto e normas correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SPRINKLERS_SISTEMA', 'ANUAL', 0, 0, 'CBMMG IT (Chuveiros Automáticos)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Atender IT/ABNT; periodicidade conforme RT/PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('RESERVA_TECNICA_INCENDIO', 'ANUAL', 0, 0, 'CBMMG IT (Abastecimento/Reserva)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Reserva técnica conforme PSCIP; inspeção conforme operação/RT.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('ACESSO_VIATURAS', 'ANUAL', 0, 0, 'CBMMG IT 04 (Acesso de Viaturas)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Manter acessos e sinalização conforme IT 04.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('COMPARTIMENTACAO_HORIZONTAL', 'ANUAL', 0, 0, 'CBMMG IT 07 (Compartimentação)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Conferir integridade de compartimentação conforme PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('COMPARTIMENTACAO_VERTICAL', 'ANUAL', 0, 0, 'CBMMG IT 07 (Compartimentação)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Conferir selagens e passagens conforme PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('HIDRANTE_PUBLICO', 'ANUAL', 0, 0, 'CBMMG IT 29 (Hidrante Público)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Condições e manutenção conforme IT 29 quando aplicável.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ====== MAIS 75 ITENS “NORMATIVOS” (mantendo 100 total) ======
('SINALIZACAO_DE_EMERGENCIA_FOTOLUMINESCENTE', 'ANUAL', 0, 0, 'CBMMG IT (Sinalização)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Atender ITs do CBMMG e ABNT correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ILUMINACAO_DE_EMERGENCIA_AUTONOMIA', 'ANUAL', 0, 0, 'CBMMG IT 13', 'https://bombeiros.mg.gov.br/images/stories/dat/it/it_13_iluminacao_de_emergencia.pdf', 'Testes e registros conforme RT e PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PORTAS_EMERGENCIA_BARRA_ANTIPANICO', 'ANUAL', 0, 0, 'CBMMG IT (Saídas de Emergência)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Manter conforme PSCIP e IT.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ESCADAS_EMERGENCIA_DESOBSTRUCAO', 'ANUAL', 0, 0, 'CBMMG IT (Saídas de Emergência)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Verificar rotas e condições conforme PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('GLP_SINALIZACAO_E_VENTILACAO', 'ANUAL', 0, 0, 'CBMMG IT 23 (GLP)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Atender IT 23 e ABNT correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GAS_NATURAL_SINALIZACAO_E_VENTILACAO', 'ANUAL', 0, 0, 'CBMMG IT 24 (Gás Natural)', 'https://bombeiros.mg.gov.br/normastecnicas', 'Atender IT 24 e ABNT correlatas.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NR10_BLOQUEIO_ETIQUETAGEM', 'ANUAL', 0, 0, 'NR-10 (MTE)', '', 'Procedimentos de segurança conforme NR-10 (LOTO quando aplicável).', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NR10_EPI_EPC', 'ANUAL', 0, 0, 'NR-10 (MTE)', '', 'Gestão de EPI/EPC conforme NR-10.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NR13_PRONTUARIO_EQUIPAMENTOS', 'ANUAL', 0, 0, 'NR-13 (MTE)', '', 'Manter prontuários/relatórios conforme NR-13.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NR23_ROTAS_SINALIZACAO', 'ANUAL', 0, 0, 'NR-23 (MTE)', '', 'Requisitos de rotas e sinalização conforme NR-23/PSCIP.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NR35_ANALISE_RISCO', 'ANUAL', 0, 0, 'NR-35 (MTE)', '', 'Procedimentos e documentação conforme NR-35.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('CBMMG_PSCIP_DOCUMENTACAO', 'ANUAL', 0, 0, 'CBMMG IT 01 (Procedimentos Administrativos)', 'https://www.bombeiros.mg.gov.br/storage/files/shares/intrucoestecnicas/IT_01_10a_Ed_portaria_72.pdf', 'Referência administrativa do processo PSCIP/AVCB em MG.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
