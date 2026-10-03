# SolarMiner: UI-/UX-Audit und Vorschlag für die Produktgestaltung

Stand: 2. Oktober 2026. Gegenstand: das lokale Bedienfrontend in `Solar-Miner-Node/react-frontend`, nicht die Marketingwebsite, das Adminportal oder die eigenständige PC-Agent-Oberfläche. Autor: Codex; Expertenreview anhand von Quellcode und Browserdarstellung, keine Studie mit tatsächlichen Nutzern.

## 1. Gesamturteil

SolarMiner besitzt eine gute fachliche Grundlage für ein anspruchsvolles Produkt: Energiefluss, Anlagenverwaltung, Mining-Geräte, Automatisierung, Wirtschaftlichkeit und Auszahlungen greifen in einer Anwendung ineinander. Positiv sind die bereits vorhandenen Datenqualitätsanzeigen, die Erklärung geschätzter Mining-Energiequellen, die getrennte Darstellung unterschiedlicher Guthabenarten und die Simulation von Automatisierungsregeln.

Die Oberfläche vermittelt jedoch noch eher die Funktionssammlung eines technischen Systems als ein durchgehend geführtes Produkt. Ihre wichtigste Aufgabe sollte sein, drei Fragen schnell und verlässlich zu beantworten:

1. Funktioniert meine Anlage, und kann ich ihren Daten vertrauen?
2. Warum läuft oder pausiert mein Mining gerade?
3. Welchen zusätzlichen Nutzen bringt Mining gegenüber dem Einspeisen?

Aktuell muss der Nutzer die Antworten aus mehreren Kennzahlen und Unterseiten zusammensetzen. Das größte Verbesserungspotenzial liegt deshalb in Informationshierarchie, Zustandskommunikation und verständlicher Automatisierung. Ein rein optisches Redesign würde einen Teil der Probleme verdecken, aber nicht lösen.

Meine empfohlene gestalterische Richtung: eine ruhige, präzise Energieanwendung mit klar erkennbarem Mining-Bereich. Dunkle Oberflächen und der gelbe Sonnenakzent passen dazu. Mehr visuelle Zurückhaltung, besser lesbare Beschriftungen und klare Handlungsschwerpunkte würden die vorhandene Identität stärken.

## 2. Untersuchungsmethode und Grenzen

Gelesen wurden Repository-Anweisungen, Wiki und Architektur-/Vertragsübersicht sowie Routing, Layout, globale Stile, Übersetzungen, Einrichtung, Dashboard, Anlagenverwaltung, Mining-Verwaltung, Mining-Ziele, Cluster-Regeln, Miner-Details, Finanzen, Prognose, Guthaben und Lightning-Wallet. Der gemeinsame PV-Profil-Editor wurde anhand des Quellcodes untersucht.

Der vorhandene Vite-Einstieg `spa/main.tsx` wurde lokal gestartet. Chrome stellte folgende Seiten mit kontrollierten API-Beispieldaten dar: Startseite, Einrichtung/Grunddaten, Geräte-Schritt, Dashboard, Mining, Finanzen, Ertragsprognose, Guthaben/Wallets, Mining-Ziele, Anlagendetails und Cluster-Konfiguration. Die Hauptansichten wurden bei 1440 × 1000 und 390 × 844 Pixeln erfasst; die Navigation zusätzlich bei 1920, 1280, 1180, 1024, 768 und 760 Pixeln untersucht. Geprüft wurden ausgewählte Dialoge, Neuladen eines Einrichtungsentwurfs sowie API-Ausfälle.

Die Beispieldaten dienten ausschließlich dazu, UI-Zustände zu zeigen. Manche Zahlen sind bewusst unabhängig gesetzt; daraus werden keine Buchhaltungs- oder Berechnungsfehler abgeleitet. Nicht vollständig durchgespielt wurden alle sechs Einrichtungsschritte, sämtliche Dialoge, echte Geräte-, Pool- und Zahlungsvorgänge, umfangreiche Anlagen oder alle Browser/Assistenztechnologien. Aussagen zur tatsächlichen Rentabilität, Gerätesicherheit, Hardwareunterstützung oder WCAG-Gesamtkonformität sind damit nicht belegt.

Keine Produktdatei und kein API-Vertrag wurde geändert. Die Bewertung und alle Zielbilder unten sind Vorschläge. Die während des Reviews angelegten Skripte sind lokale Diagnosehilfen, keine neue Produkttestsuite.

Lokale Nachweise:

- Skripte: `H:/Jetbrains_Workspace/Solarminer/.codex-qa/uiux-audit.cjs` und `uiux-audit-extra.cjs`.
- Screenshots und Messungen: `H:/Jetbrains_Workspace/Solarminer/.codex-qa/screenshots/uiux-2026-10-02/`.
- Ergebnisse: `observations.json` und `extra-observations.json` im Screenshotordner. Keine JavaScript-Seitenfehler im ersten Browserdurchlauf.
- Die regulären getesteten Desktop-/Mobilansichten hatten keinen horizontalen Dokumentüberlauf. Das war ausdrücklich kein Beweis für vollständig sichtbare Bedienelemente: `overflow-x: clip` verbirgt überstehende Navigation.

Ausgewählte lokale Screenshotnachweise, sämtlich mit synthetischen Daten:

- [Dashboard Desktop](../../../.codex-qa/screenshots/uiux-2026-10-02/dashboard-desktop.png) und [Mobil](../../../.codex-qa/screenshots/uiux-2026-10-02/dashboard-mobile.png).
- [Abgeschnittene Tablet-Navigation](../../../.codex-qa/screenshots/uiux-2026-10-02/dashboard-tablet.png).
- [Aktueller Regeleditor](../../../.codex-qa/screenshots/uiux-2026-10-02/mining-clusters-Standard-config-desktop.png).
- [Finanzen](../../../.codex-qa/screenshots/uiux-2026-10-02/finance-desktop.png), [Guthaben](../../../.codex-qa/screenshots/uiux-2026-10-02/finance-wallets-desktop.png) und [Lightning-Wallet](../../../.codex-qa/screenshots/uiux-2026-10-02/lightning-desktop.png).
- [Einrichtungsgrunddaten](../../../.codex-qa/screenshots/uiux-2026-10-02/setup-desktop.png) und [Geräte-Schritt](../../../.codex-qa/screenshots/uiux-2026-10-02/setup-source-desktop.png).

## 3. Was beibehalten werden sollte

