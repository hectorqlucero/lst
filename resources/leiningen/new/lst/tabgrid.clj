(ns {{name}}.models.tabgrid
  "Tabbed interface helpers for parent/child grids.
   Generic `build-tabs` accepts optional subgrid descriptors as varargs."
  (:require
   [clojure.string :as str]
   [cheshire.core :as json]
   [{{name}}.models.grid :refer [build-grid build-grid-with-custom-new]]
   [{{name}}.models.crud :refer [Query]]))

(defn- safe-id [s]
  (-> (or s "")
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"-+" "-")
      (str/replace #"(^-|-$)" "")))

(defn- parent-id [row]
  (when (map? row)
    (or (:contacto_id row) (:contacto-id row) (:id row) (some-> (keys row) first))))

(defn- fields-map [dbfields labels]
  (if (and (seq dbfields) (seq labels))
    (apply array-map (interleave dbfields labels))
    {}))

(def ^:private onclick-fn
  "event.preventDefault(); (function(){var id=this.dataset.target; localStorage.setItem('active-tab', id); document.querySelectorAll('.nav-tabs .nav-link').forEach(function(x){x.classList.remove('active');});this.classList.add('active');document.querySelectorAll('.tab-pane').forEach(function(p){p.classList.remove('show','active');});var t=document.getElementById(id); if(t){t.classList.add('show','active');} if(history && history.replaceState){history.replaceState(null,null,'#'+id);}}).call(this);")

(defn- build-tab-nav-item [nav-id pane-id label active?]
  [:li.nav-item {:role "presentation"}
   [:a.nav-link
    (merge
     {:id nav-id
      :class (when active? "active")
      :data-bs-toggle "tab"
      :href (str "#" pane-id)
      :role "tab"
      :aria-controls pane-id
      :aria-selected (if active? "true" "false")
      :data-target pane-id
      :onclick onclick-fn})
    label]])

(defn- build-tab-pane [pane-id nav-id active? content]
  [:div.tab-pane.fade
   (merge {:id pane-id
           :role "tabpanel"
           :aria-labelledby nav-id}
          (when active? {:class "show active"}))
   content])

(defn- parent-table-modal
  [parent-table dbfields labels all-records]
  (let [table-id (str "select-" (safe-id parent-table) "-modal-table")
        modal-id (str "select-" (safe-id parent-table) "-modal")
        json-data (json/generate-string all-records)]
    [:div
     [:button.btn.btn-outline-primary.mb-3 {:type "button" :data-bs-toggle "modal" :data-bs-target (str "#" modal-id)}
      "Select " (str/capitalize (name parent-table))]
     [:div.modal.fade {:id modal-id :tabIndex -1 :role "dialog" :aria-labelledby (str modal-id "Label") :aria-hidden "true"}
      [:div.modal-dialog.modal-xl {:role "document"}
       [:div.modal-content
        [:div.modal-header
         [:h5.modal-title {:id (str modal-id "Label")} "Select " (str/capitalize (name parent-table))]
         [:button.btn-close {:type "button" :data-bs-dismiss "modal" :aria-label "Close"}]]
        [:div.modal-body
         [:table.table.table-striped.table-bordered.w-100 {:id table-id}
          [:thead
           [:tr
            [:th "Select"]
            (for [label labels] [:th label])]]
          [:tbody]]]
        [:div.modal-footer
         [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"} "Close"]]]]]
     [:script
      (str
       "document.addEventListener('shown.bs.modal', function(e){\n"
       "  if(e.target && e.target.id==='" modal-id "'){\n"
       "    var tableEl = $('#" table-id "');\n"
       "    if ($.fn.DataTable.isDataTable(tableEl)) {\n"
       "      tableEl.DataTable().clear().destroy();\n"
       "      tableEl.find('tbody').empty();\n"
       "    }\n"
       "    var data = " json-data ";\n"
       "    tableEl.DataTable({\n"
       "      data: data,\n"
       "      columns: [\n"
       "        { data: null, render: function(data,type,row){ return '<form method=\"get\" style=\"display:inline\"><input type=\"hidden\" name=\"id\" value=\"'+row.id+'\"/><button class=\"btn btn-sm btn-success\" type=\"submit\">Select</button></form>'; } },\n"
       (clojure.string/join ",\n" (map #(str "        { data: '" (name %) "' }") dbfields))
       "      ]\n"
       "    });\n"
       "  }\n"
       "});")]]))

(defn- default-child-rows [table fk p-id]
  (if p-id
    (Query [(str "select * from " table " where " (name fk) " = ?") p-id])
    []))

(defn- normalize-child [parent-table parent-id idx spec]
  (let [table (or (:table spec) (some-> (:id spec) name) (throw (ex-info "child spec missing :table" {:spec spec})))
        title (or (:title spec) (str/capitalize table))
        fk (or (:fk spec) (keyword (str parent-table "_id")))
        dbfields (or (:dbfields spec) [])
        labels (or (:labels spec) (mapv name dbfields))
        args (or (:args spec) {:new true :edit true :delete true})
        rows (cond
               (:rows spec) (:rows spec)
               (:rows-fn spec) ((:rows-fn spec) parent-id)
               :else (default-child-rows table fk parent-id))
        href (or (:href spec) (str "/admin/" table))
        new-href (or (:new-url spec) (when parent-id (str href "/add-form/" parent-id)))
        base (str (safe-id parent-table) "-" (safe-id (str parent-id)) "-" (safe-id title) "-" idx)
        pane-id (str base "-pane")
        nav-id (str base "-link")
        fields (fields-map dbfields labels)
        grid-fn (or (:grid-fn spec) build-grid-with-custom-new)]
    {:id base
     :table table :title title :dbfields dbfields :labels labels :args args :rows rows
     :href href :new-href new-href :pane-id pane-id :nav-id nav-id :fields fields :grid-fn grid-fn :lazy? (boolean (:lazy spec))}))

(defn- normalize-parent [parent-table parent-row spec]
  (let [p-id (parent-id parent-row)
        title (or (:title spec) (str/capitalize parent-table))
        dbfields (or (:dbfields spec) [:name :email :phone :imagen])
        labels (or (:labels spec) ["Name" "Email" "Phone" "Imagen"])
        args (or (:args spec) {:new true :edit true :delete true})
        href (or (:href spec) (str "/admin/" parent-table))
        base (str (safe-id parent-table) "-" (safe-id (str p-id)) "-parent")
        pane-id (str base "-pane")
        nav-id (str base "-link")
        fields (fields-map dbfields labels)
        grid-fn (or (:grid-fn spec) build-grid)
        rows (or (:rows spec) [parent-row])]
    {:id base :title title :dbfields dbfields :labels labels :args args :href href :fields fields :grid-fn grid-fn :rows rows :pane-id pane-id :nav-id nav-id}))

;; Start example usage - you need to modify your controller on the main grid only - You still have to generate the grid and subgrids, those are needed.  This is just a tabbed ui interface
; (defn contactos
;   [request]
;   (let [title "Contactos"
;         ok (get-session-id request)
;         js nil
;         params (:params request)
;         contactos-id (or (:id params) nil)
;         rows (if-not (nil? contactos-id)
;                [(get-contactos-id contactos-id)]
;                (get-contactos))
;         content (build-tabs "contactos" (first rows)
;                             {:labels ["Name" "Email" "Phone" "Imagen"] :dbfields [:name :email :phone :imagen] :args {:new true :edit true :delete true} :href "/admin/contactos"}
;                             {:table "siblings" :fk :contacto_id :labels ["Name" "Age" "Imagen"] :dbfields [:name :age :imagen] :args {:new true :edit true :delete true} :href "/admin/siblingscontactos"}
;                             {:table "cars" :fk :contacto_id :labels ["Company" "Model" "Year" "Imagen"] :dbfields [:company :model :year :imagen] :args {:new true :edit true :delete true} :href "/admin/carscontactos"})
;         user-r (user-level request)]
;     (if (some #(= user-r %) allowed-rights)
;       (application request title ok js content)
;       (application request title ok nil (str "Not authorized to access this item! (level(s) " allowed-rights ")")))))
;; End example usage

(defn build-tabs
  "Build bootstrap tabs for a parent and its child grids, with Select modal for parent."
  [parent-table parent-row & subgrids]
  (let [p-id (parent-id parent-row)
        [parent-override child-specs] (if (and (seq subgrids) (not (:table (first subgrids))))
                                        [(first subgrids) (rest subgrids)]
                                        [nil subgrids])
        parent-conf (normalize-parent parent-table parent-row (or parent-override {}))
        parent-title (:title parent-conf)
        parent-fields (:fields parent-conf)
        parent-href (:href parent-conf)
        parent-args (:args parent-conf)
        parent-dbfields (:dbfields parent-conf)
        parent-labels (:labels parent-conf)
        ;; Fetch all parent records for modal selection
        all-parent-records (Query [(str "select * from " (name parent-table))])
        child-confs (map-indexed (fn [idx s] (normalize-child parent-table p-id idx s)) child-specs)
        parent-grid ((:grid-fn parent-conf) parent-title [parent-row] parent-table parent-fields parent-href parent-args)
        tabs (concat [{:tabname parent-title :id (:id parent-conf) :pane-id (:pane-id parent-conf) :nav-id (:nav-id parent-conf) :grid parent-grid}]
                     (map (fn [c]
                            {:tabname (:title c) :id (:id c) :pane-id (:pane-id c) :nav-id (:nav-id c)
                             :grid ((:grid-fn c) (:title c) (:rows c) (:table c) (:fields c) (:href c) (:args c) (:new-href c))})
                          child-confs))]
    (list
     [:div
      ;; Select modal button and modal for parent grid only, with all records as JSON
      (parent-table-modal parent-table parent-dbfields parent-labels all-parent-records)
      [:ul.nav.nav-tabs {:role "tablist"}
       (doall (map-indexed (fn [idx t]
                             (build-tab-nav-item (:nav-id t) (:pane-id t) (:tabname t) (zero? idx)))
                           tabs))]
      [:div.tab-content.mt-3
       (doall (map-indexed (fn [idx t]
                             (build-tab-pane (:pane-id t) (:nav-id t) (zero? idx) (:grid t)))
                           tabs))]]
     [:script
      "document.addEventListener('DOMContentLoaded', function(){var id=localStorage.getItem('active-tab'); if(id){var sel='.nav-tabs .nav-link[href=\\\"#'+id+'\\\"]'; var el=document.querySelector(sel); if(el){try{bootstrap.Tab.getOrCreateInstance(el).show();}catch(e){el.click();}}}});"])))
