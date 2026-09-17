# Design-Vorbild: MyHeritage-App

Recherche vom 17.09.2026. Quellen: MyHeritage-Blog zur Baum-Neugestaltung (März 2025), Play-Store-Eintrag,
MyHeritage-Hilfeseiten, „The Family History Guide" (Schritt-für-Schritt-Anleitungen zur App), Nutzerrezensionen.
**[B]** = belegt, **[S]** = aus offiziellen Screenshots abgelesen (Farben per Pixelprobe), **[A]** = Annahme.

Übernommen werden **Bedienmuster**, nicht das Branding (kein Logo, kein Name, das Orange nicht als Markenzeichen).

## Navigation
- [S] Untere Leiste mit 5 Reitern: Home · Tree · Discoveries · DNA · Photos – Umriss-Symbole, Beschriftung immer
  sichtbar, aktiver Reiter orange. Für uns: **Start · Baum · Fotos · Suche** (Discoveries/DNA sind MyHeritage-Dienste).
- [S] Kopfzeile des Baums: **Baumname mit ▾ zum Wechseln**, darunter „178 Personen"; rechts Umschalter der Ansicht.
- [S] Direkt darunter: Suchfeld **„Person finden …"**, daneben Generationen-Chip („5+ ▾") und Zahnrad.
- [B] Startseite: Titelbild, Neuigkeiten-Karussells und ein **„Verwandte hinzufügen"-Baustein**.

## Baum
- [B] Ansichten: **Familienansicht** (Standard), **Ahnentafel** (links → rechts), **Liste** (sortierbar nach
  Verwandtschaft, Vor-/Nachname, zuletzt hinzugefügt).
- [B] Seit 2025 **hochformatige Karten** als Standard (quer wählbar); Geschlecht nur als **dünner farbiger Rahmen**,
  Vollfarbe wählbar.
- [S] Karte: rundes Foto oben mittig, Name fett, „(geb. X)" in eigener Zeile, Jahre grau; weiß, Ecken ~4 dp,
  Rahmen ~1 dp, Seitenverhältnis ~0,78.
- [S] Farben: Rahmen ♂ `#54B3CB` / ♀ `#ED8783`; Vollfarbe ♂ `#CBEBF9` / ♀ `#F9DBD9`; Fläche `#F1F1F1`;
  Linien `#A0A0A0`, 1 px, rechtwinklig mit **runden Ecken**; Partner an der Unterkante verbunden, Kinder hängen an
  einer runden Klammer in der Paarmitte. Mittelperson: weißer Rahmen + Schatten.
- [S] **„+"-Lasche an der Unterkante jeder Karte** → Verwandte hinzufügen (dann Art wählen) oder Foto hinzufügen. [B]
- [B] **Geisterkarten „Vater/Mutter hinzufügen"** (abschaltbar); kleines **Zweig-Symbol** über einer Karte, wenn deren
  Eltern eingeklappt sind – antippen klappt den Zweig auf.
- [B] Antippen: Karte wird zentriert, ein **Personen-Panel** öffnet sich (Im Baum zeigen · Foto hinzufügen ·
  Einträge suchen · Bearbeiten); Tipp daneben schließt es. Zwei Finger zoomen, Wischen verschiebt.
- [B] Einstellungen als Blatt von unten: Verstorbenen-Schleife, Farbcodierung der Zweige, Vollfarbe, Hochkant-Karten,
  Eltern-Geisterkarten. Generationen-Blatt: Regler 1–5+, „Cousins der Hauptperson zeigen".
- Nicht belegt: Minikarte, langes Drücken, eigener „Home"-Knopf.

## Profil
- [S] Rundes Foto mittig, Name mit „(geb. X)", darunter **„Urgroßmutter | 1910–1999"** (Verwandtschaft zu mir + Jahre),
  zwei Pillen-Knöpfe, Reiter **FAKTEN · FOTOS · VERWANDTE**.
- [S] Fakten als **Zeitleiste: große Jahreszahl links**, Ereignistitel, graues Datum; Familienereignisse eingereiht
  („Geburt des Sohnes: Andrew", „Heirat: Joseph Glass"). Runder Aktionsknopf unten rechts.
- [B] Aktionsknopf: Fakt, Foto, Tonaufnahme, Verwandte hinzufügen; ⋮ → „Bearbeiten". Tipp auf die Silhouette fügt ein
  Foto hinzu. Reiter Verwandte: Partner, Kinder, Geschwister, Großeltern, Onkel/Tanten, Schwiegerfamilie.
- [S] **Tablet: zweispaltig – Profil links (~40 %), Baum rechts.**

## Hinzufügen / Bearbeiten
- [B] Felder: Geschlecht, Name, Geburts-/Sterbedatum, **Umschalter lebend/verstorben**; Speichern = Haken oben rechts;
  „Diese Person löschen" ganz unten. Datum per Auswahl, Ort per Vorschlagsliste. Faktenarten in „Häufig" und „Alle".

## Fotos
- [B] Alben, Mehrfachauswahl, Titel vor dem Hochladen; **Personen im Foto markieren** („Wer ist das?", Name tippen,
  aus Liste wählen); Scanner mit Eckenziehen, Stil Dokument/Lebhaft, Drehen.

## Formensprache
- [B] Orange der Website: `#D14900` (Hauptton), `#F56932`, Abstufungen bis `#FFF5F4`; Text `#595959`, Flächen `#F7F7F7`,
  Trennlinien `#E5E5E5`. [S] Roboto, Abschnittstitel in gesperrten GROSSBUCHSTABEN, weiße Flächen auf hellgrauem
  Grund, Haarlinien, kaum Schatten, Pillen-Knöpfe; runde Avatare, **graue Silhouetten ♂/♀** ohne Foto;
  schwarze Eck-Schleife für Verstorbene. Dunkelmodus nicht belegt.

## Was Nutzer dort ärgert (vermeiden)
„Etwas ist schiefgelaufen" beim Speichern ohne Erklärung · Baumposition/Anmeldung geht beim App-Wechsel verloren ·
breite Bäume werden unhandlich (Wunsch: nur direkte Vorfahren) · kein PDF-Export · kein Split-Screen ·
langsame Synchronisation · aufdringliche Abo-Werbung.