- **Die fachliche Verbindung von Energie und Mining.** Ein Betreiber sollte keine separate Solar-App und Mining-App zusammensetzen müssen.
- **Datenqualität und Aktualität.** ONLINE/STALE/OFFLINE/WAITING im Dashboard sind eine wertvolle Grundlage. Diese Idee sollte auf Miner, Pooldaten, Kursdaten und Prognosen erweitert werden.
- **Die überarbeitete Geräteeinrichtung.** Automatische Suche und manuelle Verbindung sind getrennt; die Oberfläche erklärt den Zweck der PV-Daten und weist auf den nächsten erforderlichen Schritt hin. Das ist im vorhandenen Browserzustand eine klare Stärke.
- **Die überarbeitete Dachflächen-Erfassung.** Der Quellcode bietet verständliche Richtungswahl, Neigung, Wp/kWp-Vorschau, editierbare Flächen und explizite Standortwahl. Historische Kritik am alten Formular gilt nicht unverändert für diese Version.
- **Simulation vor dem Einsatz von Regeln.** Diese Funktion ist ein Differenzierungsmerkmal und sollte leichter zugänglich werden.
- **Die Unterscheidung von Poolguthaben, beobachteten Adressen und Node-Lightning-Wallet.** Die vorhandene Erklärung ist fachlich hilfreich. Im Zielbild muss sie erhalten bleiben.
- **Kennzeichnung von Brutto-Prognosen, unvollständigen Poolhistorien und Schätzwerten.** Diese Ehrlichkeit sollte prominenter und konsistenter werden.
- **Berücksichtigung reduzierter Bewegung.** `globals.css` und das Anlagenlayout enthalten bereits Regeln für `prefers-reduced-motion`.

## 4. Priorisierte Befunde

Priorität A: zuerst beheben, weil Bedienbarkeit, Vertrauen oder Verständnis wesentlicher Zustände betroffen sind. B: wesentliche strukturelle Verbesserung. C: Verfeinerung nach den grundlegenden Korrekturen. Aufwand ist eine relative UI-Einschätzung, keine Projektkalkulation.

| ID | Priorität | Befund | Nachweis | Empfehlung | Aufwand |
| --- | --- | --- | --- | --- | --- |
| UX01 | A | Navigation/Einstellungen werden abgeschnitten; Einstellungen bei mittleren Breiten nicht erreichbar | Browser: 1440 px, Einstellungsblock reicht bis x=1541; bei 1024 px Navigation bis x=1113. 761–1180 px: Einstellungen und Menütaste beide ausgeblendet | Weniger Hauptpunkte, Anlagenwechsel im Header, Einstellungen in dauerhaft erreichbarem Menü; Breakpoints nach tatsächlichem Platz | Mittel |
| UX02 | A | Startseitenfehler bleibt ein endloser Ladezustand | HTTP-503-Fixture zeigt nur „Loading...“; `app/page.tsx` protokolliert Fehler nur in Konsole | Erkennbarer Fehlerzustand mit erneutem Versuch, Zeitgrenze und Erklärung | Klein |
| UX03 | A | Dashboard-Profilprüfung kann einen Fehler als Laden verbergen | 503 auf Profilprüfung: Dashboard bleibt bei „Dashboard wird geladen...“; früher Return vor Fehlerdarstellung | Initialprüfung mit expliziten Lade-/Fehler-/Erfolgszuständen und Wiederholen | Klein |
| UX04 | A | Wichtige kleine Beschriftungen haben zu wenig Kontrast | Farbpaar #777781/#131318: 4,18:1; #666670: 3,26:1; #696973: 3,41:1; Dashboard verwendet 10–11 px Text | Einheitliche kontrastgeprüfte Texttokens; entscheidende Labels 14–16 px als Designziel | Klein bis mittel |
| UX05 | A | Dialogverhalten ist nicht vollständig tastaturbedienbar | Ertragsdialog: Escape schließt nicht, nächster Tab liegt außerhalb; Miner-Verbinden: Escape schließt nicht; mobiles Menü: Fokus beim Öffnen außerhalb | Gemeinsame Dialogkomponente mit Fokusführung, Fokusbegrenzung, Escape und Rückgabe des Fokus | Mittel |
| UX06 | A | Einrichtungsentwurf geht beim Neuladen verloren | Eingetragener Name nach Reload leer; Formularzustand nur in React-State | Entwurf wiederherstellen, Schritt speichern, Änderungsschutz; Geheimnisse nicht unbedacht dauerhaft ablegen | Mittel |
| UX07 | A | Datenfreigabe ist im Setup vorausgewählt | `setup/page.tsx`: `useState(true)` für `telemetryEnabled` und Übermittlung als `telemetryOptIn` | Freigabe standardmäßig aus; bewusste Auswahl und verständliche Vorschau der geteilten Daten | Klein |
| UX08 | A | „Profitables“ Netzstrom-Mining vergleicht laut UI/Regel Bruttoertrag mit Strompreis | Prognose nennt ausgeschlossene Gebühren; Regeleditor beschreibt Bruttovergleich und Modus „Profitable grid mining“ | Bis zur Netto-Berechnung präzise als Bruttovergleich benennen; Abzüge und Unsicherheit direkt bei Aktivierung zeigen | UI klein; fachlich mittel bis groß |
| UX09 | B | Dashboard zeigt zu viel gleichrangige Information | 390-px-Beispiel: Dokumenthöhe 3773 px, mit nur einem Miner; Tageswerte mehrfach | Betriebszustand und Entscheidung zuerst, vier Schlüsselwerte, Details nach Bedarf | Mittel bis groß |
| UX10 | B | Energiefluss-Hub wirkt wie Summenknoten, trägt aber Verbrauchswert | Quellen/Abflüsse werden über zentralen Block geleitet; `EnergyFlow` nutzt `totalLoadKw` | Gemeinsamen Energieverteiler korrekt benennen; Verbrauch separat ausweisen; Messgrenzen/Schätzung markieren | Mittel |
| UX11 | B | Gestaltung und Einstellungen wechseln zwischen Seiten | Start/Lightning: blaugraue Flächen, blaue Buttons, Arial; Anlagenlayout: schwarze Flächen/gelbe Akzente. Start/Lightning mit eigenem Sprachstate | Ein gemeinsamer Rahmen und Designsystem; überall dieselben Präferenzen | Mittel |
| UX12 | B | Wirtschaftlichkeit verteilt sich auf viele semantisch ähnliche Bereiche | Dashboard-Tagesertrag, Finance, Earnings, Walletstrip, Wallets, Lightning | Tatsächliche Ergebnisse, Prognose und verfügbare Guthaben eindeutig gliedern | Mittel |
| UX13 | B | Regeleditor verlangt DSL-Verständnis | Browser zeigt POTENTIAL_PV_SURPLUS, GREATER_OR_EQUAL, CLUSTER_DYNAMIC, MEDIAN, Multiplikator 1000 | Einfachmodus mit verständlichen Regeln und Einheiten; Expertensicht erhalten | Groß |
| UX14 | B | Währungen, Zeitzonen und Zahlenformate sind uneinheitlich | Globale EUR/USD/CHF; Ertragskarte/Prognose fest EUR; Lightning nur EUR/USD und eigener State; diverse `toFixed` | Zentrale Formatierung und konsistente Präferenzen; absichtliche EUR-Referenzen beschriften | Mittel |
| UX15 | B | Globale Hashrate-/Effizienzwerte bleiben auf TH/s/J/TH ausgerichtet | Dashboard, Mining-KPI und Miner-Details; Workergruppen bieten teilweise bereits adaptive Einheiten | Nach Algorithmus darstellen; kleine CPU-/GPU-Raten in H/s/kH/s/MH/s; keine scheinbar sinnvolle algorithmusübergreifende Leistungssumme | Mittel |
| UX16 | B | Guthabenleiste erzeugt permanente Aufmerksamkeit ohne vollständige Übersicht | Auf jeder Anlagenseite; „Gesamt · EUR“ ohne EUR-Zahl; alle Positionen derselben Coin-Art addiert | Kompakter Guthabenlink; Details auf eigener Seite; Teilverfügbarkeit und Quellen klar zeigen | Klein bis mittel |
| UX17 | B | Einrichtung endet vor dem ersten nachgewiesenen Mining-Erfolg | Setup verbindet PV/optional Pool, Miner später; Mining-Dialog verweist auf separate Clusterzuordnung | Anschließende geführte Liste bis „Miner sendet akzeptierte Arbeit“ und „Überschussregel aktiv“ | Mittel bis groß |
| UX18 | C | Zu viele Sonderstile, Karten und Farbbedeutungen | Screenshots, lokale Klassen und eingebettete Stile | Gemeinsame Seitenköpfe, Karten, Buttons, Tabs, Status, Formfelder; Animationen sparsam | Mittel |

