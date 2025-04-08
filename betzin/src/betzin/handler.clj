(ns betzin.handler
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.util.response :as response]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; Configurações da API
(def usar-api? false)
(def odds-api-key "2376fa6ef6mshafb77a11478dfd0p1117d2jsn78f2ebf92b69")
(def odds-api-host "odds-api1.p.rapidapi.com")

;; Configuração para persistência
(def conta (atom {:saldo 0})) 
(def apostas (atom []))      

;; Funções de Persistência
(defn salvar-conta []
  (spit "conta.edn" (pr-str @conta)))

(defn salvar-apostas []
  (spit "apostas.edn" (pr-str @apostas)))

(defn carregar-conta []
  (if (.exists (io/file "conta.edn"))
    (reset! conta (edn/read-string (slurp "conta.edn")))
    (reset! conta {:saldo 0})))

(defn carregar-apostas []
  (let [arquivo (io/file "apostas.edn")]
    (if (.exists arquivo)
      (reset! apostas (edn/read-string (slurp arquivo)))
      (do
        (reset! apostas [])
        (spit arquivo "[]"))))
        (println "Lendo apostas de:" (.getAbsolutePath (io/file "apostas.edn"))))


(defn inicializar-dados []
  (carregar-conta)
  (carregar-apostas))

(inicializar-dados)

;; Funções principais
(defn depositar [valor]
  (swap! conta update :saldo + valor)
  (salvar-conta))

(defn obter-saldo [] (:saldo @conta))

(defn remover-dinheiro [valor]
  (if (<= valor (:saldo @conta))
    (do (swap! conta update :saldo - valor)
        (salvar-conta))
    (throw (ex-info "Saldo insuficiente" {:saldo (:saldo @conta)}))))

(defn registrar-aposta [aposta]
  (swap! apostas conj aposta)
  (salvar-apostas))

