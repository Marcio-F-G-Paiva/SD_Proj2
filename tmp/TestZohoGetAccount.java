package sd2526.trab.impl.external;

import sd2526.trab.impl.external.zoho.msgs.ZohoAccount;

/**
 * Teste manual para verificar que o getAccount() está a funcionar corretamente.
 *
 * Como correr (a partir da raiz do projeto, após mvn package):
 * java -cp target/sd2526-tp1-1.jar sd2526.trab.external.impl.zoho.TestZohoGetAccount
 */
public class TestZohoGetAccount {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║        TESTE: Zoho getAccount()              ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        int passed = 0;
        int failed = 0;

        // ── Passo 1: Instância singleton ──────────────────────────────────
        System.out.println("\n[1/4] A criar instância Zoho...");
        Zoho zoho;
        try {
            zoho = Zoho.getInstance();
            System.out.println("  ✅ Instância criada.");
            passed++;
        } catch (Exception e) {
            System.err.println("  ❌ Falha ao criar instância: " + e.getMessage());
            e.printStackTrace();
            printSummary(passed, ++failed);
            return; // Sem instância, não vale a pena continuar
        }

        // ── Passo 2: Obter token OAuth ────────────────────────────────────
        System.out.println("\n[2/4] A obter access token OAuth2...");
        String token;
        try {
            token = zoho.tokenManager.getValidAccessToken();
            if (token == null || token.isBlank())
                throw new RuntimeException("Token retornado é nulo ou vazio.");
            System.out.println("  ✅ Token obtido com sucesso.");
            System.out.println(
                    "  Token (primeiros 20 chars): " + token.substring(0, Math.min(20, token.length())) + "...");
            passed++;
        } catch (Exception e) {
            System.err.println("  ❌ Falha ao obter token: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("PKIX"))
                System.err.println(
                        "  DICA: Erro de certificado TLS. Verifica o truststore com -Djavax.net.ssl.trustStore.");
            e.printStackTrace();
            printSummary(passed, ++failed);
            return;
        }

        // ── Passo 3: Chamar getAccount() ──────────────────────────────────
        System.out.println("\n[3/4] A chamar getAccount()...");
        ZohoAccount account;
        try {
            account = zoho.getAccount();

            if (account == null)
                throw new RuntimeException("getAccount() retornou null (resposta vazia ou sem contas).");

            System.out.println("  ✅ Conta obtida com sucesso!");
            System.out.println();
            System.out.println("  ┌─── Detalhes da Conta ───────────────────────");
            System.out.printf("  │  accountId          : %s%n", account.accountId());
            System.out.printf("  │  accountName        : %s%n", account.accountName());
            System.out.printf("  │  displayName        : %s%n", account.displayName());
            System.out.printf("  │  accountDisplayName : %s%n", account.accountDisplayName());
            System.out.printf("  │  primaryEmailAddress: %s%n", account.primaryEmailAddress());
            System.out.printf("  │  mailboxAddress     : %s%n", account.mailboxAddress());
            System.out.printf("  │  incomingUserName   : %s%n", account.incomingUserName());
            System.out.printf("  │  firstName          : %s%n", account.firstName());
            System.out.printf("  │  role               : %s%n", account.role());
            System.out.printf("  │  enabled            : %s%n", account.enabled());
            System.out.println("  └─────────────────────────────────────────────");
            passed++;
        } catch (Exception e) {
            System.err.println("  ❌ Falha em getAccount(): " + e.getMessage());
            e.printStackTrace();
            printSummary(passed, ++failed);
            return;
        }

        // ── Passo 4: Validar campos críticos ──────────────────────────────
        System.out.println("\n[4/4] A validar campos críticos...");

        // accountId não pode ser nulo — é usado em todas as chamadas seguintes
        if (account.accountId() == null || account.accountId().isBlank()) {
            System.err.println("  ❌ accountId é nulo ou vazio! As chamadas à API vão falhar.");
            failed++;
        } else {
            System.out.println("  ✅ accountId presente: " + account.accountId());
            passed++;
        }

        // primaryEmailAddress — necessário para o fromAddress no sendEmail
        if (account.primaryEmailAddress() == null || account.primaryEmailAddress().isBlank()) {
            System.err.println("  ⚠️  primaryEmailAddress é nulo. Usa este valor como fromAddress no postMessage!");
            // Não é fatal — aviso apenas
        } else {
            System.out.println("  ✅ primaryEmailAddress: " + account.primaryEmailAddress());
            System.out.println("  ℹ️  Usa este endereço como ZOHO_FROM_ADDRESS em ZohoMessagesImpl.");
        }
        // Conta tem de estar ativa
        if (!account.enabled()) {
            System.err.println("  ❌ A conta está DESATIVADA (enabled=false). Envios vão falhar.");
            failed++;
        } else {
            System.out.println("  ✅ Conta está ativa (enabled=true).");
            passed++;
        }

        printSummary(passed, failed);
    }

    private static void printSummary(int passed, int failed) {
        System.out.println();
        System.out.println("══════════════════════════════════════════════");
        System.out.printf("  Resultado: %d passaram  |  %d falharam%n", passed, failed);
        if (failed == 0)
            System.out.println("  ✅ TUDO OK — getAccount() está funcional.");
        else
            System.out.println("  ❌ HÁ ERROS — vê as mensagens acima.");
        System.out.println("══════════════════════════════════════════════");
    }
}