## 5. Produktkonzept und Navigation

### Beobachtung

Der Hauptheader enthält sieben Ziele und daneben Sprache, Währung und Zeitzone. „Anlagenauswahl“ ist ein Kontextwechsel, „Lightning Wallet“ eine globale Finanzfunktion und „Ertragsprognose“ ein Teil der wirtschaftlichen Bewertung. Sie beanspruchen trotzdem denselben Navigationsrang wie Dashboard und Mining. Die aktuelle Anlage steht erst im Inhalt, nicht verlässlich im zentralen Kontext.

Das führt gleichzeitig zu Platzproblemen und gedanklicher Unordnung. Unterpunkte wie Mining-Ziele und Cluster-Regeln sind zwar erreichbar, werden aber erst im Inhalt entdeckt. Der Begriff „Mining-Cluster“ im Seitenkopf passt nur teilweise zur initialen Geräteansicht.

### Vorschlag

Vier Hauptbereiche: **Übersicht, Geräte, Automatisierung, Wirtschaftlichkeit**. Im Header eine Anlagenwahl mit Namen und Verbindungsstatus; rechts ein kleines Menü für Sprache, Währung, Zeitzone, Darstellung und Hilfe. Der genaue Name „Geräte“ lässt sich bei Bedarf durch „Anlage & Geräte“ präzisieren.

| Bereich | Inhalte | Hauptfrage |
| --- | --- | --- |
| Übersicht | Betriebszustand, Energiefluss, Tagesergebnis, dringende Aufgaben | Läuft alles wie beabsichtigt? |
| Geräte | PV-Messquellen, Miner, Gruppen, gerätespezifische Limits | Was ist verbunden und einsatzbereit? |
| Automatisierung | Betriebsstrategie, Regeln, Prioritäten, Simulation, Entscheidungsverlauf | Wann und warum soll Mining laufen? |
| Wirtschaftlichkeit | Tatsächliche Ergebnisse, Prognose, Kosten, Guthaben, Auszahlungen, Export | Lohnt sich der Betrieb, und wo liegen die Coins? |

Die Lightning-Wallet bleibt als globale Node-Funktion technisch und in ihrer Beschriftung erkennbar. Sie muss nicht zum Guthaben einer einzelnen Anlage umgedeutet werden. Sie kann aus dem Finanzbereich heraus erreichbar sein, ohne dass der Nutzer den Kontext verliert.

Desktop: vier kurze Hauptlinks reichen voraussichtlich ohne zusätzliche Seitenleiste. Eine Seitenleiste wird sinnvoll, wenn echte häufig genutzte Unterbereiche hinzukommen. Mobil: vier beschriftete Navigationsziele, optional unten fixiert; dabei Safe Areas und die Softwaretastatur berücksichtigen. Die Anlagenauswahl bleibt im oberen Header. Tastatur- und Vergrößerungsbedienung sind Teil der Abnahme.

**Abnahmekriterium:** Kein wichtiges Steuerelement wird zwischen 320 und 1920 px sowie bei vergrößerter Darstellung abgeschnitten oder durch einen Breakpoint unerreichbar. Das Kriterium ist Erreichbarkeit, nicht lediglich fehlender Scrollbalken.

## 6. Dashboard: vom Datenbestand zur Entscheidung

### Gegenwärtiges Problem

Vier KPIs, sieben Energieflusspositionen plus Hub, Batterie, Automatisierung, vier Tagesübersichten, Energiequellen, Diagramm, Miner und Pools ergeben eine lange Seite. Produktion und Autarkie werden mehrfach angezeigt. Für einen täglichen Kontrollblick fehlt eine klare Hauptaussage.

Die Effizienz in J/TH ist für ASIC-Betreiber hilfreich, aber als eines von vier führenden Feldern für Einsteiger weniger relevant als „Mining läuft mit Überschuss“ oder „Heute nach Energiekosten“. Auf Mobilgeräten wird der Energiefluss zur langen Liste ohne sichtbare Verbindungslinien; aus dem Diagramm wird damit eine Sammlung von Zahlen.

### Empfohlene Reihenfolge

1. **Betriebsstatus und Grund:** „Automatik aktiv · 1 von 2 Minern läuft.“ Darunter etwa „2,4 kW Solarüberschuss werden genutzt; der zweite Miner wartet auf genügend Leistung.“ Dies ist vorgeschlagener Text, kein belegter aktueller Backend-Zustand.
2. **Aktuelle Energie:** PV, Haus, Mining, Batterie und Netz in einer kompakten Darstellung. Auf Mobil eigene Anordnung statt bloßem Stapeln aller Desktopknoten.
3. **Tagesnutzen:** Produktion, Eigenverbrauch, tatsächlicher Mining-Erlös und zusätzliches Ergebnis gegenüber Einspeisen. Fehlende Grundlage als „noch nicht berechenbar“ zeigen.
4. **Ein fokussiertes Diagramm:** Heute als Standard, darunter Zeitraumwahl; weitere Reihen erst nach Auswahl.
5. **Geräte und technische Details:** einklappbar oder mit klarer Verlinkung in Geräte/Diagnose.

Der bestehende Automatisierungsblock sollte prominent und klickbar werden. Bei Pausen zeigt er den entscheidenden Grund: geringer Überschuss, Batteriegrenze, Mindestpause, externe Steuerung deaktiviert, Daten zu alt oder manuell pausiert. Nur tatsächlich verfügbare Gründe anzeigen; ergänzende Informationen benötigen eventuell neue Backend-Felder.

Warnungen sollten eine Aktion bekommen: „PV-Daten seit 5 Minuten veraltet → Verbindung prüfen“ oder „Miner nicht erreichbar → Gerät öffnen“. Ein generischer Textblock hilft weniger. Eine unveränderliche Meldung braucht keine dauernde Animation.

### Energiefluss und Messgrenzen