;; Busca de dados (modo híbrido)
(defn filtrar-eventos [eventos]
  (->> eventos
       (map (fn [[_k evento]]
              {:eventId (:eventId evento)
               :participante1 (:participant1 evento)
               :participante2 (:participant2 evento)}))
       (filter #(and (:participante1 %) (:participante2 %)))))

(defn buscar-jogos-futebol []
  (if usar-api?
    (try
      (let [response (client/get "https://odds-api1.p.rapidapi.com/events"
                                 {:headers {:x-rapidapi-key odds-api-key
                                            :x-rapidapi-host odds-api-host}
                                  :query-params {:tournamentId "325" :media "false"}})
            body (json/parse-string (:body response) true)
            eventos (filtrar-eventos (:events body))]
        (response/response eventos))
      (catch Exception e
        (-> (response/response {:error "Erro na API externa"}) (response/status 500))))
    (response/response (edn/read-string (slurp "dados-futebol.edn")))))

(defn buscar-odds-futebol [event-id]
  (if usar-api?
    (try
      (let [response (client/get "https://odds-api1.p.rapidapi.com/odds"
                                 {:headers {:x-rapidapi-key odds-api-key
                                            :x-rapidapi-host odds-api-host}
                                  :query-params {:eventId event-id :bookmakers "bet365" :oddsFormat "decimal" :raw "false"}})
            body (json/parse-string (:body response) true)
            mercados (:markets body)]
        (if (seq mercados)
          (response/response (->> mercados
                                  (filter (fn [[_ market]]
                                            (or (= (:marketName market) "Full Time Result")
                                                (and (= (:marketName market) "Over Under Full Time")
                                                     (= (:handicap market) "1.5")))))
                                  (map (fn [[_ market]]
                                         {:marketName (:marketName market)
                                          :handicap (:handicap market)
                                          :outcomes (->> (:outcomes market)
                                                         (map (fn [[_ o]]
                                                                {:outcomeName (:outcomeName o)
                                                                 :price (get-in o [:bookmakers :bet365 :price])})))}))))
          (-> (response/response {:error "Mercados não encontrados"}) (response/status 404))))
      (catch Exception e
        (-> (response/response {:error "Erro na API externa"}) (response/status 500))))
    (let [dados (edn/read-string (slurp "dados-odds-futebol.edn"))
          odds (get dados event-id)]
      (if (seq odds)
        (response/response odds)
        (-> (response/response {:error "Odds não encontradas"}) (response/status 404))))))

(defn buscar-jogos-basquete []
  (if usar-api?
    (try
      (let [response (client/get "https://odds-api1.p.rapidapi.com/events"
                                 {:headers {:x-rapidapi-key odds-api-key
                                            :x-rapidapi-host odds-api-host}
                                  :query-params {:tournamentId "132" :media "false"}})
            body (json/parse-string (:body response) true)
            eventos (filtrar-eventos (:events body))]
        (response/response eventos))
      (catch Exception e
        (-> (response/response {:error "Erro na API externa"}) (response/status 500))))
    (response/response (edn/read-string (slurp "dados-basquete.edn")))))

(defn buscar-odds-basquete [event-id]
  (if usar-api?
    (try
      (let [response (client/get "https://odds-api1.p.rapidapi.com/odds"
                                 {:headers {:x-rapidapi-key odds-api-key
                                            :x-rapidapi-host odds-api-host}
                                  :query-params {:eventId event-id :bookmakers "ladbrokes" :oddsFormat "decimal" :raw "false"}})
            body (json/parse-string (:body response) true)
            mercados (:markets body)]
        (if (seq mercados)
          (response/response (->> mercados
                                  (filter (fn [[_ market]]
                                            (or (= (:marketName market) "Regular Time Result")
                                                (and (= (:marketName market) "Over Under (incl. overtime)")
                                                     (= (:handicap market) "231.5")))))
                                  (map (fn [[_ market]]
                                         {:marketName (:marketName market)
                                          :handicap (:handicap market)
                                          :outcomes (->> (:outcomes market)
                                                         (map (fn [[_ o]]
                                                                {:outcomeName (:outcomeName o)
                                                                 :price (get-in o [:bookmakers :ladbrokes :price])})))}))))
          (-> (response/response {:error "Mercados não encontrados"}) (response/status 404))))
      (catch Exception e
        (-> (response/response {:error "Erro na API externa"}) (response/status 500))))
    (let [dados (edn/read-string (slurp "dados-odds-basquete.edn"))
          odds (get dados event-id)]
      (if (seq odds)
        (response/response odds)
        (-> (response/response {:error "Odds não encontradas"}) (response/status 404))))))

(defn calcular-resultado-aposta [aposta scores]
  (let [market (:mercado aposta)
        aposta-odds (:odds aposta)
        valor-aposta (try (Double/parseDouble (str (:valor aposta))) (catch Exception _ 0.0))
        valor-odds (try (Double/parseDouble (str (:valor-odds aposta))) (catch Exception _ 0.0))
        time1-score (:participant1Score scores)
        time2-score (:participant2Score scores)
        handicap-str (:handicap aposta)
        handicap (if (some? handicap-str)
                   (Double/parseDouble (str handicap-str))
                   0.0)
        media-gols (/ (+ time1-score time2-score) 2.0)]
    (cond
      (= market "Full Time Result")
      (let [resultado (cond
                        (> time1-score time2-score) "1"
                        (= time1-score time2-score) "X"
                        :else "2")]
        (if (= resultado aposta-odds)
          {:vencedor true :ganho (* valor-aposta valor-odds)}
          {:vencedor false :ganho 0.0}))

      (= market "Over Under Full Time")
      (let [resultado (if (< media-gols handicap) "Under" "Over")]
        (if (= resultado aposta-odds)
          {:vencedor true :ganho (* valor-aposta valor-odds)}
          {:vencedor false :ganho 0.0}))

      (= market "Regular Time Result")
      (let [resultado (if (> time1-score time2-score) "1" "2")]
        (if (= resultado aposta-odds)
          {:vencedor true :ganho (* valor-aposta valor-odds)}
          {:vencedor false :ganho 0.0}))

      (= market "Over Under (incl. overtime)")
      (let [resultado (if (< media-gols handicap) "Under" "Over")]
        (if (= resultado aposta-odds)
          {:vencedor true :ganho (* valor-aposta valor-odds)}
          {:vencedor false :ganho 0.0}))

      :else {:vencedor false :ganho 0.0})))



(defn buscar-score [event-id]
  (if usar-api?
    (try
      (let [response (client/get "https://odds-api1.p.rapidapi.com/historical/scores"
                                 {:headers {:x-rapidapi-key odds-api-key
                                            :x-rapidapi-host odds-api-host}
                                  :query-params {:eventIds event-id}})
            body (json/parse-string (:body response) true)]
        (if (seq body)
          (let [event-data (first body)
                scores (:scores event-data)]

            ;; Atualiza apostas com verificação e evita duplicidade de saldo
            (reset! apostas
                    (mapv (fn [aposta]
                            (if (= (:partida aposta) event-id)
                              (let [res (calcular-resultado-aposta aposta scores)]
                                (when (and (:vencedor res) (not (contains? aposta :vencedor)))
                                  (swap! conta update :saldo + (:ganho res))
                                  (salvar-conta))
                                (assoc aposta :vencedor (:vencedor res) :ganho (:ganho res)))
                              aposta))
                          @apostas))
            (salvar-apostas)

            (let [apostas-filtradas (filter #(= (:partida %) event-id) @apostas)]
              (response/response {:eventId event-id :scores scores :apostas apostas-filtradas :saldo-atualizado (obter-saldo)})))
          (-> (response/response {:error "Placar não encontrado"}) (response/status 404))))
      (catch Exception e
        (-> (response/response {:error "Erro ao buscar placar"}) (response/status 500))))
    (let [tipo (if (str/starts-with? event-id "fut") "futebol" "basquete")
          dados (edn/read-string (slurp (str "dados-scores-" tipo ".edn")))
          scores (get dados (str event-id))]
      (if scores
        (do
          (reset! apostas
                  (mapv (fn [aposta]
                          (if (= (:partida aposta) event-id)
                            (let [res (calcular-resultado-aposta aposta scores)]
                              (when (and (:vencedor res) (not (contains? aposta :vencedor)))
                                (swap! conta update :saldo + (:ganho res))
                                (salvar-conta))
                              (assoc aposta :vencedor (:vencedor res) :ganho (:ganho res)))
                            aposta))
                        @apostas))
          (salvar-apostas)
          (let [apostas-filtradas (filter #(= (:partida %) event-id) @apostas)]
            (response/response {:eventId event-id :scores scores :apostas apostas-filtradas :saldo-atualizado (obter-saldo)})))
        (-> (response/response {:error "Placar não encontrado"}) (response/status 404))))))


;; Rotas
(defroutes app-routes
  (POST "/deposito" {:keys [body]}
        (let [valor (:valor body)]
          (if (and valor (pos? valor))
            (do (depositar valor)
                (response/response {:message "Depósito realizado com sucesso" :saldo (obter-saldo)}))
            (-> (response/response {:error "Valor inválido"}) (response/status 400)))))

  (GET "/saldo" []
       (response/response {:saldo (obter-saldo)}))

  (POST "/apostas" {:keys [body]}
        (let [aposta body valor (:valor aposta)]
          (try
            (if (and (:esporte aposta) (:partida aposta) valor (pos? valor))
              (do (remover-dinheiro valor)
                  (registrar-aposta aposta)
                  (response/response {:message "Aposta registrada com sucesso"
                                      :saldo (obter-saldo) :aposta aposta}))
              (-> (response/response {:error "Dados da aposta inválidos."})
                  (response/status 400)))
            (catch Exception e
              (-> (response/response {:error "Saldo insuficiente para realizar a aposta."
                                      :detalhes (.getMessage e)})
                  (response/status 400))))))

  (GET "/apostas" []
       (response/response {:apostas @apostas}))

  (GET "/jogos-futebol" []
       (buscar-jogos-futebol))

  (GET "/jogos-futebol-odds/:eventId" [eventId]
       (buscar-odds-futebol eventId))

  (GET "/jogos-basquete" []
       (buscar-jogos-basquete))

  (GET "/jogos-basquete-odds/:eventId" [eventId]
       (buscar-odds-basquete eventId))

  (GET "/score/:eventId" [eventId]
       (buscar-score eventId))

  (POST "/admin/limpar-apostas" []
    (do
      (reset! apostas [])
      (salvar-apostas)
      (response/response {:message "Apostas limpas com sucesso!"})))

  (POST "/admin/zerar-saldo" []
    (do
      (reset! conta {:saldo 0})
      (salvar-conta)
      (response/response {:message "Saldo zerado com sucesso!"})))


  (route/not-found (-> (response/response {:error "Rota não encontrada"}) (response/status 404))))

;; Middleware
(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      wrap-json-response))
