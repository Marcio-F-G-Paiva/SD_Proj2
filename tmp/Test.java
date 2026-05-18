package sd2526.trab.impl.external;

import sd2526.trab.impl.external.zoho.msgs.ZohoAccount;

public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  A iniciar Teste Autónomo: Zoho.getAccount()     ");
        System.out.println("==================================================");

        try {
            // 1. Obter a instância única do Zoho (Singleton)
            Zoho zoho = Zoho.getInstance();
            
            // 2. Tentar obter uma credencial/token válido para ver se a autenticação básica passa
            System.out.println("\n[PASSO 1] A testar a obtenção do Access Token...");
            String token = zoho.getAccessToken();
            if (token != null && !token.isEmpty()) {
                System.out.println("-> Sucesso! Token obtido (truncado): " + token.substring(0, 15) + "...");
            } else {
                System.out.println("-> Falha: O token retornado está vazio ou nulo.");
            }

            // 3. Chamar o método getAccount() que faz o pedido HTTP real à API do Zoho
            System.out.println("\n[PASSO 2] A efetuar pedido a " + Zoho.MAIL_API_BASE + "/accounts ...");
            ZohoAccount account = zoho.getAccount();

            // 4. Validar o resultado
            System.out.println("\n==================== RESULTADO ====================");
            if (account != null) {
                System.out.println("   ✓ TESTE PASSOU: Conta Zoho obtida com sucesso!");
                System.out.println("--------------------------------------------------");
                System.out.println("   Account ID:   " + account.accountId());
                System.out.println("   Mail Address: " + account.mailAddress());
                // Adiciona outros campos do teu record/classe ZohoAccount se necessário, ex:
                // System.out.println("   Display Name: " + account.displayName());
            } else {
                System.err.println("   ✕ TESTE FALHOU: O método retornou null.");
                System.err.println("   Verifica os logs acima para ver se houve erro 401/404 da API.");
            }
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("\n[ERRO CRÍTICO] Ocorreu uma exceção inesperada durante o teste:");
            e.printStackTrace();
        }
    }
}