Im aktuellen `EnergyFlow` werden alle Quellen und Abflüsse optisch über den Hub geführt, der `totalLoadKw` als „Gesamtverbrauch“ zeigt. Im Beispiel fließen 6,2 kW von PV ein; Haus/Mining verbrauchen 3,2 kW, zusätzlich gehen 2,5 kW ins Netz und 0,5 kW in die Batterie. Der Hub mit 3,2 kW ist als Verbrauchswert verständlich, als grafischer Verteilknoten aber missverständlich.

Entweder einen neutralen „Energieverteilung“-Hub ohne scheinbare Summenzahl verwenden oder eine fachlich abgestimmte Leistungsbilanz darstellen. „Verbrauch Haus + Mining“ separat ausweisen. Je nachdem, wo reale Sensoren messen, sind Verluste, Zeitversatz und abgeleitete Werte zu erklären. Eine Bilanzwarnung darf erst aus belastbaren Messgrenzen entstehen.

Batterie nicht doppelt als permanenter Ein- und Ausgang zeigen, wenn nur eine Flussrichtung aktiv ist. Komponenten, die in einer Anlage nicht vorhanden sind, sollten entfallen. Die heutige Oberfläche hat hierfür keinen ausdrücklichen Präsenzindikator im `LiveEnergyDto`; ein zuverlässiges Zielbild kann eine Vertragserweiterung benötigen.

## 7. Einrichtung und erste erfolgreiche Nutzung

Der Geräte-Schritt ist deutlich besser geführt als ein reines Protokollformular. Diese Richtung sollte auf die komplette Erstnutzung ausgeweitet werden.

### Änderungen

- **Grunddaten entlasten.** Name und lokaler Kontext zuerst. Investitionskosten und ältere Inbetriebnahme getrennt als optionale Grundlage für Wirtschaftlichkeit anbieten. Strompreis und Einspeisevergütung sind bei entsprechenden Regeln relevant und dürfen nicht stillschweigend als korrektes 0-Euro-Modell behandelt werden.
- **Unbekannt von null unterscheiden.** „Noch nicht eingetragen“ ist ein anderer Zustand als ein bewusst bestätigter Tarif von 0. Fehlende Tarife dürfen Auswertungen als unvollständig kennzeichnen. Änderungen an Validierung oder Speicherung müssen im Backend abgestimmt werden.
- **Batteriekapazität in kWh erfassen.** Bei Hausbatterien ist „10 kWh“ verständlicher als „10000 Wh“. Die Umrechnung kann intern erfolgen, ohne sofort einen Vertrag zu ändern.
- **Entwurf wiederherstellen.** Aktueller Schritt und unkritische Daten speichern; bei Wiederaufnahme „Einrichtung fortsetzen“ anbieten. Zugangsdaten separat behandeln und keine Pauschallösung mit dauerhaftem Browserstorage für Geheimnisse wählen.
- **Schrittwechsel führen.** Überschrift fokussieren, an den neuen Abschnitt scrollen, feldbezogene Fehlermeldungen mit `aria-describedby` zeigen. Lange Mobilformulare benötigen einen gut erreichbaren Weiterbereich.
- **Anlagenzahl eindeutig beschriften.** „1/5“ im Setup-Header ist im Kontext einer sechs Schritte langen Einrichtung missverständlich. Besser „1 von 5 Anlagen eingerichtet“ oder an diesem Ort weglassen.
- **Hersteller/Modell statt Transport zuerst.** Einsteiger kennen Fronius, SMA oder Home Assistant eher als Modbus und REST. Dafür muss der Profilkatalog verlässliche Metadaten liefern; keine Kompatibilität allein aus Profilnamen ableiten.
- **Nach der Anlage weiterführen.** Checkliste: Miner verbinden → Gruppe/Strategie wählen → Mining-Ziel prüfen → Automatik bewusst starten → Daten und akzeptierte Poolarbeit nachweisen.

Der Standort der Module darf nicht einfach entfernt werden: Die bestehende Wiki und Quellcode-Evidenz zeigen seine Verwendung für Sonnenauf-/untergang in Regeln. Eine vereinfachte einmalige Anlagenposition ist ein mögliches Zielbild, benötigt aber eine fachliche und technische Prüfung der bisherigen Gruppenpositionen.

### Datenfreigabe

Im Setup ist die Freigabe gegenwärtig vorausgewählt. Ich empfehle eine bewusste, standardmäßig ausgeschaltete Auswahl. Direkt am Schalter sollte stehen, was gesendet wird, an wen, zu welchem Zweck und wie man die Freigabe später widerruft. Eine kleine Beispielvorschau ist leichter verständlich als ein langer Fachtext. Die Wahl muss gleichwertig überspringbar sein. Das ist eine Vertrauens- und UX-Empfehlung, keine rechtliche Bewertung.

## 8. Mining-Geräte, Ziele und Gruppen

Die Geräteverwaltung beginnt derzeit mit Clusterzahlen und einer algorithmischen Übersicht, obwohl der initiale Unterbereich „Geräte & Pools“ heißt. Das tatsächliche Verzeichnis der verbundenen Miner kommt später. Leere Coin-Karten nehmen Platz ein; technische Zusammenfassungen verdrängen Bedienaufgaben.

Mein Zielbild: Geräte zuerst, optional nach Raum, Gruppe oder Algorithmus filterbar. Jede Zeile/Karte zeigt Name, Status mit Grund, Leistung, Temperatur, algorithmusspezifische Hashrate und Steuerbarkeit. Eine Gruppe bekommt eine verständliche Bedeutung: „Diese Miner folgen derselben Strategie.“ Nach erfolgreicher Verbindung folgt direkt „Gruppe zuordnen“ beziehungsweise „Neue Gruppe erstellen“.

Es müssen getrennte Zustände erkennbar sein: gefunden, Zugang geprüft, verbunden, konfiguriert, durch die Node steuerbar, Mining aktiv und Poolarbeit bestätigt. „Vom Gerät gemeldetes Ziel“ ist kein Nachweis für aktive Poolverbindung oder Auszahlung. Die Mining-Zielseite weist auf diese Unterscheidung bereits hin; sie sollte auch die visuellen Statuslabels bestimmen.

Der Miner-Suchdialog verlangt einen Netzbereich. Die vorausgesetzte FRITZ!Box-ähnliche Adresse `192.168.178` ist nicht universell. Suchbereich erklären und vorschlagen; bei leerer Suche eine manuelle IP-/Agent-Verbindung anbieten, sofern das Backend dies unterstützt. Die detaillierten Fortschritts- und Wiederherstellungszustände der PV-Suche sind ein gutes Vorbild. Der vorliegende Review hat keine echte Minersuche durchgeführt.

Pools brauchen eine verständliche Zweiteilung: **Mining-Verbindung** zum Senden der Arbeit und **Kontoverbindung** zum Abrufen von Guthaben/Erträgen. Ein Nutzer sollte nicht glauben, das Eingeben eines API-Tokens richte automatisch jedes Mining-Gerät ein. Gerätezuordnung und API-Status nebeneinander erklären.

