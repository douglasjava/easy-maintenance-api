INSERT INTO ai_prompt_templates
(
    template_key,
    company_type,
    version,
    status,
    system_prompt,
    user_prompt,
    output_schema_json,
    model_name,
    temperature,
    max_tokens,
    created_by
)
VALUES
    (
        'ONBOARDING_BOOTSTRAP',
        'CONDOMINIO',
        1,
        'ACTIVE',
        'Você é um assistente técnico especialista em manutenção predial.
      Siga rigorosamente as normas ABNT, IT do Corpo de Bombeiros e boas práticas.
      Nunca invente normas ou itens que não existam.
      Utilize apenas os tipos de itens permitidos pelo sistema.
      Retorne sempre no formato JSON conforme o schema fornecido.',
        'O usuário está cadastrando um CONDOMÍNIO.
      Gere os cadastros iniciais de manutenção considerando um condomínio residencial padrão.
      Inclua itens como elevador, SPDA, bombas, portões automáticos e sistema de incêndio.
      Defina periodicidades corretas e criticidade.
      Não crie empresas, usuários ou datas executadas.',
        '{
          "company_type": "string",
          "items": [
            {
              "item_type": "string",
              "category": "string",
              "criticality": "string",
              "maintenance": {
                "norm": "string",
                "period_unit": "string",
                "period_qty": "number",
                "tolerance_days": "number",
                "notes": "string"
              }
            }
          ]
        }',
        'gpt-4.1',
        0.2,
        1200,
        'SYSTEM'
    ),

    (
        'ONBOARDING_BOOTSTRAP',
        'HOSPITAL',
        1,
        'ACTIVE',
        'Você é um assistente técnico especialista em manutenção hospitalar.
      Priorize segurança do paciente e continuidade operacional.
      Siga normas técnicas, ABNT e boas práticas hospitalares.
      Nunca invente dados.
      Retorne somente JSON válido.',
        'O usuário está cadastrando um HOSPITAL.
      Gere os cadastros iniciais de manutenção considerando ambiente hospitalar.
      Inclua geradores, gases medicinais, HVAC e elétrica crítica.
      Defina alta criticidade e periodicidades rigorosas.',
        '{
          "company_type": "string",
          "items": []
        }',
        'gpt-4.1',
        0.2,
        1200,
        'SYSTEM'
    ),

    (
        'ONBOARDING_BOOTSTRAP',
        'ESCOLA',
        1,
        'ACTIVE',
        'Você é um assistente técnico especialista em manutenção escolar.
      Priorize segurança de crianças e conformidade legal.
      Utilize apenas itens e normas reconhecidas.
      Retorne somente JSON válido.',
        'O usuário está cadastrando uma ESCOLA.
      Gere os cadastros iniciais de manutenção.
      Inclua AVCB, playground, elétrica e iluminação de emergência.
      Defina periodicidades adequadas.',
        '{
          "company_type": "string",
          "items": []
        }',
        'gpt-4.1',
        0.3,
        1000,
        'SYSTEM'
    ),

    (
        'ONBOARDING_BOOTSTRAP',
        'INDUSTRIA',
        1,
        'ACTIVE',
        'Você é um assistente técnico especialista em manutenção industrial.
      Siga rigorosamente normas NR e ABNT.
      Nunca invente normas ou equipamentos.
      Retorne apenas JSON conforme solicitado.',
        'O usuário está cadastrando uma INDÚSTRIA.
      Gere os cadastros iniciais de manutenção.
      Inclua caldeiras, compressores e equipamentos sujeitos à NR-13.
      Defina criticidade alta e exigências legais.',
        '{
          "company_type": "string",
          "items": []
        }',
        'gpt-4.1',
        0.2,
        1300,
        'SYSTEM'
    ),

    (
        'ONBOARDING_BOOTSTRAP',
        'ESCRITORIO',
        1,
        'ACTIVE',
        'Você é um assistente técnico especialista em manutenção predial corporativa.
      Priorize conforto, segurança e conformidade legal.
      Utilize apenas práticas reconhecidas.
      Retorne apenas JSON válido.',
        'O usuário está cadastrando um ESCRITÓRIO.
      Gere os cadastros iniciais de manutenção.
      Inclua ar-condicionado, elétrica e sistema de incêndio.
      Defina periodicidades preventivas padrão.',
        '{
          "company_type": "string",
          "items": []
        }',
        'gpt-4.1',
        0.3,
        900,
        'SYSTEM'
    );
