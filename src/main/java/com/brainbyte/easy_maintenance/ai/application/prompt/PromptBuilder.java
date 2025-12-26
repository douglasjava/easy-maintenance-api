package com.brainbyte.easy_maintenance.ai.application.prompt;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PromptBuilder {

  public static String beautifySummaryPrompt(long total, long ok, long nearDue, long overdue) {
    return "Resuma em 2 linhas e em PT-BR, de forma clara e objetiva, o status das manutenções: " +
            "total=" + total + ", ok=" + ok + ", perto_do_vencimento=" + nearDue + ", vencidas=" + overdue + ". " +
            "Seja direto, sem floreios, e não invente dados.";
  }

  public static String assistantPrompt(String question, String context) {
    return """
            Você é um assistente de manutenção preventiva.
            Responda em PT-BR, curto e prático (máx 8 linhas).
            Use o contexto abaixo. Se faltar dado, diga exatamente qual.
            Contexto:
            %s
            
            Pergunta:
            %s
            """.formatted(context, question);

  }

  public static String suggestItemPrompt(String description, String contextItemType) {
    String ctx = contextItemType != null && !contextItemType.isBlank() ? " ItemType contexto: " + contextItemType + "." : "";
    return "Sugira o cadastro de um item de manutenção a partir desta descrição (PT-BR): '" + description + "'." + ctx +
            "\nResponda somente em JSON com as chaves: {\"itemType\": string, \"itemCategory\": one of [REGULATORY, OPERATIONAL], \"customPeriodUnit\": one of [DIAS, MESES], \"customPeriodQty\": number, \"tags\": [string,string]}. " +
            "Não inclua explicações fora do JSON.";
  }
}
