package com.anydownlod.ui.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydownlod.core.PreviewFailure
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.add.AddFormViewModel
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.copied_urls
import com.anydownlod.ui.generated.resources.download
import com.anydownlod.ui.generated.resources.empty_all_body
import com.anydownlod.ui.generated.resources.empty_all_title
import com.anydownlod.ui.generated.resources.phone_home_subtitle
import com.anydownlod.ui.generated.resources.phone_home_title
import com.anydownlod.ui.generated.resources.phone_paste_hint
import com.anydownlod.ui.generated.resources.phone_recent
import com.anydownlod.ui.generated.resources.phone_rights
import com.anydownlod.ui.generated.resources.phone_settings
import com.anydownlod.ui.generated.resources.phone_share
import com.anydownlod.ui.generated.resources.phone_view_all
import com.anydownlod.ui.generated.resources.preview_edit
import com.anydownlod.ui.generated.resources.preview_edit_hide
import com.anydownlod.ui.generated.resources.preview_failed
import com.anydownlod.ui.generated.resources.preview_loading
import com.anydownlod.ui.generated.resources.preview_timed_out
import com.anydownlod.ui.generated.resources.preview_unavailable
import com.anydownlod.ui.history.HistoryViewModel
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.i18n.text
import com.anydownlod.ui.preview.PreviewEditPanel
import com.anydownlod.ui.preview.PreviewPhase
import com.anydownlod.ui.preview.PreviewUiState
import com.anydownlod.ui.preview.PreviewViewModel
import com.anydownlod.ui.preview.decodeThumbnail
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.StatusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Paste a link and confirm the download from the featured card.
 * The bottom button opens the preview, then starts the job.
 */
@Composable
internal fun PhoneHome(
    previewUrl: String?,
    onPreviewUrl: (String) -> Unit,
    onClearPreview: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewAll: () -> Unit,
    onDownloadStarted: () -> Unit,
    startupWarning: String?,
    form: AddFormViewModel = metroViewModel(),
    historyViewModel: HistoryViewModel = metroViewModel(),
    previewViewModel: PreviewViewModel = metroViewModel(),
) {
    val colors = phoneColors()
    val formState by form.state.collectAsState()
    val status by form.status.collectAsState()
    val history by historyViewModel.state.collectAsState()
    val previewState by previewViewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val latest = history.rows
        .filter { it.state == JobState.COMPLETED }
        .maxByOrNull { it.finishedAtEpochMillis ?: 0L }

    LaunchedEffect(previewUrl) {
        val url = previewUrl ?: return@LaunchedEffect
        previewViewModel.open(url, this)
    }

    fun openPreview() {
        val single = form.validateForPreview() ?: return
        copied = false
        onPreviewUrl(single)
    }

    fun confirmDownload() {
        val phase = previewState.phase
        if (phase is PreviewPhase.Loading) return
        val ready = phase as? PreviewPhase.Ready
        val ids = previewState.selectedMediaIds.toList()
        if (ready != null && ready.preview.videos.isNotEmpty() && ids.isEmpty()) return
        val spotify = ready?.preview?.spotify
        if (spotify != null) {
            previewViewModel.queueSpotify(spotify, form.currentOptions())
        } else if (form.submit(ids) == null) {
            return
        }
        onClearPreview()
        onDownloadStarted()
    }

    fun onPrimaryAction() {
        if (previewUrl != null) confirmDownload() else openPreview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(phoneCanvas(colors))
            .statusBarsPadding()
            .testTag("phone-home"),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            HomeHeader(colors = colors, onOpenSettings = onOpenSettings)
            startupWarning?.let { warning ->
                MessageStrip(text = warning, tone = StatusTone.Negative)
            }
            LinkField(
                value = formState.urlText,
                colors = colors,
                onValueChange = { value ->
                    form.setUrl(value)
                    copied = false
                    if (previewUrl != null) onClearPreview()
                },
                onGo = ::onPrimaryAction,
            )
            status?.let { current ->
                MessageStrip(
                    text = current.message.resolve(),
                    tone = if (current.isError) StatusTone.Negative else StatusTone.Positive,
                )
            }
            if (copied) {
                MessageStrip(
                    text = stringResource(Res.string.copied_urls, 1),
                    tone = StatusTone.Positive,
                )
            }
            Text(
                text = stringResource(Res.string.phone_rights),
                color = colors.muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.phone_recent),
                    color = colors.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.phone_view_all),
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onViewAll)
                        .testTag("phone-view-all")
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            FeaturedCard(
                colors = colors,
                previewUrl = previewUrl,
                previewState = previewState,
                latestTitle = latest?.title,
                latestHost = latest?.sourceHost,
                onOpenLatest = {
                    val row = latest ?: return@FeaturedCard
                    form.setUrl(row.sourceUrl)
                    onPreviewUrl(row.sourceUrl)
                },
                onShare = { url ->
                    clipboard.setText(AnnotatedString(url))
                    copied = true
                },
                onDownloadFeatured = ::onPrimaryAction,
                shareUrl = previewUrl ?: latest?.sourceUrl,
            )
            if (previewUrl != null && previewState.phase !is PreviewPhase.Loading) {
                TextButton(
                    onClick = previewViewModel::toggleEdit,
                    modifier = Modifier.testTag("preview-edit-toggle"),
                ) {
                    Text(
                        if (previewState.editExpanded) {
                            text(Res.string.preview_edit_hide)
                        } else {
                            text(Res.string.preview_edit)
                        },
                    )
                }
                if (previewState.editExpanded) {
                    PreviewEditPanel(
                        editor = form,
                        availableFormats = (previewState.phase as? PreviewPhase.Ready)?.preview?.availableFormats,
                        capabilities = previewState.capabilities,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PhoneDownloadButton(onClick = ::onPrimaryAction, colors = colors)
        }
    }
}

