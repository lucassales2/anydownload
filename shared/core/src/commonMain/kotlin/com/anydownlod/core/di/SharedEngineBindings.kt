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
import com.anydownlod.core.extract.bbc.BBCCoUkIE
import com.anydownlod.core.extract.archiveorg.ArchiveOrgIE
import com.anydownlod.core.extract.dailymotion.DailymotionIE
import com.anydownlod.core.extract.facebook.FacebookIE
import com.anydownlod.core.extract.facebook.FacebookPluginsVideoIE
import com.anydownlod.core.extract.facebook.FacebookRedirectURLIE
import com.anydownlod.core.extract.facebook.FacebookReelIE
import com.anydownlod.core.extract.instagram.InstagramIE
import com.anydownlod.core.extract.instagram.InstagramIOSIE
import com.anydownlod.core.extract.bilibili.BiliBiliIE
import com.anydownlod.core.extract.bilibili.BiliBiliPlayerIE
import com.anydownlod.core.extract.peertube.PeerTubeIE
import com.anydownlod.core.extract.patreon.PatreonIE
import com.anydownlod.core.extract.soundcloud.SoundcloudEmbedIE
import com.anydownlod.core.extract.soundcloud.SoundcloudIE
import com.anydownlod.core.extract.tiktok.TikTokIE
import com.anydownlod.core.extract.tiktok.TikTokVMIE
import com.anydownlod.core.extract.twitch.TwitchStreamIE
import com.anydownlod.core.extract.twitch.TwitchVodIE
import com.anydownlod.core.extract.twitter.TwitterAmplifyIE
import com.anydownlod.core.extract.twitter.TwitterBroadcastIE
import com.anydownlod.core.extract.twitter.TwitterCardIE
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.twitter.TwitterShortenerIE
import com.anydownlod.core.extract.twitter.TwitterSpacesIE
import com.anydownlod.core.extract.vk.VKIE
import com.anydownlod.core.extract.vimeo.VimeoIE
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
    fun twitterCard(http: ExtractorHttp): TwitterCardIE = TwitterCardIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitterAmplify(http: ExtractorHttp): TwitterAmplifyIE = TwitterAmplifyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitterBroadcast(http: ExtractorHttp): TwitterBroadcastIE = TwitterBroadcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitterSpaces(http: ExtractorHttp): TwitterSpacesIE = TwitterSpacesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitterShortener(http: ExtractorHttp): TwitterShortenerIE = TwitterShortenerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bilibili(http: ExtractorHttp): BiliBiliIE = BiliBiliIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bilibiliPlayer(http: ExtractorHttp): BiliBiliPlayerIE = BiliBiliPlayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vimeo(http: ExtractorHttp): VimeoIE = VimeoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun peerTube(http: ExtractorHttp): PeerTubeIE = PeerTubeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bbc(http: ExtractorHttp): BBCCoUkIE = BBCCoUkIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tiktok(http: ExtractorHttp): TikTokIE = TikTokIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tiktokVm(http: ExtractorHttp): TikTokVMIE = TikTokVMIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun soundcloud(http: ExtractorHttp): SoundcloudIE = SoundcloudIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun soundcloudEmbed(http: ExtractorHttp): SoundcloudEmbedIE = SoundcloudEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun facebook(http: ExtractorHttp): FacebookIE = FacebookIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun facebookReel(http: ExtractorHttp): FacebookReelIE = FacebookReelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun facebookPlugins(http: ExtractorHttp): FacebookPluginsVideoIE = FacebookPluginsVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun facebookRedirect(http: ExtractorHttp): FacebookRedirectURLIE = FacebookRedirectURLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitchVod(http: ExtractorHttp): TwitchVodIE = TwitchVodIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitchStream(http: ExtractorHttp): TwitchStreamIE = TwitchStreamIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun instagram(http: ExtractorHttp): InstagramIE = InstagramIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun instagramIos(http: ExtractorHttp): InstagramIOSIE = InstagramIOSIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun dailymotion(http: ExtractorHttp): DailymotionIE = DailymotionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vk(http: ExtractorHttp): VKIE = VKIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun patreon(http: ExtractorHttp): PatreonIE = PatreonIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun archiveOrg(http: ExtractorHttp): ArchiveOrgIE = ArchiveOrgIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun extractorRegistry(
        youtube: YoutubeIE,
        youtubeTab: YoutubeTabIE,
        twitter: TwitterIE,
        twitterCard: TwitterCardIE,
        twitterAmplify: TwitterAmplifyIE,
        twitterBroadcast: TwitterBroadcastIE,
        twitterSpaces: TwitterSpacesIE,
        twitterShortener: TwitterShortenerIE,
        bilibili: BiliBiliIE,
        bilibiliPlayer: BiliBiliPlayerIE,
        vimeo: VimeoIE,
        peerTube: PeerTubeIE,
        bbc: BBCCoUkIE,
        tiktok: TikTokIE,
        tiktokVm: TikTokVMIE,
        soundcloud: SoundcloudIE,
        soundcloudEmbed: SoundcloudEmbedIE,
        facebook: FacebookIE,
        facebookReel: FacebookReelIE,
        facebookPlugins: FacebookPluginsVideoIE,
        facebookRedirect: FacebookRedirectURLIE,
        twitchVod: TwitchVodIE,
        twitchStream: TwitchStreamIE,
        instagram: InstagramIE,
        instagramIos: InstagramIOSIE,
        dailymotion: DailymotionIE,
        vk: VKIE,
        patreon: PatreonIE,
        archiveOrg: ArchiveOrgIE,
    ): ExtractorRegistry = productionExtractorRegistry(
        youtube,
        youtubeTab,
        twitter,
        twitterCard,
        twitterAmplify,
        twitterBroadcast,
        twitterSpaces,
        twitterShortener,
        bilibili,
        bilibiliPlayer,
        vimeo,
        peerTube,
        bbc,
        tiktok,
        tiktokVm,
        soundcloud,
        soundcloudEmbed,
        facebook,
        facebookReel,
        facebookPlugins,
        facebookRedirect,
        twitchVod,
        twitchStream,
        instagram,
        instagramIos,
        dailymotion,
        vk,
        patreon,
        archiveOrg,
    )
}

