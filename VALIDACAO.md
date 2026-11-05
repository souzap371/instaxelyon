# Validação da entrega

- Build Maven: sucesso.
- Sete testes aprovados: normalização de perfil, rejeição de endereços inválidos, login obrigatório, renderização do painel e relatório, CSRF e recusa de análise sem evidências.
- Chave OpenAI criada e testada com uma chamada mínima real: HTTP 200.
- Ambiente local usa Java 17, com override de java.version apenas na verificação. pom.xml e Dockerfile entregues usam Java 21. Container Docker/Java 21 não foi executado neste ambiente.
- Consulta Meta não testada: credenciais Meta não fornecidas.
- Não houve teste visual em navegador nem análise ponta a ponta de um perfil real. Layout tem regras responsivas para celular, tablet e desktop.
- O ZIP privado contém compose.yaml com credencial ativa por solicitação do proprietário. Não publique ou compartilhe o ZIP.