Hashraten in H/s, kH/s, MH/s, GH/s oder TH/s passend zur Größenordnung zeigen. SHA256, RandomX und PearlHash getrennt bewerten; die Kennzahl „Gesamthashrate“ sollte nicht algorithmusübergreifend wie ein vergleichbarer Leistungsindex wirken. Allgemeine Summen können Gerätezahl und Watt sein. J/TH als führende Effizienzkennzahl eignet sich für SHA256; CPU-/GPU-Bereiche brauchen passende Definitionen und dürfen nicht durch Rundung scheinbar null leisten.

Die Entwicklergebühr sollte sichtbar und verständlich bleiben. Allerdings heißen die aktuellen Angaben „Hashrate-Verteilung“, während die angezeigten Werte im Frontend aus Gesamt-Hashrate × Gebührenprozentsatz berechnet werden. Besser „Konfigurierte Gebührenverteilung“ mit Hinweis auf Schätzung, soweit kein gemessener Routing-/Poolnachweis vorliegt. Transparenz ist wertvoll; Scheingenauigkeit schwächt sie.

## 9. Automatisierung: größte konzeptionelle Baustelle

Der aktuelle Regeleditor ist leistungsfähig, aber auf technische Anwender ausgerichtet. Er zeigt interne Enums, Vergleiche, Aggregation, Multiplikator, Offset und Kapazitätsanteile von 0–1. Eine falsche Einheit oder ein falsch verstandener Operator kann die beabsichtigte Strategie erheblich verändern.

### Zwei Bedienebenen

**Einfachmodus:** Einsteiger wählen „Solarüberschuss nutzen“ und bearbeiten eine kleine Zahl gut erklärter Einstellungen: minimale freie Leistung, Batteriereserve, Verzögerung vor Start, minimale Lauf-/Pausendauer und maximale Mining-Leistung. Ergänzend eine verständliche Prioritätenbeschreibung. Vorgaben erst nach Abgleich mit tatsächlichen Controllerfähigkeiten anbieten.

**Expertenmodus:** Den vorhandenen Regelumfang erhalten, aber interne Werte in sprechende Bezeichnungen übersetzen. „GREATER_OR_EQUAL“ wird „mindestens“, „MEDIAN“ zu „Median im Zeitraum“, „CLUSTER_DYNAMIC“ zu einer Erklärung des tatsächlich gemeinten Ziels. Jede Variable zeigt ihre Einheit; ein W/kW-Multiplikator gehört möglichst in die Darstellung und nicht als überraschende 1000 ins Einsteigerformular.

Beispiel einer vorgeschlagenen Regelvorschau: „Wenn der Solarüberschuss im Median der letzten 5 Minuten mindestens 1,5 kW beträgt, starte die Gruppe. Stoppe unter 1,0 kW; halte Lauf- und Pausenzeiten ein.“ Das ist ein Textkonzept, kein behaupteter ausgelieferter Preset.

Regelentwurf, gespeichert und aktiv müssen unterschiedliche Zustände sein. Nach einer Änderung: Vorschau → Simulation → Zusammenfassung der Wirkung → bewusst anwenden. Bei einer vorhandenen komplexen Regel darf die Einfachansicht nicht stillschweigend Bedingungen löschen; gegebenenfalls „Diese Regel benötigt die Expertenansicht“ anzeigen.

### Netzstrom und vermeintliche Profitabilität

Das aktuelle Prognosefrontend bezeichnet Werte korrekt als brutto und nennt ausgeschlossene Gebühren. Der Regeleditor bietet aber „Bei profitablem Netzstrom weiter minen“ und vergleicht laut Beschreibung Bruttoertrag mit Strompreis. Das kann eine stärkere wirtschaftliche Aussage vermitteln, als die angezeigte Datengrundlage trägt.

Bis ein abgestimmtes Nettomodell vorhanden ist, präzise benennen: „Netzstrom erlauben, wenn der geschätzte Bruttoertrag den Tarif übersteigt“. Direkt daneben Abzüge und Unsicherheit zeigen. Ein Nettonutzenmodell müsste Pool-/Entwicklergebühren, Ablehnungen, Energiekosten und bei PV die entgangene Einspeisevergütung nachvollziehbar behandeln. Keine Berechnung im Frontend erfinden. Begrenzung, Ein-/Ausschaltmargen und Datenalter müssen zur tatsächlichen Controllerimplementierung passen.

### Simulation als verständliches Werkzeug

Die Simulation sollte schon aus dem Einfachmodus erreichbar sein. Sie liefert eine Tageszusammenfassung: aktive Zeit, Mining-Energie, Netzanteil, Moduswechsel und gegebenenfalls Kosten mit markierten Annahmen. Eine Zeitleiste sollte zeigen, warum die Gruppe pausiert. Synthetische und historische Daten klar unterscheiden; die bestehende Schätzung von Sperren und Zuweisung nicht als echten Hardwaretest darstellen.

## 10. Wirtschaftlichkeit und Finanzdaten

Die Finanzseite startet mit Kapitalrückfluss und BTC-Sensitivität; der Zeitraumfilter folgt erst darunter. Nutzer können annehmen, der Filter ändere auch die bereits angezeigten Lebenszeitwerte. Unterhalb stehen mehrere ähnliche Größen: Wertschöpfung, Betriebsergebnis, Erlös, live/historisch, Kapitalwert und Nettoposition.

### Empfohlene Gliederung

1. **Ergebnis im ausgewählten Zeitraum:** tatsächliche beziehungsweise gebuchte Erlöse, Energiekosten, Opportunitätskosten und nachvollziehbares Nettoergebnis.
2. **Prognose:** Modell für aktuelle Hashrate und Marktpreise, mit Annahmen und deutlich anderer visueller Kennzeichnung.
3. **Amortisation seit Inbetriebnahme:** Investition und kumulierter Nutzen, getrennt von kurzfristigen Periodenergebnissen.
4. **Guthaben und Auszahlungen:** wo Coins liegen und welcher Betrag tatsächlich verfügbar ist.

Die Periodenwahl oben platzieren, mit Heute, 7 Tagen, Monat und eigener Auswahl. Lebenszeitkarten tragen einen sichtbaren unabhängigen Zeitraum. Export in einem Menü zusammenfassen; vor dem Export Zeitraum und Währung anzeigen. Kryptopreis-Sensitivität als Vertiefung anbieten, nicht als zweiten dominanten Bereich für jeden Betreiber.

Für das Produktversprechen ist die Vergleichsfrage besonders wertvoll: **Was hat Mining zusätzlich zur Einspeisung gebracht?** Vorhandene Opportunitätskosten sind dafür eine Grundlage, aber eine neue Kennzahl benötigt bestätigte Definitionen. Eine PV-Haushaltsersparnis darf nicht versehentlich als ausschließlich durch Mining verursachter Erfolg wirken.

