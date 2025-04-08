(ns betzin-interface.core
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

;; Funções auxiliares
(defn listar-opcoes [titulo opcoes]
  (println titulo)
  (doseq [[idx opcao] (map-indexed vector opcoes)]
    (println (str (inc idx) ". " opcao)))
  (let [escolha (read-line)
        escolha-int (try (Integer/parseInt escolha) (catch Exception _ nil))]
    (if (and escolha-int (<= escolha-int (count opcoes)) (pos? escolha-int))
      (nth opcoes (dec escolha-int))
      (do
        (println "Opção inválida. Tente novamente.")
        (listar-opcoes titulo opcoes)))))

(defn listar-partidas [partidas]
  (println "Selecione uma partida:")
  (doseq [[idx partida] (map-indexed vector partidas)]
    (println (str (inc idx) ". " (:participante1 partida) " vs " (:participante2 partida))))
  (let [escolha (read-line)
        escolha-int (try (Integer/parseInt escolha) (catch Exception _ nil))]
    (if (and escolha-int (<= escolha-int (count partidas)) (pos? escolha-int))
      (nth partidas (dec escolha-int))
      (do
        (println "Opção inválida. Tente novamente.")
        (listar-partidas partidas)))))



(defn selecionar-mercado [event-id url]
  (try
    (let [response (http/get (str "http://localhost:3000/" url event-id)
                             {:headers {"Content-Type" "application/json"}})]
      (if (= 200 (:status response))
        (let [mercados (json/parse-string (:body response) true)]
          (if (seq mercados)
            (let [mercado-selecionado (listar-opcoes "Selecione um mercado:" (map :marketName mercados))
                  mercado (first (filter #(= (:marketName %) mercado-selecionado) mercados))]
              mercado)
            (do
              (println "Nenhum mercado disponível para esta partida. Retornando ao menu principal...")
              nil)))
        (do
          (println "Erro ao buscar mercados. Retornando ao menu principal...")
          nil)))
    (catch Exception e
      (println "Erro ao buscar mercados: " (.getMessage e) ". Retornando ao menu principal...")
      nil)))

(defn selecionar-odds [mercado]
  (println "Odds disponíveis (Handicap:" (:handicap mercado) "):")
  (doseq [[idx outcome] (map-indexed vector (:outcomes mercado))]
    (println (str (inc idx) ". " (:outcomeName outcome) " - Odds: " (:price outcome))))
  (let [escolha (read-line)
        escolha-int (try (Integer/parseInt escolha) (catch Exception _ nil))]
    (if (and escolha-int (<= escolha-int (count (:outcomes mercado))) (pos? escolha-int))
      (nth (:outcomes mercado) (dec escolha-int))
      (do
        (println "Opção inválida.")
        (selecionar-odds mercado)))))

;; Gerenciar conta
(defn realizar-deposito []
  (println "Digite o valor do depósito:")
  (let [entrada (read-line)
        valor (try (Integer/parseInt entrada) (catch Exception _ nil))]
    (if (and valor (pos? valor))
      (let [response (http/post "http://localhost:3000/deposito"
                                {:headers {"Content-Type" "application/json"}
                                 :body (json/generate-string {:valor valor})})]
        (if (= 200 (:status response))
          (let [body (json/parse-string (:body response) true)
                saldo (:saldo body)]
            (println "Depósito realizado com sucesso! Novo saldo: R$" saldo))
          (println "Erro ao processar depósito.")))
      (println "Valor inválido. Tente novamente."))))

(defn verificar-resultados []
  (let [verde "\u001B[32m"
        vermelho "\u001B[31m"
        reset "\u001B[0m"]
    
    ;; Buscar apostas via API
    (let [response (http/get "http://localhost:3000/apostas"
                             {:headers {"Content-Type" "application/json"}})
          body (json/parse-string (:body response) true)
          apostas-disponiveis (:apostas body)]

      (if (empty? apostas-disponiveis)
        (println "Nenhuma aposta registrada.")
        (let [partidas-unicas (distinct (map #(select-keys % [:partida :participante1 :participante2]) apostas-disponiveis))
              opcoes (map-indexed
                       (fn [idx {:keys [partida participante1 participante2]}]
                         (str (inc idx) ". " participante1 " vs " participante2 " (" partida ")"))
                       partidas-unicas)]

          (println "\nSelecione uma partida para verificar:")
          (doseq [linha opcoes] (println linha))

          (let [escolha (read-line)
                idx (try (Integer/parseInt escolha) (catch Exception _ nil))]

            (if (and idx (<= idx (count partidas-unicas)) (> idx 0))
              (let [{event-id :partida} (nth partidas-unicas (dec idx))]

                (try
                  (let [response (http/get (str "http://localhost:3000/score/" event-id)
                                           {:headers {"Content-Type" "application/json"}})]
                    (if (= 200 (:status response))
                      (let [body (json/parse-string (:body response) true)
                            scores (:scores body)
                            apostas (:apostas body)
                            saldo (:saldo-atualizado body)]

                        (println (str "\n" verde "Resultados encontrados para a partida!" reset))
                        (println "---------------------------------------------")
                        (doseq [aposta apostas]
                          (let [{:keys [mercado ganho vencedor]} aposta
                                status (if vencedor
                                         (str verde "Aposta vencedora!" reset)
                                         (str vermelho "Aposta perdida." reset))]
                            (println (format "%-30s -> %s Ganho: R$%.2f"
                                             mercado status (double ganho)))))
                        (println "---------------------------------------------")
                        (println (str "Novo saldo: R$" saldo)))
                      (println "Erro ao buscar resultados da partida.")))
                  (catch Exception e
                    (println "Erro ao buscar resultados da partida: " (.getMessage e)))))
              (println "Opção inválida."))))))))

(defn ver-saldo []
  (let [response (http/get "http://localhost:3000/saldo"
                           {:headers {"Content-Type" "application/json"}})]
    (if (= 200 (:status response))
      (let [body (json/parse-string (:body response) true)
            saldo (:saldo body)]
        (println "Saldo atual: R$" saldo))
      (println "Erro ao consultar saldo."))))


(defn menu-gerenciar-conta []
  (println "____________________________")
  (println "Menu - Gerenciar Conta")
  (println "1. Fazer depósito")
  (println "2. Ver saldo")
  (println "3. Voltar ao menu principal")
  (println "Escolha uma opção:")
  (let [opcao (read-line)]
    (case opcao
      "1" (do (realizar-deposito) (menu-gerenciar-conta))
      "2" (do (ver-saldo) (menu-gerenciar-conta))
      "3" (println "Voltando ao menu principal...")
      (do (println "Opção inválida.") (menu-gerenciar-conta)))))

(defn carregar-dados []
  (try
    (let [response (http/get "http://localhost:3000/apostas"
                             {:headers {"Content-Type" "application/json"}})]
      (if (= 200 (:status response))
        (println "Dados carregados com sucesso!")
        (println "Erro ao carregar dados do servidor.")))
    (catch Exception e
      (println "Erro ao carregar dados: " (.getMessage e)))))


;; Apostas de Futebol
(defn realizar-aposta-futebol []
  (let [response (http/get "http://localhost:3000/jogos-futebol"
                           {:headers {"Content-Type" "application/json"}})]
    (when (= 200 (:status response))
      (let [partidas (json/parse-string (:body response) true)]
        (when (seq partidas)
          (let [selecionada (listar-partidas partidas)
                mercado (selecionar-mercado (:eventId selecionada) "jogos-futebol-odds/")]
            (when mercado
              (println "Você selecionou o mercado:" (:marketName mercado))
              (let [odd (selecionar-odds mercado)]
                (println "Você escolheu:" (:outcomeName odd) "- Odds:" (:price odd))
                (println "Digite o valor da aposta:")
                (let [entrada (read-line)
                      valor (try (Integer/parseInt entrada) (catch Exception _ nil))]
                  (if (and valor (pos? valor))
                    (try
                      (let [resposta (http/post "http://localhost:3000/apostas"
                                                {:headers {"Content-Type" "application/json"}
                                                 :body (json/generate-string
                                                        {:esporte "Futebol"
                                                         :partida (:eventId selecionada)
                                                         :participante1 (:participante1 selecionada)
                                                         :participante2 (:participante2 selecionada)
                                                         :mercado (:marketName mercado)
                                                         :odds (:outcomeName odd) 
                                                         :valor-odds (:price odd) 
                                                         :handicap (:handicap mercado)
                                                         :valor valor})})]
                        (if (= 200 (:status resposta))
                          (println "Aposta registrada com sucesso!")
                          (let [erro (-> resposta :body (json/parse-string true) :error)]
                            (println "Erro ao registrar aposta:" erro))))
                      (catch Exception e
                        (println "Erro inesperado ao registrar aposta: saldo insuficiente ou outro problema.")))
                    (println "Valor inválido. Aposta não realizada.")))))))))))



;; Apostas de Basquete
(defn realizar-aposta-basquete []
  (let [response (http/get "http://localhost:3000/jogos-basquete"
                           {:headers {"Content-Type" "application/json"}})]
    (if (= 200 (:status response))
      (let [partidas (json/parse-string (:body response) true)]
        (if (seq partidas)
          (let [selecionada (listar-partidas partidas)
                mercado (selecionar-mercado (:eventId selecionada) "jogos-basquete-odds/")]
            (if mercado
              (do
                (println "Você selecionou o mercado:" (:marketName mercado))
                (let [odd (selecionar-odds mercado)]
                  (println "Você escolheu:" (:outcomeName odd) "- Odds:" (:price odd))
                  (println "Digite o valor da aposta:")
                  (let [entrada (read-line)
                        valor (try (Integer/parseInt entrada) (catch Exception _ nil))]
                    (if (and valor (pos? valor))
                      (let [response (http/post "http://localhost:3000/apostas"
                                                {:headers {"Content-Type" "application/json"}
                                                 :body (json/generate-string
                                                        {:esporte "Basquete"
                                                         :partida (:eventId selecionada)
                                                         :participante1 (:participante1 selecionada)
                                                         :participante2 (:participante2 selecionada)
                                                         :mercado (:marketName mercado)
                                                         :odds (:outcomeName odd)
                                                         :valor-odds (:price odd) 
                                                         :handicap (:handicap mercado)
                                                         :valor valor})})]
                        (if (= 200 (:status response))
                          (println "Aposta registrada com sucesso!")
                          (println "Erro ao registrar aposta.")))
                      (println "Valor inválido. Aposta não realizada.")))))))
              (println "Nenhum mercado selecionado. Retornando ao menu principal...")))
          (println "Nenhuma partida disponível. Retornando ao menu principal...")))
      (println "Erro ao buscar partidas. Retornando ao menu principal..."))




;; Exibir apostas realizadas


(def verde "\u001B[32m")
(def amarelo "\u001B[33m")
(def reset "\u001B[0m")

(defn exibir-apostas []
  (try
    (let [response (http/get "http://localhost:3000/apostas"
                             {:headers {"Content-Type" "application/json"}})
          apostas (-> response :body (json/parse-string true) :apostas)]
      (if (empty? apostas)
        (println "Nenhuma aposta registrada.")
        (do
          (println "\nApostas realizadas:")
          (println (apply str (repeat 120 "-")))
          (println (format "%-4s %-10s %-35s %-30s %-10s %-8s %-15s"
                           "Nº" "Esporte" "Partida" "Mercado" "Odds" "Valor" "Status"))
          (println (apply str (repeat 120 "-")))
          (doseq [[idx aposta] (map-indexed vector apostas)]
            (let [{:keys [esporte participante1 participante2 mercado odds valor vencedor]} aposta
                  status (if (contains? aposta :vencedor)
                           (str verde "Verificada" reset)
                           (str amarelo "Não verificada" reset))
                  partida-str (str participante1 " vs " participante2)]
              (println (format "%-4d %-10s %-35s %-30s %-10s %-8s %-15s"
                               (inc idx) esporte partida-str mercado odds (str "R$" valor) status))))
          (println (apply str (repeat 120 "-"))))))
    (catch Exception e
      (println "Erro ao buscar apostas: " (.getMessage e)))))


(defn limpar-apostas []
  (let [response (http/post "http://localhost:3000/admin/limpar-apostas"
                            {:headers {"Content-Type" "application/json"}})]
    (if (= 200 (:status response))
      (println "Todas as apostas foram removidas com sucesso!")
      (println "Erro ao tentar limpar apostas."))))

(defn zerar-saldo []
  (let [response (http/post "http://localhost:3000/admin/zerar-saldo"
                            {:headers {"Content-Type" "application/json"}})]
    (if (= 200 (:status response))
      (println "Saldo zerado com sucesso!")
      (println "Erro ao tentar zerar saldo."))))

(defn menu-administrador []
  (println "\n____________________________")
  (println "Menu Administrativo")
  (println "1. Limpar todas as apostas")
  (println "2. Zerar saldo da conta")
  (println "3. Voltar ao menu principal")
  (let [opcao (read-line)]
    (case opcao
      "1" (do (limpar-apostas) (menu-administrador))
      "2" (do (zerar-saldo) (menu-administrador))
      "3" (println "Voltando ao menu principal...")
      (do (println "Opção inválida.") (menu-administrador)))))

;; Realizar aposta
(defn realizar-aposta []
  (println "____________________________")
  (println "Escolha um esporte para apostar:")
  (println "1. Futebol")
  (println "2. Basquete")
  (let [opcao (read-line)]
    (case opcao
      "1" (realizar-aposta-futebol)
      "2" (realizar-aposta-basquete)
      (println "Opção inválida. Tente novamente."))))

;; Menu Principal
(defn menu-principal []
  (println "____________________________")
  (println "Menu Principal")
  (println "1. Gerenciar Conta")
  (println "2. Realizar Aposta")
  (println "3. Exibir Apostas")
  (println "4. Verificar Resultados")
  (println "5. Administrador")
  (println "6. Sair")
  (println "Escolha uma opção:")
  (let [opcao (read-line)]
    (case opcao
    "1" (do (menu-gerenciar-conta) (menu-principal))
    "2" (do (realizar-aposta) (menu-principal))
    "3" (do (exibir-apostas) (menu-principal))
    "4" (do (verificar-resultados) (menu-principal))
    "5" (do (menu-administrador) (menu-principal))
    "6" (println "Saindo...")
      (do (println "Opção inválida.") (menu-principal)))))


;; Função Principal
(defn -main []
  (println "Bem-vindo ao sistema Betzin!")
  (carregar-dados)
  (menu-principal))