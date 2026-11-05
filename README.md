# ELYON Instagram Intelligence

Projeto Java 21 / Spring Boot 3.5.7 com interface responsiva Thymeleaf, autenticação, CSRF, histórico H2 persistido em volume Docker e análise multimodal pela Responses API da OpenAI.

## Executar

Requisitos: Docker com Compose. O compose.yaml local contém a chave criada nesta sessão e é ignorado pelo Git e Docker build. Nunca publique esse arquivo. A entrega privada inclui compose.yaml com a chave solicitada. Não compartilhe esse arquivo nem o ZIP configurado. Para distribuir o código, remova compose.yaml e mantenha apenas compose.example.yaml.

No diretório do projeto:

```bash
read -rs -p 'Senha de acesso (mínimo 12 caracteres): ' APP_PASSWORD
export APP_PASSWORD
docker compose up -d --build
```

Abra http://localhost:8080 e entre com usuário `elyon` e a senha definida. A porta está vinculada a localhost. Para acesso externo, configure proxy reverso com HTTPS, mantendo a aplicação sem exposição direta. A entrega configurada já contém compose.yaml. Ao criar uma cópia sem credenciais, use compose.example.yaml e forneça OPENAI_API_KEY por ambiente seguro.

## Uso real

1. Informe somente o perfil no formato `@usuario`.
2. O sistema pesquisa automaticamente os dados públicos disponíveis e identifica as fontes usadas. Perfis privados ou pouco indexados podem produzir uma análise limitada.
3. O conteúdo é enviado à OpenAI (store=false). Cada análise usa créditos da API; saldo ChatGPT não cobre esse uso.
4. A análise retorna evidências, limitações, recomendações, bio proposta e nove pautas com direção visual.
5. Antes/depois compara a primeira captura com esboço editorial. Não gera logos, fotografias ou artes finais; não altera o Instagram.
6. Use Imprimir / salvar PDF no relatório para exportar pelo navegador.

## Consulta automática

A pesquisa web da OpenAI é usada automaticamente a partir do `@`. Quando houver integração oficial Meta, ela é priorizada para consultar perfis profissionais. Para habilitá-la, configure no ambiente do serviço `app`:

- META_ACCESS_TOKEN: token da sua própria integração Meta com permissões adequadas.
- META_IG_USER_ID: ID da conta profissional usada pela sua integração.
- META_GRAPH_VERSION: versão Graph habilitada no aplicativo (padrão v23.0, alterável).

A implementação usa Business Discovery para buscar dados públicos e até 24 publicações. Sem essas credenciais a aplicação informa a indisponibilidade. Não pede conexão ao perfil pesquisado, mas sua integração precisa estar habilitada. Contas pessoais/privadas e campos não retornados não são preenchidos artificialmente. Seguindo não é solicitado pois não é garantido nesse caminho. Não coleta automaticamente Stories, destaques ou métricas privadas. Nesta versão imagens para avaliação visual precisam ser anexadas, mesmo no modo Meta.

## Limites da versão

- Não possui pesquisa de tendências em tempo real. A IA não deve apresentar suas sugestões como tendências verificadas.
- Processamento síncrono (até 150 segundos por chamada IA); botão evita clique duplicado na mesma página. Escala para agência exige fila persistente, controle de concorrência e cotas.
- Acesso administrativo único; dados e capturas ficam no banco local. Não implementa portal de clientes, exclusão via UI ou gerenciamento de usuários.
- Banco H2 adequado para instalação pequena; produção com vários usuários deve migrar para PostgreSQL e migrations.
- Antes/depois é esboço editorial de conteúdo com cores fixas da ferramenta, não reconstrução fiel do feed nem design final da marca.
- Requisições recusadas ou sem saldo retornam mensagem ao usuário sem expor tokens.

## Desenvolvimento e testes

```bash
mvn test
mvn package
```

Os testes cobrem normalização/validação de perfis, autenticação, CSRF e renderização do painel. O Dockerfile executa testes antes de empacotar e usa Java 21 sem root em execução.
# instaxelyon
