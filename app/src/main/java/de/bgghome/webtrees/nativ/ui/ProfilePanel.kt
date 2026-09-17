package de.bgghome.webtrees.nativ.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import de.bgghome.webtrees.nativ.R
import de.bgghome.webtrees.nativ.api.FactJson
import de.bgghome.webtrees.nativ.api.FamilyJson
import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.MediaJson
import de.bgghome.webtrees.nativ.api.Person
import java.io.File

private val TABS = listOf(R.string.tab_facts, R.string.tab_media, R.string.tab_family, R.string.tab_map)

/** Eine Zeile der Zeitleiste: eigenes Ereignis, Familienereignis (Heirat) oder Geburt eines Kindes. */
private data class TimelineRow(
    val sortKey: Int,
    val year: Int?,
    val label: String,
    val fact: FactJson?,
    val editable: Boolean,
    val child: Person? = null,
    /** Familien-XREF, wenn das Ereignis an der Familie haengt (Heirat ...); null = an der Person */
    val record: String? = null,
)

/**
 * Profil einer Person nach dem Vorbild MyHeritage: rundes Foto, Name, "Verwandtschaft | Jahre",
 * Reiter, Ereignisse als Zeitleiste mit der Jahreszahl links, runder Aktionsknopf.
 * Am Tablet steht es dauerhaft links neben dem Baum, am Handy ist es eine eigene Seite.
 */