Kennzahlen bekommen kurze Definitionen am Ort der Verwendung. Ein sinnvoller Aufbau wäre „Erlös − Netzstrom − entgangene Einspeisevergütung = Mining-Ergebnis“; weitere Gebühren nur abziehen, wenn sie nicht bereits in den gebuchten Poolwerten enthalten sind. Bewertete unverkaufte Coins, realisierte Verkäufe und tatsächlich auszahlbare Beträge bleiben getrennt.

Prognosen wie „7,20 €/Tag“ sollten direkt „Brutto, bei unverändertem Betrieb“ tragen. Eine 24-Stunden-Hochrechnung ist keine Wetter- oder Solarüberschussprognose. Wenn eine realistischere Tagesprognose PV-Verfügbarkeit oder Zeitfenster einbeziehen soll, muss das Modell fachlich ergänzt werden.

Szenarien besser mit expliziten Annahmen und ggf. auswählbaren ±30 % darstellen. Kein mathematisch genauer Amortisationstermin ohne Angabe seiner Grundlage. Lückenhafte Poolhistorien in allen betroffenen Summen sichtbar machen; fehlende Werte nicht auf null formatieren.

## 11. Guthaben und Lightning-Wallet

Die eigene Guthabenseite erklärt Beobachtungsadressen und Poolkonten gut. Der globale Walletstrip führt diese Unterschiede dagegen in einer einzigen Coin-Summe zusammen und zeigt einen „Gesamt · EUR“-Chip ohne EUR-Gesamtbetrag. Das wirkt wie eine unvollständige KPI und nimmt auf jeder Seite Platz ein.

Vorschlag: Ein kompakter Link „Guthaben“ mit optionalem zuletzt bekannten Betrag und klarer Teilverfügbarkeitsanzeige. Die vollständige Aufschlüsselung bleibt auf der Guthabenseite. Ein Gesamtwert wird nur unter verifizierten Bewertungs- und Abgrenzungsregeln gezeigt. Node-Lightning-Guthaben ist global; bei mehreren Anlagen darf seine wiederholte Darstellung nicht als anlageneigener Ertrag gelesen werden. Eine tatsächliche Doppelzählung ist durch diesen Review nicht nachgewiesen.

Für BTC bei kleinen Beträgen sats als bevorzugte Anzeige anbieten. Acht Nachkommastellen sind auf mobilen Geräten schwer vergleichbar. Adressen gekürzt, aber mit zugänglichem vollständigem Wert und Kopieraktion darstellen. Bestätigt, unbestätigt, nicht unterstützt, veraltet und Abfrage fehlgeschlagen sollten eigene Zustände sein. XMR-Beobachtung per öffentlicher Adresse darf nicht den Eindruck einer auslesbaren vollständigen Wallet erzeugen.

Die Lightning-Wallet verwendet derzeit ein eigenes blaugraues Layout und eigene Sprache/Währung; der Zurückpfeil führt immer zur Startseite. Sie sollte in den gemeinsamen Rahmen integriert werden oder zumindest einen verständlichen Rückweg zum aufrufenden Finanzbereich behalten.

Priorität dort: **Empfangen, Senden, Verlauf**. Kanalstatistiken, Verbindungsdiagnose und BOLT12 in eine Vertiefung „Technische Details“. „Mining Payout Center“, „Inbound Fee Credit“ und „Remote Liquidität“ verständlich und konsistent benennen. Den Platzhalter „Auto-Regeln (Bald)“ aus dem normalen Arbeitsablauf entfernen, bis eine nutzbare Funktion vorhanden ist.

Kopieren sollte eine kurze unaufdringliche Bestätigung erzeugen; die aktuellen Browser-Alerts unterbrechen den Ablauf. API-Fehler brauchen Erklärung und Wiederholen. Bei 503 zeigt die Wallet derzeit lediglich „Keine Wallet-Daten verfügbar“, ohne Wiederherstellungsmöglichkeit.

Vor einer Zahlung sollte eine prüfbare Zusammenfassung mit Empfänger, Betrag, Netzwerk und verfügbaren Gebührendaten stehen. Bei unbekannten Gebühren diese Ungewissheit ausdrücklich zeigen. Versand, Bestätigung, Fehler und unklarer Ausgang brauchen unterscheidbare Zustände; die Oberfläche darf bei unklarem Ergebnis nicht zum unbedachten erneuten Senden verleiten. Dieser Review führte keine Zahlung aus und bewertet keine tatsächliche Transaktionssicherheit.

## 12. Visuelle Gestaltung und Designsystem

### Richtung

Die dunkle/gelbe Grundgestaltung ist passend. Ich würde sie vereinheitlichen und beruhigen: wenige Flächenstufen, klare Typografie, gelber Akzent für wesentliche Aktionen, semantische Statusfarben. Aktuell werden Solarproduktion, Tabs, Warnungen, Buttons, Gebühren und Icons teilweise mit ähnlicher Intensität hervorgehoben. Gleichzeitig konkurrieren Cyan, Grün, Orange und Violett um Aufmerksamkeit.

### Konkrete Regeln

- **Typografie:** Eine tatsächlich eingebundene Schriftfamilie oder einen bewussten Systemfont überall verwenden. Das aktive Vite-Frontend durchläuft nicht das Next-Rootlayout mit Google-Geist; der globale Body verwendet Arial, das Anlagenlayout eine Inter/Systemfont-Liste. Eine deklarierte Schrift ist kein Nachweis für geladene Fontdateien.
- **Größen:** 16 px Fließtext, 14 px wichtige Labels, 12 px nur unterstützende Metadaten als Ausgangspunkt. 10–11 px vermeiden, wenn die Information für Entscheidungen gebraucht wird.
- **Farben:** Primärtext nahe Weiß, Sekundärtext mit geprüftem Kontrast; Gelb als Sonnen-/Aktionsakzent, Grün für bestätigten Normalzustand, Amber für Aufmerksamkeit, Rot für Fehler oder gefährliche Aktion. Status immer zusätzlich mit Text/Icon zeigen.
- **Abstände:** Gemeinsames 4-/8-px-Raster, konsistente Karteninnenabstände und überschaubare Radienstufen. Weniger Karte-in-Karte-Flächen und große dekorative Header.
- **Buttons:** Ein primärer Handlungsschwerpunkt pro Kontext. Auswahl-Tabs nicht als riesige primäre gelbe Aktionen gestalten. „Speichern“ und „Gespeichert“ müssen sichtbar unterschiedliche Zustände haben.
- **Zahlen:** Tabellarische Ziffern, deutsche Dezimaltrennung bei deutscher Sprache, konsistente Einheiten und sinnvoll gerundete Präzision. Fehlender Wert = Gedankenstrich plus Ursache, nie automatisch echte Null.
- **Bewegung:** Flussanimationen sollen Richtung erklären. Dauerhaft pulsierende Icons und Glows reduzieren. Vorhandene Reduced-Motion-Unterstützung erhalten und für neue Komponenten mitprüfen.
- **Helle Darstellung:** Als spätere Option sinnvoll für Tageslicht und unterschiedliche Präferenzen. Vorrang hat ein ausreichend kontrastreiches konsistentes Dunkelthema; eine zweite Palette vervielfacht sonst Inkonsistenzen.
- **Branding:** Startseite zeigt derzeit Logo-Wortmarke und zusätzlich „Solarminer.app“. Ein klarer Markenname plus funktionale Begrüßung reicht. Schreibweisen SolarMiner/Solarminer.app bewusst vereinheitlichen.