@Composable
private fun HomeHeader(colors: PhoneColors, onOpenSettings: () -> Unit) {
    val settingsLabel = stringResource(Res.string.phone_settings)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = settingsLabel }
                .testTag("phone-settings"),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.phone_home_title),
                color = colors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(Res.string.phone_home_subtitle),
                color = colors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinkField(
    value: String,
    colors: PhoneColors,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
) {
    val downloadLabel = stringResource(Res.string.download)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(colors.field)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(Modifier.size(18.dp)) { drawLink(colors.muted) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag("add-url-field"),
            singleLine = true,
            textStyle = TextStyle(color = colors.ink, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onGo() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.phone_paste_hint),
                            color = colors.muted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onGo)
                .semantics { contentDescription = downloadLabel }
                .testTag("phone-field-download"),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Modifier.size(20.dp)) { drawDownload(Color.White) }
        }
    }
}

@Composable
private fun FeaturedCard(
    colors: PhoneColors,
    previewUrl: String?,
    previewState: PreviewUiState,
    latestTitle: String?,
    latestHost: String?,
    onOpenLatest: () -> Unit,
    onShare: (String) -> Unit,
    onDownloadFeatured: () -> Unit,
    shareUrl: String?,
) {
    val phase = previewState.phase
    if (previewUrl != null) {
        val ready = phase as? PreviewPhase.Ready
            MediaCard(
                colors = colors,
                name = ready?.preview?.title ?: stringResource(Res.string.preview_loading),
                handle = ready?.preview?.channel ?: ready?.preview?.extractor,
                onOpen = {},
                onDownload = onDownloadFeatured,
                onShare = shareUrl?.let { url -> { onShare(url) } },
                tag = "preview-card",
                titleTag = if (ready != null) "preview-title" else null,
            ) {
                when (phase) {
                    PreviewPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.testTag("preview-loading"))
                    }
                    is PreviewPhase.Failed -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = failureText(phase.failure),
                            color = colors.ink,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is PreviewPhase.Ready -> ThumbnailArt(
                        bytes = phase.thumbnail,
                        title = phase.preview.title,
                        showPlay = true,
                    )
                }
            }
        return
    }
    if (latestTitle != null) {
        MediaCard(
            colors = colors,
            name = latestTitle,
            handle = latestHost,
            onOpen = onOpenLatest,
            onDownload = onOpenLatest,
            onShare = shareUrl?.let { url -> { onShare(url) } },
            tag = "latest-download",
        ) {
            ThumbnailArt(bytes = null, title = latestTitle, showPlay = true)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.empty_all_title), color = colors.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.empty_all_body), color = colors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun MediaCard(
    colors: PhoneColors,
    name: String,
    handle: String?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: (() -> Unit)?,
    tag: String,
    titleTag: String? = null,
    art: @Composable () -> Unit,
) {
    val downloadLabel = stringResource(Res.string.download)
    val shareLabel = stringResource(Res.string.phone_share)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .testTag(tag)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD7E7FF)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
                )
                handle?.let { site ->
                    Text(
                        text = site,
                        color = colors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDownload)
                    .semantics { contentDescription = downloadLabel },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Modifier.size(18.dp)) { drawDownload(colors.muted) }
            }
            if (onShare != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onShare)
                        .semantics { contentDescription = shareLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Modifier.size(18.dp)) { drawShare(colors.muted) }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.track)
                .clickable(onClick = onOpen),
        ) {
            art()
        }
    }
}

@Composable
private fun ThumbnailArt(
    bytes: ByteArray?,
    title: String,
    showPlay: Boolean,
) {
    val colors = phoneColors()
    val bitmap = remember(bytes) { bytes?.let(::decodeThumbnail) }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag("preview-thumbnail"),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(posterColors(title)),
                ),
            )
        }
        if (showPlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Modifier.size(22.dp)) { drawPlay(colors.ink) }
            }
        }
    }
}

@Composable
private fun failureText(failure: PreviewFailure): String = when (failure) {
    PreviewFailure.Unavailable -> stringResource(Res.string.preview_unavailable)
    PreviewFailure.TimedOut -> stringResource(Res.string.preview_timed_out)
    PreviewFailure.Failed -> stringResource(Res.string.preview_failed)
}

private fun posterColors(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFD7E7FF), Color(0xFFB7D0F0)),
        listOf(Color(0xFFE6E8EE), Color(0xFFD5D8E0)),
        listOf(Color(0xFFE8EEF6), Color(0xFFD0D7E2)),
    )
    return palettes[title.hashCode().mod(palettes.size)]
}
