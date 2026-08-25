package app.terminalssh.secure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.model.FuzzyMatch
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ui.theme.Space
import app.terminalssh.secure.ui.theme.Size
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise
import app.terminalssh.secure.vm.AppViewModel

/**
 * Below this many hosts a search field is noise: scanning four rows is faster than
 * deciding what to type. Hick's law cuts both ways, and an input the user will never
 * use still costs them a glance on every launch.
 */
private const val SEARCH_APPEARS_AT = 5

@Composable
fun HostsScreen(
    viewModel: AppViewModel,
    onConnect: (HostProfile, CharArray?) -> Unit,
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hostStates by viewModel.sessions.hostStates.collectAsStateWithLifecycle(emptyMap())
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var askPasswordFor by remember { mutableStateOf<HostProfile?>(null) }
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importHostsFromSshConfig)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(viewModel::exportHostsToSshConfig) }

    // With no query the store's own order (favourites, then most recent) is what the user
    // expects; once they type, best match wins and that ordering is what helps.
    val filtered = if (query.isBlank()) {
        hosts
    } else {
        hosts.map { it to it.searchScore(query) }
            .filter { (_, score) -> score > FuzzyMatch.NO_MATCH }
            .sortedByDescending { (_, score) -> score }
            .map { (profile, _) -> profile }
    }

    val window = rememberWindowSize()
    val margin = window.width.pageMargin()
    val maxContentWidth = window.width.contentMaxWidth()
    val searching = query.isNotBlank()
    val showSearch = searching || hosts.size >= SEARCH_APPEARS_AT

    val openHost: (HostProfile) -> Unit = { profile ->
        if (viewModel.hasStoredSecret(profile)) onConnect(profile, null) else askPasswordFor = profile
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            // Centred and width-capped on tablets: a host row stretched across 1280dp
            // puts the name and the actions at opposite ends of the screen.
            modifier = Modifier
                .fillMaxSize()
                .then(if (maxContentWidth != Dp.Unspecified) Modifier.widthIn(max = maxContentWidth) else Modifier)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = margin, end = margin, top = Space.xl, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "header") {
                ScreenHeader(hostCount = hosts.size, liveCount = hostStates.count { it.value.isLive })
            }

            if (showSearch) {
                item(key = "search") {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        onClear = { viewModel.setQuery("") },
                    )
                }
            }

            when {
                hosts.isEmpty() -> item(key = "first-run") {
                    FirstRunPanel(
                        onAdd = { creating = true },
                        onImport = { configPicker.launch(arrayOf("*/*")) },
                    )
                }

                filtered.isEmpty() -> item(key = "no-results") {
                    NoResults(query = query, onClear = { viewModel.setQuery("") })
                }

                // While searching, relevance is the only order that helps: section
                // headers would cut the ranking into pieces that each start over.
                searching -> hostRows(filtered, hostStates, openHost, viewModel, { editing = it })

                else -> sectionedHosts(filtered, hostStates, openHost, viewModel, { editing = it })
            }

            if (hosts.isNotEmpty()) {
                item(key = "config-io") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        TextButton(onClick = { configPicker.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.hosts_import_config))
                        }
                        TextButton(onClick = { exportPicker.launch("ssh_config") }) {
                            Text(stringResource(R.string.hosts_export_config))
                        }
                    }
                }
            }
        }

        // The only primary action on the screen. There used to be a second one inside a
        // banner at the top; two buttons for one job makes a user stop and choose between
        // things that are the same, which is the tax the isolation effect is meant to avoid.
        //
        // On an empty app the first-run panel already carries that action full width, so
        // the button floating over it would be the same duplication in a different place.
        if (hosts.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = Turquoise,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = {
                    Text(
                        stringResource(R.string.home_new_connection),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.xl)
                    .primaryActionSemantics { creating = true },
            )
        }
    }

    if (creating || editing != null) {
        HostEditSheet(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { profile, password ->
                if (viewModel.saveHost(profile, password)) {
                    creating = false
                    editing = null
                }
            },
            onDelete = { profile ->
                viewModel.deleteHost(profile)
                creating = false
                editing = null
            },
        )
    }

    askPasswordFor?.let { profile ->
        PasswordPrompt(
            profile = profile,
            onDismiss = { askPasswordFor = null },
            onSubmit = { password ->
                askPasswordFor = null
                onConnect(profile, password)
            },
        )
    }
}

/**
 * Hosts split into the groups people actually think in, in the order they think of them.
 *
 * A server you are already working on comes first — leaving a session open and then
 * hunting for the row that reopens it is the most annoying thing a client can do. Starred
 * next, then recently used, then everything else. Headers only appear once there is more
 * than one group to tell apart; on a three-host list they would be pure decoration.
 */