@Composable
fun ProfilePanel(state: UiState, detail: IndividualDetail, viewModel: AppViewModel, openWeb: (String) -> Unit, onClose: (() -> Unit)?) {
    // Zweiter Wert: Familien-XREF, wenn das Ereignis an einer Familie haengt
    var editFact by remember { mutableStateOf<Pair<FactJson, String?>?>(null) }
    var newFact by remember { mutableStateOf(false) }
    var newFamilyFact by remember { mutableStateOf<String?>(null) }
    var pickFamily by remember { mutableStateOf(false) }
    var deleteFact by remember { mutableStateOf<Pair<FactJson, String?>?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var unlink by remember { mutableStateOf<Pair<String, Person>?>(null) }

    val canEdit = detail.canEdit
    val canUpload = canEdit && state.tree?.canUpload == true
    val person = detail.person

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPhoto(uri, person.name)
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Kamera: die Aufnahme landet in einer eigenen Datei im Cache, die nur die Kamera-App beschreiben darf.
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) viewModel.uploadPhoto(uri, person.name)
    }
    val takePhoto = {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "aufnahme-${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, context.packageName + ".files", file).let { uri ->
            cameraUri = uri
            camera.launch(uri)
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box {
            Column(Modifier.fillMaxSize()) {
                if (state.loadingDetail || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                // Kopf
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Tipp auf das Portraet fuegt ein Foto hinzu (wie beim Vorbild)
                        Avatar(person, 96.dp, if (canUpload) Modifier.clip(RoundedCornerShape(48.dp)).clickable { pickPhoto() } else Modifier)
                        Text(
                            person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                        )
                        val subtitle = when {
                            detail.relationship.isNotEmpty() && person.lifespan.isNotBlank() -> stringResource(R.string.relation_and_years, detail.relationship.replaceFirstChar { it.uppercase() }, person.lifespan)
                            detail.relationship.isNotEmpty() -> detail.relationship.replaceFirstChar { it.uppercase() }
                            else -> person.lifespan
                        }
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.root != person.xref) {
                                OutlinedButton(onClick = { viewModel.setRoot(person.xref) }) { Text(stringResource(R.string.action_make_root)) }
                            }
                            OutlinedButton(onClick = { openWeb(person.url) }) { Text(stringResource(R.string.chip_open_web)) }
                        }
                    }
                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                    // Seltenes und Endgueltiges steckt im Drei-Punkte-Menue, nicht im Aktionsknopf
                    if (canEdit) {
                        Box(Modifier.align(Alignment.TopStart)) {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu)) }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete_person)) }, onClick = { moreMenu = false; confirmDelete = true })
                            }
                        }
                    }
                }

                // Verschiebbar: vier Reiter passen in das schmale Tablet-Panel sonst nur mit Zeilenumbruch.
                ScrollableTabRow(selectedTabIndex = state.detailTab.coerceIn(0, TABS.lastIndex), containerColor = MaterialTheme.colorScheme.surface, edgePadding = 4.dp) {
                    TABS.forEachIndexed { index, title ->
                        val label = stringResource(title).uppercase()
                        Tab(
                            selected = state.detailTab == index, onClick = { viewModel.setDetailTab(index) },
                            text = { Text(if (index == 1 && detail.media.isNotEmpty()) stringResource(R.string.tab_with_count, label, detail.media.size) else label, style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }

                when (state.detailTab.coerceIn(0, TABS.lastIndex)) {
                    0 -> Timeline(detail, canEdit, onEdit = { fact, record -> editFact = fact to record }, onDelete = { fact, record -> deleteFact = fact to record }, onPerson = viewModel::select)
                    1 -> MediaGrid(detail.media, openWeb)
                    2 -> Relatives(detail, canEdit, onSelect = viewModel::select, onUnlink = { family, who -> unlink = family to who })
                    3 -> LifeMap(mapFacts(detail))
                }
            }

            // Runder Aktionsknopf: Ereignis, Verwandte, Foto
            if (canEdit) {
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    FloatingActionButton(onClick = { addMenu = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_add_event)) }, onClick = { addMenu = false; newFact = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_add_relative)) }, onClick = { addMenu = false; viewModel.requestAddRelative(person.xref) })
                        if (detail.spouseFamilies.isNotEmpty()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_add_family_event)) }, onClick = {
                                addMenu = false
                                if (detail.spouseFamilies.size == 1) newFamilyFact = detail.spouseFamilies.first().xref else pickFamily = true
                            })
                        }
                        if (canUpload) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_take_photo)) }, onClick = { addMenu = false; takePhoto() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_pick_photo)) }, onClick = { addMenu = false; pickPhoto() })
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_person_title, person.name),
            text = stringResource(R.string.delete_person_text),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deletePerson(person.xref) },
        )
    }
    unlink?.let { (family, who) ->
        ConfirmDialog(
            title = stringResource(R.string.unlink_title),
            text = stringResource(R.string.unlink_text, who.name),
            confirm = stringResource(R.string.action_unlink),
            onDismiss = { unlink = null },
            onConfirm = { unlink = null; viewModel.unlink(family, who.xref) },
        )
    }
    if (newFact) {
        FactDialog(fact = null, tags = state.tags, onDismiss = { newFact = false }, onSave = { newFact = false; viewModel.saveFact(it) })
    }
    newFamilyFact?.let { family ->
        FactDialog(fact = null, tags = state.familyTags, onDismiss = { newFamilyFact = null }, onSave = { newFamilyFact = null; viewModel.saveFact(it, record = family) })
    }
    if (pickFamily) {
        ChoiceDialog(
            title = stringResource(R.string.family_pick_title),
            options = detail.spouseFamilies.map { it.xref to (it.spouse?.name ?: stringResource(R.string.unknown_person)) },
            onDismiss = { pickFamily = false },
            onChoose = { pickFamily = false; newFamilyFact = it },
        )
    }
    editFact?.let { (fact, record) ->
        FactDialog(fact = fact, tags = state.tags, onDismiss = { editFact = null }, onSave = { editFact = null; viewModel.saveFact(it, record) })
    }
    deleteFact?.let { (fact, record) ->
        ConfirmDialog(
            title = stringResource(R.string.fact_delete_title, fact.label),
            text = listOfNotNull(fact.value.takeIf { it.isNotEmpty() }, fact.date?.text, fact.place?.name).joinToString(" · "),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { deleteFact = null },
            onConfirm = { deleteFact = null; viewModel.deleteFact(fact.id, record) },
        )
    }
}

