Descrição:
Este projeto tem como objetivo o desenvolvimento de um sistema diostribuido para gestão e realização de perguntas de escolha múltipla, com suporte a dois tipos de users:
 	- Docente -> Cria e gere perguntas de escolha múltipla, visualiza respostas do estudantes e exporta resultados
	- Estudante -> Responde a perguntas ativas e consulta o histórico de respostas

O sistema é constituído por três aplicações distintas que comunicam entre si em rede:
1. Serviço de Diretoria
	Responsável por manter uma lista de servidores ativos e fornecer o endereço do servidor principal aos clientes
Comunicação via UDP

2. Servidor
	Implementa a lógica de negócio e acede a uma base de dados SQLite local para guardar utilizadores, perguntas e respostas
Comunicação com clientes via TCP e sincronização entre servidores multicast

3. Cliente
	Aplicação em modo consola que permite a interação dos utilizadores (docente ou estudantes) com o sistema

Estrutura do sistema:
/src
	-common/	# Classes e objetos partilhados (mensagem, modelos)
	-directory/	# Serviço de diretoria (UDP)
	-server/	# Servidor Principal (TCP + SQLite)
	-client/	# Aplicação cliente (modo consola)
Cada componenter é executado de forma independente e comunuica através de Sockets

Comunicação entre Componentes
Cliente<->Diretoria	UDP	Cliente solicita o endereço do Servidor Principal
Servidor<->Diretoria	UDP	Servidores regiustam-se e enviam heartbeats
Cliente<->Servidor	TCP	Operações de registo, autenticação e perguntas/respostas
Servidor<->Servidor	Multicast	Sincronização e propagação de atualizações

Base de Dados (SQLite)
Cada servidor possui uma base de dados local com as seguintes tabelas
-docente	(id, nome, email, password)
-estudante	(id, numero, nome, email, password)
-pergunta	(id, docente_id, enunciado, opcao_certa, data_inicio, data fim, codigo)
-opção		(id, perghunta_id, código, texto)
-resposta	(id, pergunta_id, estudante_id, resposta, data_submissao)

Funcionalidades Principais
Docente:
	- Registar-se e autenticar-se
	- Criar e gerir perguntas (enunciado, opções, resposa correta, período de validade)
	- Consultar perguntas criadas
	- Exportar resultados de perguntas expiradas para CSV

Estudante:
	- Registar-se e Autenticar-se
	- Introduzir o código de pergunta e responder
	- Consultar respostas submetids e respetivo estado(certo/errado)

Tecnologias utilizadas:
	-Java 17+
	-Sockets TCP/UDP/MULTICAST
	-SQLite (via JDBC)
	-Threads e programação concorrente
	- CSV (FileWriter/PrintWriter)

Camada				Descrição
View (Cliente)			Interface de consola para interação com o utilizador
Lógica de Negócio (Servidor)	Gestão de utilizadores, perguntas e respostas
Comunicação (TCP/UDP)		Envio e receção de mensagens entre componentes
Persistência (SQLite)		Armazenamento local de dados no servidor


Autoria
José Francisco Spínola Marques Tôco a2023141186
Duarte Machado Gois - a2022136610
David Lopes dos Santos 2022135712


Projeto académico desenvolvido no âmbito da unidade curricular Programação Distribuída (DEIS/ISEC) — 2025/2026.
