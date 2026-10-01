(() => {
  const preferenceKey = 'solarminer.pc-agent.language';
  const supported = ['de', 'en'];
  const saved = (() => { try { return localStorage.getItem(preferenceKey); } catch (_) { return null; } })();
  const locale = supported.includes(saved) ? saved : (navigator.language || 'en').toLowerCase().startsWith('de') ? 'de' : 'en';
  const translations = {
    'Overview': 'Overview', 'Mining': 'Mining', 'Hardware': 'Hardware', 'Telemetrie': 'Telemetry',
    'Verbinde …': 'Connecting …', 'Aktualisieren': 'Refresh', 'Warte auf Daten': 'Waiting for data',
    'DEIN PC IM ÜBERBLICK': 'YOUR PC AT A GLANCE', 'Mining öffnen →': 'Open mining →',
    'Agent-Status': 'Agent status', 'Lokale Steuerung': 'Local control', 'Monero und Pearl verfügbar': 'Monero and Pearl available',
    'SCHNELLZUGRIFF': 'QUICK ACCESS', 'Dein Arbeitsbereich': 'Your workspace',
    'Miner installieren, konfigurieren und steuern': 'Install, configure and control miners',
    'GPU-Schutzgrenzen und Leistungswerte verwalten': 'Manage GPU safety limits and power values',
    'Sensoren und Messwerte ansehen': 'View sensors and readings', 'MINER': 'MINERS', 'Installationsstatus': 'Installation status',
    'ERTRAGSPROGNOSE': 'EARNINGS FORECAST', 'Voraussichtliche Earnings pro Tag': 'Estimated daily earnings',
    'Brutto · vor Pool-, Miner- und SolarMiner-Gebühren': 'Gross · before pool, miner and SolarMiner fees',
    'TRANSPARENTE GEBÜHREN': 'TRANSPARENT FEES', 'Wo dein Mining-Ertrag hingeht': 'Where your mining yield goes',
    'SolarMiner- und Referrer-Anteile kommen live vom Fee-Backend. Pool- und Miner-Gebühren werden je Coin offen ausgewiesen.': 'SolarMiner and referrer shares are loaded live from the fee backend. Pool and miner fees are shown openly for each coin.',
    'Referral-Key': 'Referral key', 'Lokal speichern': 'Save locally', 'Der SolarMiner Node setzt seinen Referral-Key automatisch erneut, sobald er den Agenten steuert.': 'The SolarMiner Node automatically sets its referral key again when it controls this agent.',
    'STANDARD · KEIN REFERRER': 'DEFAULT · NO REFERRER', 'NICHT GELADEN': 'NOT LOADED', 'Gebührenmodell wird geladen …': 'Loading fee model …',
    'Voraussichtlich für dich': 'Estimated for you', 'unbekannt': 'unknown', 'Poolgebühr': 'pool fee',
    'XMRig Entwickler-Spende': 'XMRig developer donation', 'SRBMiner-MULTI Entwicklergebühr': 'SRBMiner-MULTI developer fee',
    'Eigener/noch nicht katalogisierter Pool': 'Custom / not yet catalogued pool', 'Poolgebühr unbekannt – bitte beim Pool prüfen': 'Pool fee unknown – please check with the pool',
    'Vom SolarMiner Fee-Backend geladen': 'Loaded from the SolarMiner fee backend',
    'WINDOWS SENSORZUGRIFF': 'WINDOWS SENSOR ACCESS', 'LibreHardwareMonitor starten': 'Start LibreHardwareMonitor',
    'Windows benötigt eine Administratorfreigabe, damit der Hardware-Monitor seine Sensoren starten kann.': 'Windows requires administrator approval so the hardware monitor can access its sensors.',
    'LibreHardwareMonitor neu starten': 'Restart LibreHardwareMonitor',
    'Bestätige die Windows-UAC-Abfrage. Ohne Freigabe bleiben Agent und Telemetrie gesperrt.': 'Approve the Windows UAC prompt. Without approval, the agent and telemetry remain blocked.',
    'MINER HINZUFÜGEN': 'ADD MINER', 'Wähle deine Hardware und installiere den passenden Miner.': 'Choose your hardware and install the matching miner.',
    'Hardware wählen': 'Choose hardware', 'Alle': 'All', 'CPU Mining': 'CPU mining', 'GPU Mining': 'GPU mining',
    'LOKALE MINING-ZENTRALE': 'LOCAL MINING HUB', 'CPU- und GPU-Miner können parallel laufen.': 'CPU and GPU miners can run in parallel.',
    'Miner starten': 'Start miner', 'Miner pausieren': 'Pause miner', 'Mining-Status': 'Mining status', 'STATUS': 'STATUS',
    'PROGNOSE / TAG': 'FORECAST / DAY', 'Netzwerkdaten werden geladen': 'Loading network data', 'LEISTUNGSZIEL': 'POWER TARGET',
    'MINER-ÜBERSICHT': 'MINER OVERVIEW', 'Algorithmus': 'Algorithm', 'Installation': 'Installation', 'Konfiguration': 'Configuration', 'Mining-Verbindung': 'Mining connection',
    'PLATTFORMTIPPS': 'PLATFORM TIPS', 'Voraussetzungen & Optimierungen': 'Requirements & optimizations',
    'Ein Haken zeigt eine Einstellung an, die SolarMiner für den Miner aktiviert hat. Ob der Miner sie vollständig nutzen kann, siehst du in seiner Ausgabe.': 'A checkmark indicates a setting SolarMiner enabled for the miner. Check its output to see whether the miner can fully use it.',
    'LIVE-AUSGABE': 'LIVE OUTPUT', 'Miner-Konsole': 'Miner console', 'Konsole': 'Console', 'GPU-Konsole auswählen': 'Select GPU console', 'Lade …': 'Loading …', 'Zum Ende': 'To bottom', 'Gesamtes Log herunterladen': 'Download full log',
    'KONFIGURATION': 'CONFIGURATION', 'Mining einrichten': 'Set up mining', 'Mining-Pool': 'Mining pool', 'SolarMiner-Standard · ohne eigene Auszahlung': 'SolarMiner default · no personal payout',
    'Kryptex · Europa': 'Kryptex · Europe', 'Kryptex · Nordamerika': 'Kryptex · North America', 'Erweitert · eigene Pool-Adresse': 'Advanced · custom pool address',
    'Eigene Pool-Adresse': 'Custom pool address', 'Format: stratum+tcp://host:port oder stratum+ssl://host:port': 'Format: stratum+tcp://host:port or stratum+ssl://host:port',
    'Speichern': 'Save', 'Status wird geladen …': 'Loading status …', 'GPUs auswählen': 'Select GPUs', 'Konfiguration speichern': 'Save configuration',
    'SRBMiner erneut herunterladen': 'Download SRBMiner again', 'SRBMiner hat eine zusätzliche Miner-Gebühr von 2 %.': 'SRBMiner has an additional 2% miner fee.',
    'GPU-Leistungsziel': 'GPU power target', 'Gesamtleistung': 'Total power', 'Übernehmen': 'Apply', 'VERBINDUNG': 'CONNECTION',
    'Status': 'Status', 'Monero-Route': 'Monero route', 'Pearl-Route': 'Pearl route', 'Monero Dev-Fee-Ziel': 'Monero dev-fee target', 'Pearl Dev-Fee-Ziel': 'Pearl dev-fee target',
    'Externer Proxy-Host': 'External proxy host', 'Proxy im Netzwerk suchen': 'Find proxy on network', 'Hardware & Telemetrie öffnen →': 'Open hardware & telemetry →', 'API-Dokumentation ↗': 'API documentation ↗',
    'HARDWARE & SENSOREN': 'HARDWARE & SENSORS', 'Sensoren werden geladen': 'Loading sensors', 'GESAMTLEISTUNG': 'TOTAL POWER', 'Warte auf Messwerte': 'Waiting for readings',
    'PROZESSOR': 'PROCESSOR', 'GRAFIKKARTEN': 'GRAPHICS CARDS', 'Alle erkannten Geräte': 'All detected devices', 'SENSOREN': 'SENSORS', 'GERÄTE': 'DEVICES', 'MESSWERTE': 'READINGS',
    'Alle Telemetriedaten': 'All telemetry data', 'Werte ohne Sensorquelle erscheinen als nicht verfügbar. Gesamtleistung summiert CPU-Package und erkannte GPU-Boardwerte.': 'Values without a sensor source are unavailable. Total power adds CPU package and detected GPU board readings.',
    'JSON-Schnittstelle ↗': 'JSON endpoint ↗', 'Treiberwerte werden vor jeder Änderung geprüft.': 'Driver values are checked before every change.',
    'Mining aktiv': 'Mining active', 'Pausiert': 'Paused', 'Gestoppt': 'Stopped', 'Fehler': 'Error', 'Online': 'Online', 'Offline': 'Offline',
    'Verbunden': 'Connected', 'Nicht erreichbar': 'Unavailable', 'Agent verbunden': 'Agent connected', 'Agent nicht erreichbar': 'Agent unavailable',
    'Lokaler Dienst': 'Local service', 'Externer SolarMiner-Proxy': 'External SolarMiner proxy', 'Installiert': 'Installed', 'Noch nicht installiert': 'Not installed',
    'DATEN VERALTET': 'STALE DATA', 'LIVE-SCHÄTZUNG': 'LIVE ESTIMATE', 'NOCH NICHT VERFÜGBAR': 'NOT AVAILABLE YET', 'Marktdaten werden geladen': 'Loading market data',
    'Nicht verfügbar': 'Unavailable', 'Keine GPU erkannt.': 'No GPU detected.', 'Keine GPU vom Treiber erkannt.': 'No GPU was detected by the driver.',
    'Wähle mindestens eine GPU.': 'Select at least one GPU.', 'Keine unterstützte GPU erkannt.': 'No supported GPU detected.',
    'Miner geöffnet': 'Miner opened', 'Miner starten': 'Start miner', 'Miner pausieren': 'Pause miner', 'Miner gestartet.': 'Miner started.', 'Miner pausiert.': 'Miner paused.',
    'Proxy gespeichert.': 'Proxy saved.', 'SRBMiner-Download gestartet.': 'SRBMiner download started.', 'XMRig-Download gestartet.': 'XMRig download started.'
    , 'SCHNELLSTART': 'QUICK START', 'Lokal starten – Node später hinzufügen': 'Start locally – add a Node later',
    'Der PC-Agent funktioniert eigenständig. Ein SolarMiner Node ergänzt später Automatisierung, PV-Überschuss und weitere Homelab-Geräte.': 'The PC agent works on its own. Add a SolarMiner Node later for automation, PV surplus and additional homelab devices.',
    'Hardware ansehen und einen CPU- oder GPU-Miner auswählen.': 'Review your hardware and choose a CPU or GPU miner.',
    'Miner installiert – Pool und eigene Wallet einrichten.': 'Miner installed – set up a pool and your own wallet.',
    'Passenden Miner herunterladen und installieren.': 'Download and install a suitable miner.',
    'Eigene Auszahlung ist konfiguriert.': 'Your own payout is configured.',
    'Eigene Wallet eintragen oder das SolarMiner-Standardziel bewusst bestätigen.': 'Enter your own wallet or explicitly confirm the SolarMiner standard destination.',
    'Optional: SolarMiner Node verbinden für Regeln, PV-Überschuss und Automatisierung.': 'Optional: connect a SolarMiner Node for rules, PV surplus and automation.',
    'Node: Mining ist aktuell wirtschaftlich freigegeben.': 'Node: mining is currently approved as economical.',
    'Node: Mining ist aktuell nicht wirtschaftlich freigegeben.': 'Node: mining is currently not approved as economical.',
    'Node: Noch keine Wirtschaftlichkeitsbewertung.': 'Node: no economic assessment yet.',
    'Ich bestätige: Ohne eigene Wallet geht meine gesamte Mining-Auszahlung an das angezeigte SolarMiner-Standardziel. Die vollständigen Gebühren stehen unten.': 'I confirm: without my own wallet, my entire mining payout goes to the displayed SolarMiner standard destination. The complete fees are listed below.',
    'Bestätige zuerst die Auszahlung an das SolarMiner-Standardziel.': 'First confirm the payout to the SolarMiner standard destination.',
    'Auszahlung an das SolarMiner-Standardziel': 'Payout to the SolarMiner standard destination',
    'Kein SolarMiner-Standardziel erreichbar. Eigene Wallet angeben oder Proxy prüfen.': 'No SolarMiner standard destination is available. Enter your own wallet or check the proxy.',
    'Monero gespeichert: Auszahlung an das SolarMiner-Standardziel.': 'Monero saved: payout to the SolarMiner standard destination.',
    'Pearl gespeichert: Auszahlung an das SolarMiner-Standardziel.': 'Pearl saved: payout to the SolarMiner standard destination.',
    'Alle bekannten Abzüge werden live aufgeschlüsselt. SolarMiner nutzt eine verpflichtende Fee-Route; ist sie nicht erreichbar, startet der betreffende Miner nicht. Pool- und Miner-Gebühren sind zusätzlich ausgewiesen.': 'All known deductions are broken down live. SolarMiner uses a mandatory fee route; if it is unavailable, the affected miner will not start. Pool and miner fees are listed separately.'
    , 'Empfohlen': 'Recommended', 'Voraussetzung': 'Requirement', 'Hinweis': 'Note', 'Optional · Linux': 'Optional · Linux',
    'Huge Pages für RandomX aktivieren': 'Enable Huge Pages for RandomX', '1-GB-Pages für RandomX aktivieren': 'Enable 1 GB pages for RandomX',
    'Kompatible GPU und Treiber': 'Compatible GPU and driver', 'Miner-Ausgabe': 'Miner output', 'Dokumentation öffnen ↗': 'Open documentation ↗',
    'Keine CPU-Sensorwerte verfügbar': 'No CPU sensor readings available', 'Keine Grafikkarte erkannt': 'No graphics card detected',
    'Leistungsmessung nicht verfügbar': 'Power measurement unavailable', 'Quelle unbekannt': 'Unknown source', 'direkte Messung': 'direct measurement', 'abgeleitet / geschätzt': 'derived / estimated',
    'Sensorzugriff bereit': 'Sensor access ready', 'Noch nicht gestartet': 'Not started', 'Nicht ausgewählt': 'Not selected', 'Manuell pausiert': 'Paused manually',
    'Verbinde mit Pool': 'Connecting to pool', 'Bereit zum Starten': 'Ready to start', 'Alle GPUs': 'All GPUs', 'GPU starten': 'Start GPU', 'GPU pausieren': 'Pause GPU',
    'Läuft auf GPU(s)': 'Running on GPU(s)', 'Installation fehlgeschlagen': 'Installation failed', 'Miner öffnen': 'Open miner', 'Wird installiert …': 'Installing …',
    'Erneut versuchen': 'Try again', 'Herunterladen & installieren': 'Download & install', 'Für diesen Hardware-Filter ist kein Miner verfügbar.': 'No miner is available for this hardware filter.',
    'Aktueller Miner-Wert': 'Current miner value', 'Keine Hashrate gemeldet': 'No hashrate reported', 'Noch kein Leistungsziel': 'No power target yet',
    'Pool und Wallet konfiguriert': 'Pool and wallet configured', 'Pool und Wallet fehlen': 'Pool and wallet are missing', 'Verbindung wird geprüft': 'Checking connection',
    'Lokaler Mining-Dienst bereit': 'Local mining service ready', 'SolarMiner-Proxy erreichbar': 'SolarMiner proxy reachable',
    'Bitte bestätige die Windows-Sicherheitsabfrage. Der PC-Agent wartet auf die Administratorfreigabe.': 'Please approve the Windows security prompt. The PC agent is waiting for administrator approval.',
    'LibreHardwareMonitor läuft nicht. Windows benötigt eine Administratorfreigabe, um den Hardware-Monitor neu zu starten.': 'LibreHardwareMonitor is not running. Windows requires administrator approval to restart the hardware monitor.',
    'Warte auf Windows-Freigabe …': 'Waiting for Windows approval …', 'Windows-Freigabe wird angefordert …': 'Requesting Windows approval …'
  };
  function translate(value) {
    if (locale !== 'en' || typeof value !== 'string') return value;
    if (translations[value]) return translations[value];
    const patterns = [
      [/^Aktualisiert (.+)$/, 'Updated $1'], [/^Stand (.+)$/, 'As of $1'], [/^Download (.+) %$/, 'Download $1%'],
      [/^Aktiviert (.+)$/, 'Enabled $1'], [/^Maximal (.+)$/, 'Maximum $1'],
      [/^Auszahlung an das SolarMiner-Standardziel \((.+)\)\. Ohne eigene Wallet geht die gesamte Hashrate dorthin\.$/, 'Payout to the SolarMiner standard destination ($1). Without your own wallet, all hashrate goes there.'],
      [/^Daten konnten nicht geladen werden: (.+)$/, 'Could not load data: $1'],
      [/^Agent-Daten konnten nicht geladen werden: (.+)$/, 'Could not load agent data: $1'],
      [/^Hardwaredaten konnten nicht geladen werden: (.+)$/, 'Could not load hardware data: $1'],
      [/^Telemetrie konnte nicht geladen werden: (.+)$/, 'Could not load telemetry: $1'],
      [/^Konsole nicht erreichbar: (.+)$/, 'Console unavailable: $1'], [/^Status konnte nicht geladen werden: (.+)$/, 'Could not load status: $1']
    ];
    for (const [pattern, replacement] of patterns) if (pattern.test(value)) return value.replace(pattern, replacement);
    return value;
  }
  window.SolarMinerI18n = { locale: locale === 'de' ? 'de-DE' : 'en-US', language: locale, t: translate };
  document.documentElement.lang = locale;
  function translateTree(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = []; while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(node => { const translated = translate(node.nodeValue); if (translated !== node.nodeValue) node.nodeValue = translated; });
    if (root instanceof Element) ['title', 'placeholder', 'aria-label'].forEach(attribute => {
      if (root.hasAttribute(attribute)) root.setAttribute(attribute, translate(root.getAttribute(attribute)));
    });
  }
  document.addEventListener('DOMContentLoaded', () => {
    document.title = translate(document.title);
    translateTree(document.body);
    document.querySelectorAll('[title], [placeholder], [aria-label]').forEach(element =>
      ['title', 'placeholder', 'aria-label'].forEach(attribute => {
        if (element.hasAttribute(attribute)) element.setAttribute(attribute, translate(element.getAttribute(attribute)));
      }));
    const switcher = document.createElement('select');
    switcher.className = 'button subtle language-switcher'; switcher.setAttribute('aria-label', locale === 'de' ? 'Sprache wählen' : 'Choose language');
    switcher.innerHTML = '<option value="de">Deutsch</option><option value="en">English</option>'; switcher.value = locale;
    switcher.addEventListener('change', () => { try { localStorage.setItem(preferenceKey, switcher.value); } catch (_) {} location.reload(); });
    document.querySelector('.top-actions')?.prepend(switcher);
    new MutationObserver(records => records.forEach(record => record.addedNodes.forEach(node => {
      if (node.nodeType === Node.TEXT_NODE) { const translated = translate(node.nodeValue); if (translated !== node.nodeValue) node.nodeValue = translated; }
      else if (node.nodeType === Node.ELEMENT_NODE) translateTree(node);
    }))).observe(document.body, { childList: true, subtree: true });
  });
})();
