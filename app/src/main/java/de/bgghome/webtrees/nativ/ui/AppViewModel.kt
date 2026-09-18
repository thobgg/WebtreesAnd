package de.bgghome.webtrees.nativ.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.webtrees.nativ.BuildConfig
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.WtApp
import de.bgghome.webtrees.nativ.api.AddIndividualRequest
import de.bgghome.webtrees.nativ.api.Descendants
import de.bgghome.webtrees.nativ.api.FactRequest
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.Info
import de.bgghome.webtrees.nativ.api.NotJsonException
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.api.TreeInfo
import de.bgghome.webtrees.nativ.api.WriteResult
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.ImagePrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Das eine View-Model der App: haelt den UiState und spricht ueber WtClient mit dem Server.
 *
 * Gliederung: Einstieg (Adresse, Anmelden, Koppeln) -> Baum waehlen -> Personenliste -> Baum und Profil-Panel
 * -> Fotos -> Schreiben -> Fehler. Jeder Netzaufruf laeuft in viewModelScope; Ergebnisse kommen nur an, wenn der
 * Zustand noch zu ihnen passt (z. B. ist die Suche inzwischen eine andere, wird die Antwort verworfen).
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Kleinste API-Version des Server-Moduls, mit der diese App arbeiten kann (Feld "api" in Info). */
        const val MIN_API = 1
        /** So viele Generationen kommen je Tipp auf "weiter nach oben" dazu (die angetippte Person mitgezaehlt). */
        const val EXPAND_GENERATIONS = 3
        /** Nennt der Server sein Upload-Limit nicht: der PHP-Standard von 2 MB. */
        const val DEFAULT_MAX_UPLOAD = 2L * 1024 * 1024
        /** Ab dieser API-Stufe: Jahrestage, Datensatz loeschen, Verknuepfung loesen. */
        const val API_ANNIVERSARIES = 4
        /** Ab dieser API-Stufe kennt das Modul MediaList, relationship und die Personenzahl. */
        const val API_PHOTOS = 2
        const val DESCENDANT_GENERATIONS = 3
    }

    private val app = application as WtApp
    private val client = app.client
    private val settings = app.settings

    private val _state = MutableStateFlow(UiState(baseUrl = settings.baseUrl, userName = settings.userName))
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null

    init {
        start()
    }

    // ── Einstieg ─────────────────────────────────────────────────────

    private fun start() {
        if (settings.baseUrl.isEmpty()) {
            _state.update { it.copy(screen = Screen.Setup) }
            return
        }

        viewModelScope.launch {
            try {
                applyInfo(client.info())
            } catch (e: Exception) {
                // Server gerade nicht erreichbar o. ae.: zur Adress-Eingabe, Adresse bleibt vorbelegt.
                _state.update { it.copy(screen = Screen.Setup, error = explain(e)) }
            }
        }
    }

    fun submitUrl(input: String) {
        if (rejectCleartext(input)) {
            _state.update { it.copy(error = text(R.string.err_http_only)) }
            return
        }

        client.baseUrl = input
        val url = client.baseUrl

        if (url.isEmpty()) return

        _state.update { it.copy(busy = true, error = null, baseUrl = url) }

        viewModelScope.launch {
            try {
                val info = client.info()
                settings.baseUrl = url
                applyInfo(info)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = explain(e)) }
            }
        }
    }

    fun login(user: String, password: String) {
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            try {
                val info = client.login(user.trim(), password)

                if (info.user.loggedIn) {
                    settings.userName = user.trim()
                    _state.update { it.copy(userName = user.trim()) }
                    applyInfo(info)
                } else {
                    _state.update { it.copy(busy = false, error = text(R.string.login_failed)) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = explain(e)) }
            }
        }
    }

    /**
     * "Verbinden" aus webtrees (Link webtreesand://connect): erst nachfragen. Einen solchen Link kann jede Webseite
     * und jeder QR-Code ausloesen - ohne Rueckfrage liesse sich die App still an einen fremden Server binden und
     * eine bestehende Anmeldung ersetzen. Eingeloest wird der Code erst in confirmConnect().
     */
    fun connect(url: String, tree: String, code: String, user: String) {
        if (url.isBlank() || code.isBlank()) return

        if (rejectCleartext(url)) {
            _state.update { it.copy(screen = Screen.Setup, busy = false, error = text(R.string.err_http_only)) }
            return
        }

        _state.update { it.copy(pendingConnect = ConnectRequest(url = url.trim(), tree = tree, code = code, user = user)) }
    }

    fun cancelConnect() = _state.update { it.copy(pendingConnect = null) }

    /**
     * http:// wird abgelehnt - ausser im Debug-Build, der Klartext erlaubt (debug/AndroidManifest.xml), damit die
     * lokale Testinstanz (php -S) erreichbar bleibt. Im Release blockiert Android Klartext ohnehin.
     */
    private fun rejectCleartext(input: String): Boolean = !BuildConfig.DEBUG && WtClient.isCleartext(input)

    /** Adresse setzen, Einmal-Code einloesen, Baum oeffnen. Eine bestehende Anmeldung an einem anderen Server wird ersetzt. */
    fun confirmConnect() {
        val request = _state.value.pendingConnect ?: return

        client.cookieJar.clear()
        client.baseUrl = request.url
        _state.update { UiState(screen = Screen.Loading, baseUrl = client.baseUrl, busy = true) }

        viewModelScope.launch {
            try {
                val paired = client.pair(request.code)
                settings.baseUrl = client.baseUrl
                settings.userName = paired.user
                settings.tree = paired.tree.ifEmpty { request.tree }
                _state.update { it.copy(userName = paired.user) }
                applyInfo(client.info())
            } catch (e: Exception) {
                _state.update { it.copy(screen = Screen.Setup, busy = false, error = explain(e)) }
            }
        }
    }

    /** Ohne Anmeldung weiter - zeigt, was Besucher sehen duerfen. */
    fun continueAsGuest() {
        val info = _state.value.info ?: return
        if (info.trees.isNotEmpty()) showTrees(info)
    }

    fun showLogin() = _state.update { it.copy(screen = Screen.Login, error = null) }

    fun changeServer() = _state.update { it.copy(screen = Screen.Setup, error = null) }

    fun logout() {
        viewModelScope.launch {
            client.logout()
            settings.tree = ""
            _state.update { UiState(screen = Screen.Login, baseUrl = settings.baseUrl, userName = settings.userName) }
            runCatching { client.info() }.onSuccess { info -> _state.update { it.copy(info = info) } }
        }
    }

    private fun applyInfo(info: Info) {
        // Aelteres Modul als diese App braucht: klare Ansage statt spaeter raetselhafter Fehler.
        if (info.api < MIN_API) {
            _state.update {
                it.copy(info = info, busy = false, screen = Screen.Setup, error = text(R.string.err_module_too_old, info.module))
            }
            return
        }

        _state.update { it.copy(info = info, busy = false, error = null) }

        if (!info.user.loggedIn) {
            _state.update { it.copy(screen = Screen.Login) }
            return
        }

        showTrees(info)
    }

    private fun showTrees(info: Info) {
        val remembered = info.trees.firstOrNull { it.name == settings.tree }
        val only = info.trees.singleOrNull()

        when {
            remembered != null -> chooseTree(remembered)
            only != null -> chooseTree(only)
            else -> _state.update { it.copy(screen = Screen.Trees) }
        }
    }

    fun showTreePicker() = _state.update { it.copy(screen = Screen.Trees) }

    fun chooseTree(tree: TreeInfo) {
        settings.tree = tree.name
        val home = tree.userXref.ifEmpty { tree.defaultXref }.ifEmpty { null }

        _state.update {
            UiState(
                screen = Screen.Main, baseUrl = it.baseUrl, userName = it.userName, info = it.info,
                tree = tree, home = home, section = Section.Tree, ancestorGenerations = it.ancestorGenerations,
                reminders = settings.reminders,
            )
        }
        loadPeople(reset = true)

        // "Das bin ich": mit der eigenen Person starten, sonst mit der Startperson des Baums.
        // Gibt es keine, wird die erste sichtbare Person genommen, sobald die Liste da ist (loadPeople).
        if (home != null) setRoot(home, remember = false)

        loadAnniversaries()
        loadPending()

        if (tree.canEdit) {
            viewModelScope.launch {
                runCatching { client.tags(tree.name, "INDI") }
                    .onSuccess { list -> _state.update { it.copy(tags = list.data) } }
                runCatching { client.tags(tree.name, "FAM") }
                    .onSuccess { list -> _state.update { it.copy(familyTags = list.data) } }
            }
        }
    }

    private var layoutKnown = false

    /** Handy kompakter: beim ersten Start nur 3 Ahnen-Generationen statt 4 (spaeter frei waehlbar). */
    fun setWide(wide: Boolean) {
        if (layoutKnown) return
        layoutKnown = true
        if (!wide) _state.update { it.copy(ancestorGenerations = 3) }
    }

    fun setSection(section: Section) {
        _state.update { it.copy(section = section, treeFullscreen = false, profileOpen = false) }
        if (section == Section.Photos && !_state.value.mediaLoaded) loadMedia(reset = true)
    }

    private fun loadPending() {
        val tree = _state.value.tree ?: return
        if (!tree.canModerate) return

        viewModelScope.launch {
            runCatching { client.pending(tree.name) }.onSuccess { list -> _state.update { it.copy(pending = list.data) } }
        }
    }

    /** Freigabe: xref = null heisst "alle". Danach zeigen Baum und Profil den neuen Stand. */
    fun moderate(xref: String?, accept: Boolean) {
        val tree = _state.value.tree ?: return

        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            try {
                client.moderate(tree.name, xref, accept)
                val message = text(if (accept) R.string.msg_accepted else R.string.msg_rejected)
                _state.update { it.copy(busy = false, message = message, pedigree = null, descendants = null) }
                loadPending()
                loadPeople(reset = true)

                // Eine verworfene neue Person gibt es nicht mehr - dann nicht im Profil stehen lassen.
                val selected = _state.value.selected
                if (selected != null) select(selected, byTap = _state.value.quickCard)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                fail(e)
            }
        }
    }

    val anniversariesSupported: Boolean get() = (_state.value.info?.api ?: 0) >= API_ANNIVERSARIES

    private fun loadAnniversaries() {
        val tree = _state.value.tree ?: return
        if (!anniversariesSupported) return

        viewModelScope.launch {
            runCatching { client.anniversaries(tree.name, 14) }
                .onSuccess { list -> _state.update { it.copy(anniversaries = list.data) } }
        }
    }

    /** Taegliche Erinnerung ein-/ausschalten. Die Erlaubnis fuer Benachrichtigungen holt die Oberflaeche vorher ein. */
    fun setReminders(on: Boolean) {
        settings.reminders = on
        _state.update { it.copy(reminders = on) }
        AnniversaryWorker.schedule(getApplication(), on)
    }

    val photosSupported: Boolean get() = (_state.value.info?.api ?: 0) >= API_PHOTOS

    // ── Liste ────────────────────────────────────────────────────────

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadPeople(reset = true)
        }
    }

    fun loadMore() {
        if (_state.value.nextPage != null && !_state.value.loadingPeople) loadPeople(reset = false)
    }

    private fun loadPeople(reset: Boolean) {
        val tree = _state.value.tree ?: return
        val page = if (reset) 1 else _state.value.nextPage ?: return
        val query = _state.value.query

        _state.update { it.copy(loadingPeople = true) }

        viewModelScope.launch {
            try {
                val result = client.individuals(tree.name, query, page)
                _state.update {
                    // Antwort einer ueberholten Suche verwerfen
                    if (it.query != query) it else it.copy(
                        people = if (reset) result.data else it.people + result.data,
                        nextPage = result.nextPage,
                        loadingPeople = false,
                    )
                }
                if (_state.value.root == null && query.isEmpty()) {
                    result.data.firstOrNull { !it.isPrivate }?.let { first ->
                        _state.update { it.copy(home = it.home ?: first.xref) }
                        setRoot(first.xref, remember = false)
                    }
                }
            } catch (e: Exception) {
                fail(e)
                _state.update { it.copy(loadingPeople = false) }
            }
        }
    }

    // ── Baum und Profil-Panel ────────────────────────────────────────

    /** Neue Mittelperson des Baums; sie erscheint auch im Profil-Panel. */
    fun setRoot(xref: String, remember: Boolean = true) {
        _state.update {
            val history = if (remember && it.root != null && it.root != xref) it.rootHistory + it.root else it.rootHistory
            it.copy(root = xref, rootHistory = history, pedigree = null, descendants = null, section = Section.Tree, profileOpen = false)
        }
        select(xref, byTap = false)
    }

    /** Person ins Profil-Panel holen (Tipp auf eine Karte). Die Mittelperson des Baums bleibt. */
    fun select(xref: String, byTap: Boolean = true) {
        val tree = _state.value.tree ?: return
        val home = _state.value.home

        _state.update { it.copy(selected = xref, loadingDetail = true, quickCard = byTap) }

        viewModelScope.launch {
            try {
                val detail = client.individual(tree.name, xref, relativeTo = home.takeIf { it != xref }.orEmpty())
                _state.update {
                    if (it.selected != xref) it else it.copy(
                        detail = detail, loadingDetail = false,
                        recent = (listOf(detail.person) + it.recent.filter { p -> p.xref != xref }).take(12),
                    )
                }
            } catch (e: Exception) {
                fail(e)
                _state.update { it.copy(loadingDetail = false, addRelativeFor = null) }
            }
        }
    }

    fun closePanel() = _state.update {
        it.copy(selected = null, detail = null, profileOpen = false, addRelativeFor = null, quickCard = false)
    }

    /** Tipp ins Leere: die Kurzkarte verschwindet, die Auswahl (und am Tablet das Profil) bleibt. */
    fun hideQuickCard() = _state.update { it.copy(quickCard = false) }

    fun openProfile() = _state.update { it.copy(profileOpen = true) }

    fun setAncestorGenerations(generations: Int) {
        _state.update { it.copy(ancestorGenerations = generations, pedigree = null, descendants = null) }
    }

    /** Daten fuer den Baum: Ahnen und Nachkommen der Mittelperson, beide Anfragen gleichzeitig. */
    fun loadChart() {
        val tree = _state.value.tree ?: return
        val xref = _state.value.root ?: return
        val generations = _state.value.ancestorGenerations

        viewModelScope.launch {
            try {
                // coroutineScope: scheitert eine der beiden Anfragen, landet der Fehler unten im catch.
                val (p, d) = coroutineScope {
                    val pedigree = async { client.pedigree(tree.name, xref, generations) }
                    val descendants = async { client.descendants(tree.name, xref, DESCENDANT_GENERATIONS) }
                    pedigree.await() to descendants.await()
                }
                _state.update { if (it.root == xref) it.copy(pedigree = p, descendants = d) else it }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /**
     * "Weiter nach oben": die Ahnen von Platz n nachladen und in den Baum einhaengen.
     * Im nachgeladenen Teilbaum ist diese Person die Nummer 1; ihr Platz m wird zu n * 2^g + (m - 2^g),
     * wobei g die Generation von m ist.
     */
    fun expandAncestors(n: Int, xref: String) {
        val tree = _state.value.tree ?: return
        val root = _state.value.root ?: return

        viewModelScope.launch {
            try {
                val branch = client.pedigree(tree.name, xref, EXPAND_GENERATIONS)
                _state.update { state ->
                    val pedigree = state.pedigree
                    if (state.root != root || pedigree == null) return@update state

                    val added = branch.ancestors.filter { it.n > 1 }.map { ancestor ->
                        val g = 31 - Integer.numberOfLeadingZeros(ancestor.n)
                        ancestor.copy(n = n * (1 shl g) + (ancestor.n - (1 shl g)))
                    }
                    val known = pedigree.ancestors.map { it.n }.toSet()
                    state.copy(pedigree = pedigree.copy(ancestors = pedigree.ancestors + added.filter { it.n !in known }))
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun setDetailTab(tab: Int) = _state.update { it.copy(detailTab = tab) }

    fun setTreeFullscreen(on: Boolean) = _state.update { it.copy(treeFullscreen = on) }

    /** "+" an einer Karte: Details der Person holen; der Dialog oeffnet sich, sobald sie da sind. */
    fun requestAddRelative(xref: String) {
        _state.update { it.copy(addRelativeFor = xref) }
        if (_state.value.detail?.person?.xref != xref) select(xref, byTap = _state.value.quickCard)
    }

    fun addRelativeHandled() = _state.update { it.copy(addRelativeFor = null) }

    /** Zurueck innerhalb der App. false = nichts mehr zu tun, das System darf die App schliessen. */
    fun back(): Boolean {
        val state = _state.value

        return when {
            state.screen != Screen.Main -> false
            state.treeFullscreen -> { _state.update { it.copy(treeFullscreen = false) }; true }
            state.profileOpen -> { _state.update { it.copy(profileOpen = false) }; true }
            state.section == Section.Tree && state.rootHistory.isNotEmpty() -> {
                val previous = state.rootHistory.last()
                _state.update { it.copy(rootHistory = it.rootHistory.dropLast(1)) }
                setRoot(previous, remember = false)
                true
            }
            state.section != Section.Home -> { _state.update { it.copy(section = Section.Home) }; true }
            else -> false
        }
    }

    fun canGoBack(): Boolean = _state.value.let {
        it.screen == Screen.Main &&
            (it.treeFullscreen || it.profileOpen || it.section != Section.Home || it.rootHistory.isNotEmpty())
    }

    fun refresh() {
        _state.update { it.copy(pedigree = null, descendants = null) }
        _state.value.selected?.let { select(it, byTap = _state.value.quickCard) }
        loadPeople(reset = true)
        if (_state.value.mediaLoaded) loadMedia(reset = true)
        loadAnniversaries()
        loadPending()
    }

    // ── Fotos ────────────────────────────────────────────────────────

    fun loadMoreMedia() {
        if (_state.value.mediaNextPage != null && !_state.value.loadingMedia) loadMedia(reset = false)
    }

    private fun loadMedia(reset: Boolean) {
        val tree = _state.value.tree ?: return
        if (!photosSupported) {
            _state.update { it.copy(mediaLoaded = true) }
            return
        }
        val page = if (reset) 1 else _state.value.mediaNextPage ?: return

        _state.update { it.copy(loadingMedia = true) }

        viewModelScope.launch {
            try {
                val result = client.mediaList(tree.name, page)
                _state.update {
                    it.copy(
                        media = if (reset) result.data else it.media + result.data,
                        mediaNextPage = result.nextPage, loadingMedia = false, mediaLoaded = true,
                    )
                }
            } catch (e: Exception) {
                fail(e)
                _state.update { it.copy(loadingMedia = false, mediaLoaded = true) }
            }
        }
    }

    // ── Schreiben ────────────────────────────────────────────────────

    /** record: XREF des Datensatzes, an dem das Ereignis haengt - eine Familie (Heirat ...) oder, wenn null, die Person im Profil. */
    fun saveFact(request: FactRequest, record: String? = null) = write(R.string.msg_saved) { tree, xref ->
        client.saveFact(tree, record ?: xref, request)
    }

    fun deleteFact(factId: String, record: String? = null) = write(R.string.msg_deleted) { tree, xref ->
        client.deleteFact(tree, record ?: xref, factId)
    }

    fun unlink(family: String, individual: String) = write(R.string.msg_unlinked) { tree, _ ->
        client.unlink(tree, family, individual)
    }

    /** Person loeschen. Danach gibt es sie nicht mehr: Profil schliessen, notfalls eine andere Mittelperson nehmen. */
    fun deletePerson(xref: String) {
        val tree = _state.value.tree ?: return

        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            try {
                val result = client.deleteRecord(tree.name, xref)
                val done = text(R.string.msg_person_deleted)
                val message = if (result.pending) text(R.string.msg_pending, done) else done
                val wasRoot = _state.value.root == xref

                _state.update {
                    it.copy(
                        busy = false, message = message, selected = null, detail = null, profileOpen = false,
                        pedigree = null, descendants = null, mediaLoaded = false,
                        recent = it.recent.filter { p -> p.xref != xref },
                        rootHistory = it.rootHistory.filter { r -> r != xref },
                    )
                }
                loadPeople(reset = true)
                loadPending()

                if (wasRoot) {
                    val next = _state.value.rootHistory.lastOrNull() ?: _state.value.home?.takeIf { it != xref }
                    if (next != null) setRoot(next, remember = false) else _state.update { it.copy(root = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                fail(e)
            }
        }
    }

    fun addRelative(request: AddIndividualRequest) = write(R.string.msg_person_created) { tree, _ ->
        client.addIndividual(tree, request)
    }

    fun uploadPhoto(uri: Uri, title: String) = write(R.string.msg_photo_uploaded) { tree, xref ->
        val resolver = getApplication<Application>().contentResolver
        var name = "foto.jpg"

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it }
        }

        // Verkleinern und drehen (ImagePrep); was sich nicht als Bild lesen laesst, geht unveraendert hoch.
        // Limit des Servers (meist 2-8 MB) mit etwas Luft fuer den Rest der Anfrage; aeltere Module nennen es nicht.
        val limit = (_state.value.info?.maxUpload?.takeIf { it > 0 } ?: DEFAULT_MAX_UPLOAD) * 9 / 10
        val prepared = withContext(Dispatchers.IO) {
            runCatching { ImagePrep.toUploadJpeg(resolver, uri, limit) }
                .onFailure { Log.w("webtreesAnd", "Bild liess sich nicht verkleinern", it) }
                .getOrNull()
        }
        Log.i("webtreesAnd", "Upload $name: ${prepared?.size} Bytes vorbereitet, Limit $limit (Server: ${_state.value.info?.maxUpload})")

        val mime = resolver.getType(uri).orEmpty()

        when {
            prepared != null -> {
                val jpegName = name.substringBeforeLast('.') + ".jpg"
                client.uploadMedia(tree, xref, prepared, jpegName, "image/jpeg", title)
            }
            // Ein Bild, das sich nicht verkleinern liess: nicht das riesige Original hinterherschicken - das scheitert
            // am Limit des Servers nur mit einer nichtssagenden Meldung.
            mime.startsWith("image/") -> throw UserMessageException(text(R.string.err_image_prepare))
            else -> {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("file not readable")
                }
                if (bytes.size > limit) {
                    throw UserMessageException(text(R.string.err_file_too_large, bytes.size / 1048576 + 1, limit / 1048576))
                }
                client.uploadMedia(tree, xref, bytes, name, mime.ifEmpty { "application/octet-stream" }, title)
            }
        }
    }

    private fun write(@StringRes done: Int, action: suspend (tree: String, xref: String) -> WriteResult) {
        val tree = _state.value.tree ?: return
        val xref = _state.value.selected ?: return

        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            try {
                val result = action(tree.name, xref)
                val message = if (result.pending) text(R.string.msg_pending, text(done)) else text(done)
                _state.update { it.copy(busy = false, message = message) }

                // Panel und Baum zeigen den neuen Stand; die Mittelperson bleibt, wo sie ist.
                _state.update { it.copy(pedigree = null, descendants = null, mediaLoaded = false) }
                select(xref, byTap = _state.value.quickCard)
                loadPeople(reset = true)
                loadAnniversaries()
                loadPending()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                fail(e)
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // ── Fehler ───────────────────────────────────────────────────────

    private fun fail(e: Exception) {
        // Mitten in der Arbeit keine JSON-Antwort mehr: die Sitzung ist abgelaufen.
        val state = _state.value
        if (e is NotJsonException && state.screen == Screen.Main && state.info?.user?.loggedIn == true) {
            _state.update { it.copy(screen = Screen.Login, error = text(R.string.session_expired)) }
            return
        }

        _state.update { it.copy(message = explain(e)) }
    }

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun explain(e: Exception): String = getApplication<Application>().explain(e)
}
