/*
 * Shared engine bindings — AnyDownload
 *
 * T-115: the one production extractor list. Before this file the ordered list
 * (YouTube single video, YouTube playlist tab, X/Twitter) was copied in
 * AndroidExtractors, IosExtractors, WebAppGraph, desktop Main.kt, and
 * DesktopPreviewSource. The host graphs include this binding container once
 * they land (T-116–T-119); the public functions below are what the host
 * classes and the engine tests call until then.
 *
 * The order is behavior: YoutubeTabIE stays in the list so /playlist?list=
 * keeps expanding into child jobs (T-107/T-108). GenericIE stays out.
 */
package com.anydownlod.core.di

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.extract.youtube.YoutubeTabIE
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.platform.HttpTransfer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The shared engine bindings every host graph includes: one transfer-backed
 * [ExtractorHttp], the ordered extractors, and the registry. The graph owns
 * the [HttpTransfer] and [JsRuntime] instances; these functions only assemble
 * them.
 */
@BindingContainer
object SharedEngineBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun extractorHttp(transfer: HttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    @Provides
    @SingleIn(AppScope::class)
    fun youtube(http: ExtractorHttp, jsRuntime: JsRuntime): YoutubeIE = YoutubeIE(http, jsRuntime)

    @Provides
    @SingleIn(AppScope::class)
    fun youtubeTab(http: ExtractorHttp): YoutubeTabIE = YoutubeTabIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitter(http: ExtractorHttp): TwitterIE = TwitterIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun extractorRegistry(
        youtube: YoutubeIE,
        youtubeTab: YoutubeTabIE,
        twitter: TwitterIE,
    ): ExtractorRegistry = productionExtractorRegistry(youtube, youtubeTab, twitter)
}

/**
 * The production order the graph and the tests share: YouTube single video,
 * YouTube playlist tab, then X/Twitter. The playlist extractor stays between
 * the two so its `/playlist?list=` match is reached before the single-video
 * fallback, exactly as before the Metro migration.
 */
fun productionExtractorRegistry(
    youtube: YoutubeIE,
    youtubeTab: YoutubeTabIE,
    twitter: TwitterIE,
): ExtractorRegistry = ExtractorRegistry(listOf(youtube, youtubeTab, twitter))

/**
 * Convenience for host classes and tests that hold a transfer and a runtime
 * but no graph yet. It constructs the same ordered list as the graph bindings.
 */
fun productionExtractorRegistry(http: ExtractorHttp, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(
        youtube = YoutubeIE(http, jsRuntime),
        youtubeTab = YoutubeTabIE(http),
        twitter = TwitterIE(http),
    )

/** Same list, built from a raw transfer; used by the host classes' constructors. */
fun productionExtractorRegistry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(ExtractorHttp(transfer), jsRuntime)