/** Ereignisse mit Ort, zeitlich geordnet: die eigenen plus Heirat & Co. aus den Partnerschaften. */
private fun mapFacts(detail: IndividualDetail): List<FactJson> =
    (detail.facts + detail.spouseFamilies.flatMap { it.facts })
        .filter { it.place != null }
        .sortedBy { it.date?.jd?.takeIf { jd -> jd > 0 } ?: Int.MAX_VALUE }

// ── Zeitleiste ───────────────────────────────────────────────────────

@Composable
private fun Timeline(detail: IndividualDetail, canEdit: Boolean, onEdit: (FactJson, String?) -> Unit, onDelete: (FactJson, String?) -> Unit, onPerson: (String) -> Unit) {
    val rows = buildList {
        // Eigene Ereignisse in der Reihenfolge von webtrees; undatierte erben den Platz des Vorgaengers.
        var last = Int.MIN_VALUE + 1
        // Geschlecht steckt in der Farbe des Portraets; unbekannte Hersteller-Tags (_INET ...) sagen dem Leser nichts.
        detail.facts.filter { it.tag != "SEX" && it.known }.forEach { fact ->
            val key = if (fact.tag == "NAME") Int.MIN_VALUE else fact.date?.jd?.takeIf { it > 0 } ?: last
            if (fact.tag != "NAME") last = key
            // Name und Sperrvermerk haengen an Indexen bzw. Rechten - vorerst nur in webtrees aendern.
            add(TimelineRow(key, fact.date?.year?.takeIf { it != 0 }, fact.label + if (fact.type.isNotEmpty()) " · ${fact.type}" else "", fact, canEdit && fact.tag != "RESN"))
        }
        // Heirat, Scheidung ... stehen in webtrees bei der Familie; Geburten der Kinder gehoeren in die Lebenslinie.
        detail.spouseFamilies.forEach { family ->
            family.facts.forEach { fact ->
                val label = family.spouse?.name?.let { stringResource(R.string.fact_with_spouse, fact.label, it) } ?: fact.label
                // Heirat, Scheidung ... lassen sich bearbeiten - geschrieben wird an die Familie, nicht an die Person.
                add(TimelineRow(fact.date?.jd?.takeIf { it > 0 } ?: Int.MAX_VALUE, fact.date?.year?.takeIf { it != 0 }, label, fact, editable = canEdit && fact.known, record = family.xref))
            }
            family.children.forEach { child ->
                val date = child.birth?.date ?: return@forEach
                val label = stringResource(
                    when (child.sex) { "M" -> R.string.timeline_birth_son; "F" -> R.string.timeline_birth_daughter; else -> R.string.timeline_birth_child },
                    child.name,
                )
                add(TimelineRow(date.jd, date.year.takeIf { it != 0 }, label, FactJson(id = "child-" + child.xref, date = date, place = child.birth.place), editable = false, child = child))
            }
        }
    }.sortedBy { it.sortKey }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
        // Kein key: Fakten-IDs sind Inhalts-Hashes, zwei gleichlautende Ereignisse haetten denselben.
        items(rows) { row ->
            TimelineItem(row, onEdit, onDelete, onPerson)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TimelineItem(row: TimelineRow, onEdit: (FactJson, String?) -> Unit, onDelete: (FactJson, String?) -> Unit, onPerson: (String) -> Unit) {
    val fact = row.fact ?: return

    Row(
        Modifier.fillMaxWidth()
            .then(if (row.child != null && !row.child.isPrivate) Modifier.clickable { onPerson(row.child.xref) } else Modifier)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Grosse Jahreszahl links
        Text(
            row.year?.toString().orEmpty(), modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (fact.value.isNotEmpty()) Text(fact.value, style = MaterialTheme.typography.bodyMedium)
            val sub = listOfNotNull(fact.date?.text, fact.place?.name).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fact.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (fact.sources.isNotEmpty()) {
                Text(stringResource(R.string.fact_sources, fact.sources.joinToString("; ") { it.title }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.editable) {
            IconButton(onClick = { onEdit(fact, row.record) }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (fact.tag != "NAME") {
                IconButton(onClick = { onDelete(fact, row.record) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ── Verwandte ────────────────────────────────────────────────────────

@Composable
private fun Relatives(detail: IndividualDetail, canEdit: Boolean, onSelect: (String) -> Unit, onUnlink: (String, Person) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
        detail.parentFamilies.forEach { family ->
            item { SectionTitle(stringResource(R.string.family_parents_siblings)) }
            item { FamilyMembers(family, self = detail.person, asChild = true, canEdit = canEdit, onSelect = onSelect, onUnlink = onUnlink) }
        }
        detail.spouseFamilies.forEach { family ->
            item {
                SectionTitle(family.marriage?.date?.text?.let { stringResource(R.string.family_partnership_married, it) } ?: stringResource(R.string.family_partnership))
            }
            item { FamilyMembers(family, self = detail.person, asChild = false, canEdit = canEdit, onSelect = onSelect, onUnlink = onUnlink) }
        }
        if (detail.parentFamilies.isEmpty() && detail.spouseFamilies.isEmpty()) {
            item { Text(stringResource(R.string.family_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FamilyMembers(family: FamilyJson, self: Person, asChild: Boolean, canEdit: Boolean, onSelect: (String) -> Unit, onUnlink: (String, Person) -> Unit) {
    // "Verknuepfung loesen": die Person bleibt im Baum, sie gehoert nur nicht mehr zu dieser Familie.
    @Composable
    fun row(person: Person, label: String) {
        PersonRow(
            person, label = label, onClick = { onSelect(person.xref) },
            trailing = if (!canEdit || person.isPrivate) null else {
                { IconButton(onClick = { onUnlink(family.xref, person) }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_unlink), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            },
        )
    }

    Column {
        if (asChild) {
            family.husband?.let { row(it, stringResource(R.string.rel_father)) }
            family.wife?.let { row(it, stringResource(R.string.rel_mother)) }
            family.children.filter { it.xref != self.xref }.forEach { child ->
                row(child, when (child.sex) { "M" -> stringResource(R.string.rel_brother); "F" -> stringResource(R.string.rel_sister); else -> stringResource(R.string.rel_sibling) })
            }
            if (canEdit) {
                TextButton(onClick = { onUnlink(family.xref, self) }, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.unlink_self_from_parents)) }
            }
        } else {
            family.spouse?.let { row(it, when (it.sex) { "M" -> stringResource(R.string.rel_partner_m); "F" -> stringResource(R.string.rel_partner_f); else -> stringResource(R.string.rel_partner) }) }
            family.children.forEach { child ->
                row(child, when (child.sex) { "M" -> stringResource(R.string.rel_son); "F" -> stringResource(R.string.rel_daughter); else -> stringResource(R.string.rel_child) })
            }
        }
    }
}

// ── Fotos einer Person ───────────────────────────────────────────────

@Composable
fun MediaGrid(media: List<MediaJson>, openWeb: (String) -> Unit, showPeople: Boolean = false, onEnd: (() -> Unit)? = null) {
    if (media.isEmpty()) {
        Text(stringResource(R.string.media_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media) { item ->
            if (onEnd != null && item === media.last()) androidx.compose.runtime.LaunchedEffect(media.size) { onEnd() }

            Column(Modifier.clickable { openWeb(item.url) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).then(Modifier), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
                    if (item.thumb != null) {
                        AsyncImage(model = item.thumb, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(item.title.ifEmpty { item.mime }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
                if (showPeople && item.people.isNotEmpty()) {
                    Text(item.people.joinToString(", ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}