private fun LazyListScope.sectionedHosts(
    hosts: List<HostProfile>,
    hostStates: Map<String, SshSessionState>,
    onOpen: (HostProfile) -> Unit,
    viewModel: AppViewModel,
    onEdit: (HostProfile) -> Unit,
) {
    val live = hosts.filter { hostStates[it.id].status() != ConnectionStatus.IDLE }
    val rest = hosts - live.toSet()
    val starred = rest.filter { it.favorite }
    val recent = rest.filter { !it.favorite && it.lastConnectedAt > 0L }
    val others = rest.filter { !it.favorite && it.lastConnectedAt == 0L }

    val groups = listOf(
        R.string.hosts_section_live to live,
        R.string.hosts_section_favorites to starred,
        R.string.hosts_section_recent to recent,
        R.string.hosts_section_all to others,
    ).filter { (_, entries) -> entries.isNotEmpty() }

    val withHeaders = groups.size > 1
    groups.forEach { (titleRes, entries) ->
        if (withHeaders) {
            item(key = "header-$titleRes") { SectionHeader(stringResource(titleRes)) }
        }
        hostRows(entries, hostStates, onOpen, viewModel, onEdit)
    }
}

private fun LazyListScope.hostRows(
    hosts: List<HostProfile>,
    hostStates: Map<String, SshSessionState>,
    onOpen: (HostProfile) -> Unit,
    viewModel: AppViewModel,
    onEdit: (HostProfile) -> Unit,
) {
    items(hosts, key = { it.id }) { profile ->
        HostRow(
            profile = profile,
            state = hostStates[profile.id],
            onOpen = { onOpen(profile) },
            onEdit = { onEdit(profile) },
            onToggleFavorite = { viewModel.toggleFavorite(profile) },
        )
    }
}

/**
 * Names whichever control is currently the screen's primary action.
 *
 * Exactly one of them is on screen at a time — the floating button once there are hosts,
 * the panel button before that — and both answer to the same name, so a screen reader
 * user is never hunting for a control that quietly changed what it is called. The visible
 * label is that same string, which is what keeps voice control working: "tap new
 * connection" has to match what the button says.
 */
@Composable
private fun Modifier.primaryActionSemantics(onInvoke: () -> Unit): Modifier {
    val description = stringResource(R.string.home_new_connection)
    return clearAndSetSemantics {
        contentDescription = description
        role = Role.Button
        onClick {
            onInvoke()
            true
        }
    }
}

