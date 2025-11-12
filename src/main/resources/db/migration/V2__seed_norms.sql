-- V2: Normas em PT-BR com fontes brasileiras (ABNT / ANVISA / CBM / Vigilância)
INSERT INTO norms (item_type, period_unit, period_qty, tolerance_days, authority, doc_url, notes)
VALUES
    ('EXTINTOR', 'MESES', 12, 30,
     'ABNT / Corpo de Bombeiros',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-12962-2021',
     'Inspeção e recarga anual obrigatória. Conferir validade do cilindro e lacres.'),

    ('SPDA', 'MESES', 12, 30,
     'ABNT NBR 5419',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-5419-2015',
     'Sistema de proteção contra descargas atmosféricas. Laudo anual obrigatório.'),

    ('CAIXA_DAGUA', 'MESES', 6, 15,
     'Vigilância Sanitária',
     'https://www.gov.br/anvisa/pt-br',
     'Limpeza e desinfecção da caixa d’água a cada 6 meses. Emitir certificado.'),

    ('ILUMINACAO_EMERGENCIA', 'MESES', 12, 30,
     'ABNT NBR 10898',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-10898-2013',
     'Sistema de iluminação de emergência. Teste e laudo anual.'),

    ('HIDRANTE', 'MESES', 12, 30,
     'ABNT / Corpo de Bombeiros',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-13714-2021',
     'Inspeção do sistema de hidrantes e bomba de recalque. Laudo anual.'),

    ('AR_CONDICIONADO', 'MESES', 6, 15,
     'ANVISA RE 09 / Qualidade do Ar Interno',
     'https://pesquisa.in.gov.br/imprensa/jsp/visualiza/index.jsp?data=16/01/2003&jornal=1&pagina=66&totalArquivos=92',
     'Manutenção preventiva e higienização. Laudo de qualidade do ar regulamentado pela RE 09.'),

    ('MANGUEIRA_DE_INCENDIO', 'MESES', 12, 30,
     'ABNT NBR 11861',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-11861-1998',
     'Mangueiras devem ser testadas e certificadas anualmente.'),

    ('ALARME_DE_INCENDIO', 'MESES', 12, 30,
     'ABNT NBR 17240',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-17240-2020',
     'Sistema de alarme e detecção de incêndio. Teste e laudo anual.'),

    ('BOTOEIRA_DE_INCENDIO', 'MESES', 12, 30,
     'ABNT NBR 17240',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-17240-2020',
     'Botões de acionamento manual devem ser inspecionados e testados uma vez ao ano.'),

    ('PORTA_CORTA_FOGO', 'MESES', 12, 30,
     'ABNT NBR 11742',
     'https://biblioteca.abnt.org.br/norma/abnt-nbr-11742-2018',
     'Portas corta-fogo devem ser ensaiadas periodicamente e manter integridade e vedação.'),

    ('AUTOMACAO_BOMBEIRO', 'MESES', 12, 30,
     'Corpo de Bombeiros / ABNT',
     'https://www.cbm.mg.gov.br',
     'Painéis e sistemas integrados de prevenção. Laudo anual.'),

    ('GERADOR', 'MESES', 6, 15,
     'Fabricante + Recomendação ABNT',
     NULL,
     'Manutenção preventiva, troca de óleo e testes sob carga. Registrar horas de funcionamento.');