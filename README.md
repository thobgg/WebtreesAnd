# webtreesAnd

Native Android-App für [webtrees](https://webtrees.net/) – Tablet zuerst, mit Lesen **und** Schreiben.
Paket-ID `de.bgghome.webtrees.nativ`. Der gleichnamige WebView-Wrapper
([thobgg/WebtreesAnd](https://github.com/thobgg/WebtreesAnd), auf dem Gerät „webtrees") besteht unverändert weiter.

webtrees hat keine API. Die App spricht deshalb mit einem eigenen webtrees-Modul, das nur in
`modules_v4/` liegt – der webtrees-Kern bleibt unberührt:

| Ordner | Inhalt |
| - | - |
| `app/` | Android-App (Kotlin, Jetpack Compose) |
| `webtrees-module/webtreesand-api/` | das Server-Modul samt API-Beschreibung |
| `tools/prod_check.py` | prüft Modul, Rechte und Abschottung der Bäume gegen einen echten Server |
| `tools/ui.py` | kleine adb-Fernbedienung für UI-Tests |
| `testsite/` | lokale webtrees-Testinstanz mit Demo-Daten (nicht im Git) |
| `testdata/` | Ahnenblatt-Beispielbaum; Zugangsdaten in `*.env` (nicht im Git) |

## Bauen

JDK 21 nötig (wie bei den anderen Projekten):

    JAVA_HOME=/home/thobug/.jdks/jdk-21.0.12+8 ./gradlew :app:assembleDebug --offline

## Gegen die lokale Testinstanz testen

    cd testsite/webtrees && php -S 127.0.0.1:8377 -t .
    adb reverse tcp:8377 tcp:8377        # Gerät erreicht den Rechner unter 127.0.0.1:8377

In der App als Adresse `http://127.0.0.1:8377` eingeben (Klartext-HTTP ist nur im Debug-Build erlaubt).
Login und Bäume: siehe `testsite/README.md`.