Eine gemeinsame Komponentenbasis sollte Seitenkopf, Status, Formfeld, Validierung, Dialog, Tabs, Kennzahl, Tabelle, Leerzustand, Toast, Datenalter und Zeitraumwahl abdecken. Bibliothekswechsel sind dafür nicht Voraussetzung. Zuerst sichtbare Zustände und Regeln festlegen, anschließend technisch vereinheitlichen.

## 13. Barrierefreiheit und responsive Bedienung

Die Kontrastmessung liefert konkrete Korrekturpunkte: #777781 auf #131318 erreicht 4,18:1, #666670 3,26:1. Beides liegt für normalen Text unter dem WCAG-AA-Ziel von 4,5:1. Dies betrifft im Dashboard kleine Labels und Detailtexte, keine bloßen dekorativen Linien. #a1a1aa auf derselben Fläche erreicht zum Vergleich etwa 7,23:1. Die Messung ist eine Farbpaarprüfung, keine Prüfung aller zusammengesetzten Oberflächen.

WCAG 2.2 verlangt für normale Texte grundsätzlich 4,5:1, für große Texte 3:1, mit definierten Ausnahmen. Quelle: [W3C, Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

Für Touchflächen empfehle ich mindestens 44 × 44 CSS-Pixel bei häufigen mobilen Aktionen. Das ist eine Designempfehlung; WCAG 2.2 AA nennt bei Target Size Minimum 24 × 24 CSS-Pixel oder entsprechende Abstände, mit Ausnahmen. Quelle: [W3C, Target Size Minimum](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html).

Ein `role="dialog"` und `aria-modal="true"` allein liefern noch keine Fokusführung. Fokus beim Öffnen hineinbewegen, Tab/Shift+Tab im Dialog halten, Escape berücksichtigen und beim Schließen zum Auslöser zurückgeben. Dies folgt dem [W3C-Dialogmuster](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/). Im geprüften Ertragsdialog und mobilen Menü fehlt die Fokusübernahme; der Ertragsdialog und Miner-Suchdialog reagieren nicht auf Escape.

Weitere Abnahmeaufgaben: alle Iconaktionen verständlich beschriften, Focus-visible sicherstellen, Skiplink und sinnvolle Landmarken, Tabs semantisch und per Tastatur führen, Fehlermeldungen zu Feldern zuordnen, Sprache des Dokuments synchronisieren. Der aktive Vite-Einstieg setzt `lang="de"`; eine globale Synchronisierung nach Englisch ist im gelesenen Präferenzcode nicht erkennbar. Das Next-Metadaten-Template ist für den aktiven Vite-Einstieg nicht der belegte Seitentitel und sollte hier nicht als aktueller Browserfehler dargestellt werden.

Mobil sollen Gerätetabellen zu geeigneten Karten werden, Diagramme wenige verständliche Reihen zeigen und wichtige Aktionen ohne langes Suchen erreichbar sein. Horizontal scrollende Fachtabellen können sinnvoll bleiben, benötigen aber eine klare Scroll-Erkennbarkeit und einen zugänglichen Rahmen. Dialoge zusätzlich mit eingeblendeter Softwaretastatur, großen Texten und Landscape prüfen; diese Zustände wurden hier nicht vollständig getestet.

## 14. Lade-, Fehler-, Leer- und Erfolgszustände

Einheitlicher Zustandssatz pro Bereich: initiales Laden, vorhandene Daten, leer, teilweise verfügbar, veraltet, Fehler, Änderung in Bearbeitung, Änderung bestätigt. Die derzeitigen Seiten nutzen unterschiedliche Muster, teilweise nur Konsolenfehler oder Browseralerts.

Startseite: Bei Serverausfall „SolarMiner ist momentan nicht erreichbar“ mit Wiederholen. Ein leeres Anlagenverzeichnis ist ein anderer Zustand und sollte direkt in die erste Einrichtung führen. Bei einer einzelnen vorhandenen Anlage direktes Öffnen oder prominente Anlagenkarte statt verpflichtendem Dropdown; bei mehreren Anlagen Karten mit Name/Status/Letzter Aktivität und Suche.

Dashboard: Unterschied zwischen Live-Abruf, Profildaten und Charts beibehalten, Fehler aber pro Teil anzeigen. Ein letztes erfolgreiches Messdatum muss weiter altern, auch wenn weitere Requests scheitern. Der aktuelle UI-Freshnesswert wird aus der gelieferten `ageSeconds` angezeigt; ohne erfolgreiche Aktualisierung kann „Gerade aktualisiert“ stehen bleiben. Das ist kein Nachweis für unsichere Backendsteuerung, aber ein Vertrauensproblem der Anzeige.

Mining: Fehler des schnellen Live-Pollings werden derzeit nur protokolliert. Betreiber sollten sehen, wenn angezeigte Werte nicht mehr frisch sind. Ein Gerätefehler ist außerdem von bewusster Pause, fehlender externer Steuerung oder Mindestpause zu unterscheiden.

Leerzustände bekommen eine passende Folgeaktion: „Noch kein Miner verbunden → Miner verbinden“, „Noch keine Tagesdaten → Erste Daten werden gesammelt“, „Kein Kurs verfügbar → Bewertung fehlt“. Erfolgreiche Änderungen kurz bestätigen, mit dem tatsächlich gespeicherten Zustand. Schwere Fehler bleiben sichtbar, bis sie behoben oder bewusst geschlossen wurden.

## 15. PV-Profil-Editor für Experten

Der Editor besitzt bereits Suche, Import/Export, lokalen/Community-Katalog, Livewerte, Autosave und eine gesonderte Übernahme in die Testansicht. Diese Funktionen sind anhand des Quellcodes erkennbar, wurden hier nicht vollständig im Browser durchgespielt.

Er gehört in „Erweiterte Einrichtung/Diagnose“, nicht als fünf Protokolllinks auf die normale Startseite. Profil wählen/importieren, Verbindung testen und Messwertzuordnung bearbeiten sollten klar getrennte Schritte sein. Anfänger benötigen nur die geprüfte Geräteverbindung; Register, Datentyp, Skalierung, Formel und Fingerprint sind Expertenaufgaben.

Autosave und Verbindungstest müssen eindeutig beschriftet sein: „Profil lokal gespeichert“ ist weder „an Gerät übertragen“ noch „in aktiver Anlage wirksam“. Änderungen sollten einen letzten gespeicherten Stand und eine Wiederherstellung anbieten. Bei Messwerten Quelle, Rohwert, Skalierung, Ergebnis und Fehler anzeigen. Formelfelder benötigen verständliche Beispiele. Importfehler lokal und konkret erklären.

## 16. Empfohlene Umsetzung

### Etappe 1: Bedienhindernisse und Vertrauen

Navigation bei allen Breiten erreichbar machen; Start-/Dashboard-Fehler korrigieren; Textkontraste und Schriftgrößen verbessern; Dialoge vereinheitlichen; Präferenzen konsistent anwenden; Setup-Entwurf wiederherstellen; Freigabe bewusst wählen lassen; Zahlen-/Einheitenformatierung zentralisieren. Bruttovergleich ehrlich benennen. Dafür ist keine vollständige neue Gestaltung nötig.

Abnahme: relevante Breiten und Vergrößerung, Tastatur, API-503/Timeout, Wiederholen, Reload, Deutsch/Englisch, EUR/USD/CHF und Datum/Zeit. Keine leere Datenlage als echte Null und kein ausgeblendeter notwendiger Menüpunkt.

### Etappe 2: Informationsarchitektur und Dashboard

Vier Hauptbereiche, Anlagenkontext im Header, reduzierte Guthabenleiste, Dashboard mit Betriebsstatus/Entscheidungsgrund, kompakter mobiler Energieansicht und optionalen Details. Mining-Geräte priorisieren, tatsächliche Ergebnisse von Prognosen und Guthaben trennen.

Abnahme: Ein neuer Betreiber findet ohne Hinweise den aktuellen Betriebszustand, den Grund für eine Pause, einen fehlerhaften Miner und den wirklichen Tagesertrag. Die mobile erste Ansicht beantwortet die Betriebsfrage ohne mehrere Bildschirmhöhen Scrollen.

### Etappe 3: Automatisierung und vollständige Erstnutzung

Einfachmodus, verständliche Regelsätze, Einheitentransformation, Simulation, Entscheidungsprotokoll und Checkliste bis zur ersten bestätigten Mining-Aktivität. Nettovergleich erst nach fachlich bestätigtem Modell einführen. Herstellerorientierte Verbindung erst mit zuverlässigen Profilmetadaten.

Abnahme: Regeln können ohne Kenntnis interner Enum-Namen eingerichtet und vor Anwendung erklärt werden. Expertenregeln bleiben verlustfrei bearbeitbar. Hardware- und Poolnachweise erfolgen separat; ein Browserfixture oder erfolgreicher Build beweist sie nicht.

### Etappe 4: Verfeinerung

Helles Thema, weitere Diagramminteraktionen, gespeicherte Ansichten, gezielte Benachrichtigungen und umfassendere Expertenwerkzeuge nach tatsächlicher Nutzung priorisieren. Keine zusätzliche Dashboard-Kachel ohne klaren Nutzungszweck.

## 17. Validierung mit Nutzern

Vorgeschlagene erste Studie: fünf bis acht Betreiber oder Interessenten, mit Mischung aus PV-Kenntnissen und unterschiedlicher Mining-Erfahrung. Die Zahl ist eine praktische Startgröße, kein statistischer Qualitätsnachweis.

Aufgaben: erste Anlage verbinden, Angaben zu unbekannten Tarifen behandeln, Miner einrichten, Pause erklären, Solarüberschussregel ändern/simulieren, tatsächlichen Ertrag von Prognose unterscheiden, verfügbares Guthaben finden und Datenfreigabe zurücknehmen. Keine echten Zahlungen in einem ungesicherten Testablauf.

Messen: unassistierte Aufgabenerfüllung, Zeit, Fehlversuche, Hilfebedarf, falsche Interpretation von Kennzahlen, Verständnis der Regelwirkung und Vertrauen in die Aktualität. Vorher/nachher mit identischen Aufgaben vergleichen. Zielwerte erst aus einem Ausgangstest ableiten; keine Verbesserungsprozente ohne Messung versprechen.

## 18. Code-Evidenz und offene fachliche Voraussetzungen

Die Angaben beziehen sich auf den Arbeitsstand zum Reviewdatum. Zentrale Quellen:

- `react-frontend/spa/main.tsx`: aktives Routing; `index.html`, `app/globals.css`: Titel, Sprache, globaler Font und Animationen.
- `react-frontend/app/page.tsx`: Auswahlstart, Sprachstate, Navigation über Select, Fehlerverhalten, Protokoll-/Walletlinks.
- `react-frontend/app/site/[siteId]/layout.tsx`: sieben Hauptlinks, Einstellungen, Breakpoints, mobile Dialogfunktion, Guthabenaggregation und Stile.
- `react-frontend/app/site/[siteId]/site-preferences-context.tsx`: persistente globale Präferenzen.
- `react-frontend/app/setup/page.tsx`: sechs Schritte, volatile Formdaten, Null-Defaults, Wh-Kapazität, vorausgewählte Freigabe und spätere Miner-Einrichtung.
- `react-frontend/app/site/[siteId]/dashboard/page.tsx`: Early Returns bei Profilprüfung, Energiefluss, kleine/kontrastarme Labels, feste EUR-Ertragskarte, Freshness, Dialog und Tabellen.
- `react-frontend/app/site/[siteId]/mining/page.tsx`: schnelle Liveaktualisierung/Fehlerprotokoll, Inventar/Cluster, Gebührenhochrechnung, Such-/Leistungsdialoge und Einheiten.
- `react-frontend/app/site/[siteId]/mining/targets/page.tsx`: getrennte Ziele/Poolkonten und ausdrückliche Grenzen der Zielbestätigung.
- `react-frontend/app/site/[siteId]/mining/clusters/[clusterName]/config/page.tsx`: DSL-Enums, Modusvorlagen, Bruttomargenregel und Simulation.
- `react-frontend/app/site/[siteId]/mining/miners/[minerId]/page.tsx`: Minerkennzahlen, TH/s/J/TH, Historie und Effizienzstrategie; Quellcodeprüfung, keine vollständige Browserprüfung.
- `react-frontend/app/site/[siteId]/finance/page.tsx`: Lebenszeitkarten vor Zeitraumfilter, Szenarien, Periodenauswertungen und gemischte Übersetzungsstrategien.
- `react-frontend/app/site/[siteId]/earnings/page.tsx`: feste EUR-Währung, Bruttohinweise, Quellen, Diagnose und Tarifvergleich.
- `react-frontend/app/site/[siteId]/finance/wallets/page.tsx`: Watch-only-Information, Datenquellen und Coin-Formatierung.
- `react-frontend/app/lightning-wallet/page.tsx`: separater Sprach-/Währungsstate, Rückweg, Alerts, Form-/Zahlungsauslösung und Fehlerseite.
- `react-frontend/app/config/pv/config-editor.tsx`: Expertenkonfiguration, Autosave und Live-Test.

Neue Entscheidungsgründe, Gerätekriterien, Netto-Prognosen, Profilmetadaten und anlagenübergreifende Guthabenabgrenzung können Backendarbeit oder Vertragsänderungen erfordern. Bei Umsetzung müssen die tatsächlichen Producer/Consumer gemeinsam geprüft und die jeweiligen Wikis aktualisiert werden. Die vorliegende Analyse aktiviert keine neue Coin-/Hardware-/Heizfunktion und begründet keine solche Unterstützung.
