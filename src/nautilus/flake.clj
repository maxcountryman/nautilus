(ns nautilus.flake
  "A decentralized, k-ordered unique ID generator."
  (:import [java.net InetAddress NetworkInterface]
           [java.nio ByteBuffer]))


;; Format:
;;
;; 64-bit timestamp - milliseconds since the epoch (Jan 1 1970)
;; 48-bit worker id - MAC address from a configurable device
;; 16-bit sequence # - usually 0, incremented when more than one id is
;; requested in the same millisecond and reset to 0 when the clock ticks
;; forward


;; Utils
(defn hardware-address
  []
  (-> (InetAddress/getLocalHost)
      NetworkInterface/getByInetAddress
      .getHardwareAddress))


;; Global state
(def ^{:private true}
  state
  (atom {:time      nil
         :worker-id (hardware-address)
         :sequence  0}))


(defn byte-buffer
  [size]
  (ByteBuffer/allocate size))

(defn now
  []
  (System/currentTimeMillis))

(defn get-seq
  [timestamp]
  (->
    (if (= timestamp (:time @state))
      (swap! state update-in [:sequence] inc)
      (swap! state assoc-in [:sequence] 0))
    :sequence
    short))


;; Generator
(defn gen-flake
  [timestamp worker-id seq-short]
  (doto (byte-buffer 16)
        (.putLong timestamp)
        (.put worker-id)
        (.putShort seq-short)))

(defn generate
  [& [radix]]
  (let [timestamp (now)
        worker-id (:worker-id @state)
        seq-short (get-seq timestamp)
        flake     (.array (gen-flake timestamp worker-id seq-short))]

    ;; Ensure that the last timestamp is updated
    (swap! state assoc-in [:time] timestamp)

    (if radix
      (.toString (biginteger flake) radix)
      flake)))