/**
 * The production order the graph and the tests share: YouTube single video,
 * YouTube playlist tab, X/Twitter status, the T-125 Twitter remainder (card,
 * Amplify, broadcast, Spaces, shortener), then Bilibili and its player
 * delegate, then Vimeo, PeerTube, BBC, TikTok and its short-link delegate, then
 * SoundCloud and its embed delegate, then Facebook and its redirect helpers,
 * then the Twitch VOD before the Twitch channel extractor, then Instagram and
 * its iOS-scheme delegate, then Dailymotion, VK, Patreon, and archive.org. The
 * playlist extractor stays between the two YouTube entries so its
 * `/playlist?list=` match is reached before the single-video fallback; the
 * shorteners and the players are last because they only resolve and
 * re-dispatch.
 */
fun productionExtractorRegistry(
    youtube: YoutubeIE,
    youtubeTab: YoutubeTabIE,
    twitter: TwitterIE,
    twitterCard: TwitterCardIE,
    twitterAmplify: TwitterAmplifyIE,
    twitterBroadcast: TwitterBroadcastIE,
    twitterSpaces: TwitterSpacesIE,
    twitterShortener: TwitterShortenerIE,
    bilibili: BiliBiliIE,
    bilibiliPlayer: BiliBiliPlayerIE,
    vimeo: VimeoIE,
    peerTube: PeerTubeIE,
    bbc: BBCCoUkIE,
    tiktok: TikTokIE,
    tiktokVm: TikTokVMIE,
    soundcloud: SoundcloudIE,
    soundcloudEmbed: SoundcloudEmbedIE,
    facebook: FacebookIE,
    facebookReel: FacebookReelIE,
    facebookPlugins: FacebookPluginsVideoIE,
    facebookRedirect: FacebookRedirectURLIE,
    twitchVod: TwitchVodIE,
    twitchStream: TwitchStreamIE,
    instagram: InstagramIE,
    instagramIos: InstagramIOSIE,
    dailymotion: DailymotionIE,
    vk: VKIE,
    patreon: PatreonIE,
    archiveOrg: ArchiveOrgIE,
): ExtractorRegistry = ExtractorRegistry(
    listOf(
        youtube,
        youtubeTab,
        twitter,
        twitterCard,
        twitterAmplify,
        twitterBroadcast,
        twitterSpaces,
        twitterShortener,
        bilibili,
        bilibiliPlayer,
        vimeo,
        peerTube,
        bbc,
        tiktok,
        tiktokVm,
        soundcloud,
        soundcloudEmbed,
        facebook,
        facebookReel,
        facebookPlugins,
        facebookRedirect,
        twitchVod,
        twitchStream,
        instagram,
        instagramIos,
        dailymotion,
        vk,
        patreon,
        archiveOrg,
    ),
)

/**
 * Convenience for host classes and tests that hold a transfer and a runtime
 * but no graph yet. It constructs the same ordered list as the graph bindings.
 */
fun productionExtractorRegistry(http: ExtractorHttp, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(
        youtube = YoutubeIE(http, jsRuntime),
        youtubeTab = YoutubeTabIE(http),
        twitter = TwitterIE(http),
        twitterCard = TwitterCardIE(http),
        twitterAmplify = TwitterAmplifyIE(http),
        twitterBroadcast = TwitterBroadcastIE(http),
        twitterSpaces = TwitterSpacesIE(http),
        twitterShortener = TwitterShortenerIE(http),
        bilibili = BiliBiliIE(http),
        bilibiliPlayer = BiliBiliPlayerIE(http),
        vimeo = VimeoIE(http),
        peerTube = PeerTubeIE(http),
        bbc = BBCCoUkIE(http),
        tiktok = TikTokIE(http),
        tiktokVm = TikTokVMIE(http),
        soundcloud = SoundcloudIE(http),
        soundcloudEmbed = SoundcloudEmbedIE(http),
        facebook = FacebookIE(http),
        facebookReel = FacebookReelIE(http),
        facebookPlugins = FacebookPluginsVideoIE(http),
        facebookRedirect = FacebookRedirectURLIE(http),
        twitchVod = TwitchVodIE(http),
        twitchStream = TwitchStreamIE(http),
        instagram = InstagramIE(http),
        instagramIos = InstagramIOSIE(http),
        dailymotion = DailymotionIE(http),
        vk = VKIE(http),
        patreon = PatreonIE(http),
        archiveOrg = ArchiveOrgIE(http),
    )

/** Same list, built from a raw transfer; used by the host classes' constructors. */
fun productionExtractorRegistry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(ExtractorHttp(transfer), jsRuntime)
