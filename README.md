# 🧠 Betzin - Sistema de Apostas Esportivas em Clojure

Projeto acadêmico desenvolvido na disciplina **Programação Funcional (T300)**, utilizando **Clojure** para criar uma **API REST** robusta e uma **interface de terminal** interativa que simula apostas esportivas em futebol e basquete.

---

## 🎯 Objetivo

O sistema tem como foco aplicar os conceitos de programação funcional em um cenário prático: apostas esportivas. Foram desenvolvidas tanto a **camada de backend** (API REST) quanto a **interface de usuário** (terminal interativo), com persistência de dados local e simulação de partidas esportivas.

---

## 🧩 Estrutura do Projeto

- `betzin/` → Backend (API REST em Clojure com Compojure)
- `betzin-interface/` → Interface de terminal (Clojure + clj-http)
- `.gitignore` → Arquivos locais e dados de simulação são ignorados pelo Git

---

## 🌐 Uso de API Externa

Durante o desenvolvimento, foi utilizada a **API de odds da RapidAPI (odds-api1.p.rapidapi.com)** para obter dados reais de partidas e probabilidades.  
No entanto, **para fins acadêmicos e autonomia do projeto**, os dados foram convertidos para **arquivos `.edn` locais**, eliminando a dependência de conexão com a API externa.

O modo de uso com API pode ser reativado alterando a flag:
```clojure
(def usar-api? false) ; Altere para true para utilizar a API real
```

---

## 🔍 Funcionalidades principais

### Interface (Terminal)
- Gerenciamento de conta (depósito e consulta de saldo)
- Realização de apostas em futebol e basquete
- Verificação de resultados com base em scores simulados
- Visualização de todas as apostas feitas
- Modo administrador (resetar saldo e limpar apostas)

### API (Servidor)
- Endpoints REST para cadastro e consulta de apostas
- Atualização e leitura de saldo
- Listagem de partidas e odds (futebol e basquete)
- Cálculo automático dos resultados das apostas
- Dados persistidos em arquivos `.edn`

---

## 📝 Nota Acadêmica

Este projeto foi avaliado na **AV3 (Entrega de Projeto)** da disciplina **Programação Funcional (T300)**.  
📌 Obteve **nota 9.0**, destacando-se por seu funcionamento completo, organização modular e aplicação correta dos paradigmas funcionais.

---