@Composable
private fun ScreenHeader(hostCount: Int, liveCount: Int) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = Space.xs)) {
        Text(
            stringResource(R.string.tab_hosts),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // The count is a caption, not a metric card. It answers "did my import work?"
        // without spending a fifth of the first screen to say it, and it says nothing at
        // all when the answer is zero and the empty state is already saying it better.
        if (hostCount > 0) {
            Spacer(Modifier.width(Space.md))
            Text(
                text = if (liveCount > 0) {
                    stringResource(R.string.home_hosts_count, hostCount) + " · " +
                        stringResource(R.string.home_sessions_count, liveCount)
                } else {
                    stringResource(R.string.home_hosts_count, hostCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (liveCount > 0) Turquoise else TextSecondary,
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(start = Space.xs, top = Space.md, bottom = Space.xxs),
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val clearLabel = stringResource(R.string.hosts_clear_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.hosts_search), color = TextSecondary) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
        trailingIcon = {
            // Only present once there is something to clear: a permanently visible clear
            // button trains people to ignore the whole trailing slot.
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.semantics { contentDescription = clearLabel },
                ) {
                    Icon(Icons.Outlined.Close, null, tint = TextSecondary)
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Turquoise.copy(alpha = 0.45f),
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HostRow(
    profile: HostProfile,
    state: SshSessionState?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val status = state.status()
    // A host with a session is the one row that gets a coloured edge. If every row is
    // emphasised then none of them is, and the row the user is looking for stops being
    // findable at a glance.
    val outline = if (status == ConnectionStatus.IDLE) {
        Stroke.copy(alpha = 0.55f)
    } else {
        status.color.copy(alpha = 0.5f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        // The environment band sits on the leading edge so "production" is visible
        // while the thumb is still travelling toward the row.
        profile.environment.color?.let { bandColor ->
            Box(
                Modifier
                    .width(Size.rail)
                    .height(40.dp)
                    .clip(RoundedCornerShape(Space.xxs))
                    .background(bandColor),
            )
            Spacer(Modifier.width(Space.md))
        }

        Avatar(initial = profile.displayName.take(1).uppercase(), status = status)
        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(ltr(profile.displayName), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                ltr(profile.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.xxs))
            MetaLine(profile = profile, status = status)
        }

        val favoriteDescription = stringResource(
            if (profile.favorite) R.string.host_unfavorite else R.string.host_favorite,
            profile.displayName,
        )
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.semantics { contentDescription = favoriteDescription },
        ) {
            Icon(
                imageVector = if (profile.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (profile.favorite) Turquoise else TextSecondary,
            )
        }
        val editDescription = stringResource(R.string.host_edit, profile.displayName)
        IconButton(
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = editDescription },
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = TextSecondary)
        }
    }
}

/**
 * The third line of the row: what this host is doing, or when it was last used.
 *
 * Live state wins over recency because it is the thing that changes; a host that is
 * connected right now does not need to be told when it was connected before. Tags come
 * last and only if there is nothing more urgent to say.
 */
@Composable
private fun MetaLine(profile: HostProfile, status: ConnectionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status != ConnectionStatus.IDLE) {
            PresenceDot(status)
            Spacer(Modifier.width(Space.sm))
            Text(
                stringResource(status.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = status.color,
            )
        } else {
            Text(
                sinceLabel(profile.lastConnectedAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }

        // The environment already has a colour band; repeating it as words is what makes
        // the band readable for anyone who cannot tell the colours apart.
        if (profile.environment.color != null) {
            Text(
                " · " + stringResource(profile.environment.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        profile.tags.firstOrNull()?.let { tag ->
            Text(
                " · $tag",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Avatar(initial: String, status: ConnectionStatus) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(Size.avatar)
                .clip(CircleShape)
                .background(Turquoise.copy(alpha = if (status == ConnectionStatus.IDLE) 0.11f else 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                color = Turquoise,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (status != ConnectionStatus.IDLE) {
            Box(
                Modifier
                    .size(Space.md)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                PresenceDot(status)
            }
        }
    }
}

@Composable
private fun PresenceDot(status: ConnectionStatus) {
    // Connecting breathes; connected does not. Motion here means "still working on it",
    // so a steady dot is a promise that nothing more is pending.
    val alpha = if (status.isWorking) {
        val transition = rememberInfiniteTransition(label = "presence")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.SLOW_MS, easing = Motion.Standard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "presence-pulse",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(Space.sm)
            .alpha(alpha)
            .clip(CircleShape)
            .background(status.color),
    )
}

@Composable
private fun sinceLabel(lastConnectedAt: Long): String =
    when (val since = RelativeTime.since(lastConnectedAt, System.currentTimeMillis())) {
        Since.Never -> stringResource(R.string.host_last_never)
        Since.JustNow -> stringResource(R.string.host_last_just_now)
        Since.Yesterday -> stringResource(R.string.host_last_yesterday)
        is Since.Minutes -> stringResource(R.string.host_last_minutes, since.count)
        is Since.Hours -> stringResource(R.string.host_last_hours, since.count)
        is Since.Days -> stringResource(R.string.host_last_days, since.count)
        is Since.Weeks -> stringResource(R.string.host_last_weeks, since.count)
        is Since.Months -> stringResource(R.string.host_last_months, since.count)
    }

/**
 * What a brand new install sees.
 *
 * The old screen showed this user two competing "add" buttons and a banner describing the
 * app they had already chosen to install. What they need instead is the one thing that
 * turns an empty app into a useful one, and the shortcut that saves them typing a dozen
 * servers on a phone keyboard.
 */
@Composable
private fun FirstRunPanel(onAdd: () -> Unit, onImport: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xxl)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, Stroke, MaterialTheme.shapes.large)
            .padding(Space.xl),
    ) {
        Box(
            Modifier
                .size(Size.touchTargetLarge)
                .clip(MaterialTheme.shapes.medium)
                .background(Turquoise.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Terminal, null, tint = Turquoise, modifier = Modifier.size(Size.icon))
        }
        Spacer(Modifier.height(Space.lg))
        Text(stringResource(R.string.hosts_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.hosts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(Space.xl))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Turquoise)
                .clickable(onClick = onAdd)
                .heightIn(min = Size.touchTarget)
                .primaryActionSemantics(onAdd)
                .padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(Space.sm))
            Text(
                stringResource(R.string.home_new_connection),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            stringResource(R.string.hosts_empty_hint),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        TextButton(onClick = onImport) { Text(stringResource(R.string.hosts_import_config)) }
    }
}

/**
 * A search that found nothing says what it searched for and offers the way back, rather
 * than leaving the user staring at a screen that looks broken.
 */
@Composable
private fun NoResults(query: String, onClear: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xxxl),
    ) {
        Text(stringResource(R.string.hosts_no_results_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.hosts_no_results_body, query),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.hosts_clear_search)) }
    }
}
