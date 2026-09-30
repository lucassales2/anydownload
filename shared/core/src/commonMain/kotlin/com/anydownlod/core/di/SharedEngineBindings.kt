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
import com.anydownlod.core.extract.cbc.CBCGemContentIE
import com.anydownlod.core.extract.cbc.CBCGemIE
import com.anydownlod.core.extract.cbc.CBCGemLiveIE
import com.anydownlod.core.extract.cbc.CBCGemOlympicsIE
import com.anydownlod.core.extract.cbc.CBCGemPlaylistIE
import com.anydownlod.core.extract.cbc.CBCIE
import com.anydownlod.core.extract.cbc.CBCListenIE
import com.anydownlod.core.extract.cbc.CBCPlayerIE
import com.anydownlod.core.extract.cbc.CBCPlayerPlaylistIE
import com.anydownlod.core.extract.dailymotion.DailymotionIE
import com.anydownlod.core.extract.dplay.AmHistoryChannelIE
import com.anydownlod.core.extract.dplay.AnimalPlanetIE
import com.anydownlod.core.extract.dplay.CookingChannelIE
import com.anydownlod.core.extract.dplay.DestinationAmericaIE
import com.anydownlod.core.extract.dplay.DiscoveryLifeIE
import com.anydownlod.core.extract.dplay.DiscoveryNetworksDeIE
import com.anydownlod.core.extract.dplay.DiscoveryPlusIE
import com.anydownlod.core.extract.dplay.DiscoveryPlusIndiaIE
import com.anydownlod.core.extract.dplay.DiscoveryPlusIndiaShowIE
import com.anydownlod.core.extract.dplay.DiscoveryPlusItalyIE
import com.anydownlod.core.extract.dplay.DiscoveryPlusItalyShowIE
import com.anydownlod.core.extract.dplay.DPlayIE
import com.anydownlod.core.extract.dplay.FoodNetworkIE
import com.anydownlod.core.extract.dplay.GoDiscoveryIE
import com.anydownlod.core.extract.dplay.HGTVDeIE
import com.anydownlod.core.extract.dplay.HGTVUsaIE
import com.anydownlod.core.extract.dplay.InvestigationDiscoveryIE
import com.anydownlod.core.extract.dplay.ScienceChannelIE
import com.anydownlod.core.extract.dplay.TLCIE
import com.anydownlod.core.extract.dplay.TravelChannelIE
import com.anydownlod.core.extract.facebook.FacebookIE
import com.anydownlod.core.extract.facebook.FacebookPluginsVideoIE
import com.anydownlod.core.extract.facebook.FacebookRedirectURLIE
import com.anydownlod.core.extract.facebook.FacebookReelIE
import com.anydownlod.core.extract.instagram.InstagramIE
import com.anydownlod.core.extract.nbc.BravoTVIE
import com.anydownlod.core.extract.nbc.NBCIE
import com.anydownlod.core.extract.nbc.NBCNewsIE
import com.anydownlod.core.extract.nbc.NBCOlympicsIE
import com.anydownlod.core.extract.nbc.NBCStationsIE
import com.anydownlod.core.extract.nbc.SyfyIE
import com.anydownlod.core.extract.niconico.NiconicoIE
import com.anydownlod.core.extract.niconico.NiconicoPlaylistIE
import com.anydownlod.core.extract.niconico.NiconicoSeriesIE
import com.anydownlod.core.extract.niconico.NiconicoUserIE
import com.anydownlod.core.extract.niconico.NicovideoSearchURLIE
import com.anydownlod.core.extract.niconico.NicovideoTagURLIE
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
    fun dplay(http: ExtractorHttp): DPlayIE = DPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun hgtvDe(http: ExtractorHttp): HGTVDeIE = HGTVDeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun goDiscovery(http: ExtractorHttp): GoDiscoveryIE = GoDiscoveryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun travelChannel(http: ExtractorHttp): TravelChannelIE = TravelChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cookingChannel(http: ExtractorHttp): CookingChannelIE = CookingChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun hgtvUsa(http: ExtractorHttp): HGTVUsaIE = HGTVUsaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun foodNetwork(http: ExtractorHttp): FoodNetworkIE = FoodNetworkIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun destinationAmerica(http: ExtractorHttp): DestinationAmericaIE = DestinationAmericaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun investigationDiscovery(http: ExtractorHttp): InvestigationDiscoveryIE = InvestigationDiscoveryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun amHistoryChannel(http: ExtractorHttp): AmHistoryChannelIE = AmHistoryChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun scienceChannel(http: ExtractorHttp): ScienceChannelIE = ScienceChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryLife(http: ExtractorHttp): DiscoveryLifeIE = DiscoveryLifeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun animalPlanet(http: ExtractorHttp): AnimalPlanetIE = AnimalPlanetIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tlc(http: ExtractorHttp): TLCIE = TLCIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryPlus(http: ExtractorHttp): DiscoveryPlusIE = DiscoveryPlusIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryPlusIndia(http: ExtractorHttp): DiscoveryPlusIndiaIE = DiscoveryPlusIndiaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryNetworksDe(http: ExtractorHttp): DiscoveryNetworksDeIE = DiscoveryNetworksDeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryPlusItaly(http: ExtractorHttp): DiscoveryPlusItalyIE = DiscoveryPlusItalyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryPlusItalyShow(http: ExtractorHttp): DiscoveryPlusItalyShowIE = DiscoveryPlusItalyShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun discoveryPlusIndiaShow(http: ExtractorHttp): DiscoveryPlusIndiaShowIE = DiscoveryPlusIndiaShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbc(http: ExtractorHttp): NBCIE = NBCIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbcNews(http: ExtractorHttp): NBCNewsIE = NBCNewsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbcOlympics(http: ExtractorHttp): NBCOlympicsIE = NBCOlympicsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbcStations(http: ExtractorHttp): NBCStationsIE = NBCStationsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bravoTv(http: ExtractorHttp): BravoTVIE = BravoTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun syfy(http: ExtractorHttp): SyfyIE = SyfyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbc(http: ExtractorHttp): CBCIE = CBCIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcPlayer(http: ExtractorHttp): CBCPlayerIE = CBCPlayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcPlayerPlaylist(http: ExtractorHttp): CBCPlayerPlaylistIE = CBCPlayerPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcGem(http: ExtractorHttp): CBCGemIE = CBCGemIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcGemPlaylist(http: ExtractorHttp): CBCGemPlaylistIE = CBCGemPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcGemContent(http: ExtractorHttp): CBCGemContentIE = CBCGemContentIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcGemOlympics(http: ExtractorHttp): CBCGemOlympicsIE = CBCGemOlympicsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcGemLive(http: ExtractorHttp): CBCGemLiveIE = CBCGemLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbcListen(http: ExtractorHttp): CBCListenIE = CBCListenIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconico(http: ExtractorHttp): NiconicoIE = NiconicoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconicoPlaylist(http: ExtractorHttp): NiconicoPlaylistIE = NiconicoPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconicoSeries(http: ExtractorHttp): NiconicoSeriesIE = NiconicoSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nicovideoSearchUrl(http: ExtractorHttp): NicovideoSearchURLIE = NicovideoSearchURLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nicovideoTagUrl(http: ExtractorHttp): NicovideoTagURLIE = NicovideoTagURLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconicoUser(http: ExtractorHttp): NiconicoUserIE = NiconicoUserIE(http)

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
        dplay: DPlayIE,
        hgtvDe: HGTVDeIE,
        goDiscovery: GoDiscoveryIE,
        travelChannel: TravelChannelIE,
        cookingChannel: CookingChannelIE,
        hgtvUsa: HGTVUsaIE,
        foodNetwork: FoodNetworkIE,
        destinationAmerica: DestinationAmericaIE,
        investigationDiscovery: InvestigationDiscoveryIE,
        amHistoryChannel: AmHistoryChannelIE,
        scienceChannel: ScienceChannelIE,
        discoveryLife: DiscoveryLifeIE,
        animalPlanet: AnimalPlanetIE,
        tlc: TLCIE,
        discoveryPlus: DiscoveryPlusIE,
        discoveryPlusIndia: DiscoveryPlusIndiaIE,
        discoveryNetworksDe: DiscoveryNetworksDeIE,
        discoveryPlusItaly: DiscoveryPlusItalyIE,
        discoveryPlusItalyShow: DiscoveryPlusItalyShowIE,
        discoveryPlusIndiaShow: DiscoveryPlusIndiaShowIE,
        nbc: NBCIE,
        nbcNews: NBCNewsIE,
        nbcOlympics: NBCOlympicsIE,
        nbcStations: NBCStationsIE,
        bravoTv: BravoTVIE,
        syfy: SyfyIE,
        cbc: CBCIE,
        cbcPlayer: CBCPlayerIE,
        cbcPlayerPlaylist: CBCPlayerPlaylistIE,
        cbcGem: CBCGemIE,
        cbcGemPlaylist: CBCGemPlaylistIE,
        cbcGemContent: CBCGemContentIE,
        cbcGemOlympics: CBCGemOlympicsIE,
        cbcGemLive: CBCGemLiveIE,
        cbcListen: CBCListenIE,
        niconico: NiconicoIE,
        niconicoPlaylist: NiconicoPlaylistIE,
        niconicoSeries: NiconicoSeriesIE,
        nicovideoSearchUrl: NicovideoSearchURLIE,
        nicovideoTagUrl: NicovideoTagURLIE,
        niconicoUser: NiconicoUserIE,
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
        dplay,
        hgtvDe,
        goDiscovery,
        travelChannel,
        cookingChannel,
        hgtvUsa,
        foodNetwork,
        destinationAmerica,
        investigationDiscovery,
        amHistoryChannel,
        scienceChannel,
        discoveryLife,
        animalPlanet,
        tlc,
        discoveryPlus,
        discoveryPlusIndia,
        discoveryNetworksDe,
        discoveryPlusItaly,
        discoveryPlusItalyShow,
        discoveryPlusIndiaShow,
        nbc,
        nbcNews,
        nbcOlympics,
        nbcStations,
        bravoTv,
        syfy,
        cbc,
        cbcPlayer,
        cbcPlayerPlaylist,
        cbcGem,
        cbcGemPlaylist,
        cbcGemContent,
        cbcGemOlympics,
        cbcGemLive,
        cbcListen,
        niconico,
        niconicoPlaylist,
        niconicoSeries,
        nicovideoSearchUrl,
        nicovideoTagUrl,
        niconicoUser,
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
    dplay: DPlayIE,
    hgtvDe: HGTVDeIE,
    goDiscovery: GoDiscoveryIE,
    travelChannel: TravelChannelIE,
    cookingChannel: CookingChannelIE,
    hgtvUsa: HGTVUsaIE,
    foodNetwork: FoodNetworkIE,
    destinationAmerica: DestinationAmericaIE,
    investigationDiscovery: InvestigationDiscoveryIE,
    amHistoryChannel: AmHistoryChannelIE,
    scienceChannel: ScienceChannelIE,
    discoveryLife: DiscoveryLifeIE,
    animalPlanet: AnimalPlanetIE,
    tlc: TLCIE,
    discoveryPlus: DiscoveryPlusIE,
    discoveryPlusIndia: DiscoveryPlusIndiaIE,
    discoveryNetworksDe: DiscoveryNetworksDeIE,
    discoveryPlusItaly: DiscoveryPlusItalyIE,
    discoveryPlusItalyShow: DiscoveryPlusItalyShowIE,
    discoveryPlusIndiaShow: DiscoveryPlusIndiaShowIE,
    nbc: NBCIE,
    nbcNews: NBCNewsIE,
    nbcOlympics: NBCOlympicsIE,
    nbcStations: NBCStationsIE,
    bravoTv: BravoTVIE,
    syfy: SyfyIE,
    cbc: CBCIE,
    cbcPlayer: CBCPlayerIE,
    cbcPlayerPlaylist: CBCPlayerPlaylistIE,
    cbcGem: CBCGemIE,
    cbcGemPlaylist: CBCGemPlaylistIE,
    cbcGemContent: CBCGemContentIE,
    cbcGemOlympics: CBCGemOlympicsIE,
    cbcGemLive: CBCGemLiveIE,
    cbcListen: CBCListenIE,
    niconico: NiconicoIE,
    niconicoPlaylist: NiconicoPlaylistIE,
    niconicoSeries: NiconicoSeriesIE,
    nicovideoSearchUrl: NicovideoSearchURLIE,
    nicovideoTagUrl: NicovideoTagURLIE,
    niconicoUser: NiconicoUserIE,
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
        dplay,
        hgtvDe,
        goDiscovery,
        travelChannel,
        cookingChannel,
        hgtvUsa,
        foodNetwork,
        destinationAmerica,
        investigationDiscovery,
        amHistoryChannel,
        scienceChannel,
        discoveryLife,
        animalPlanet,
        tlc,
        discoveryPlus,
        discoveryPlusIndia,
        discoveryNetworksDe,
        discoveryPlusItaly,
        discoveryPlusItalyShow,
        discoveryPlusIndiaShow,
        nbc,
        nbcNews,
        nbcOlympics,
        nbcStations,
        bravoTv,
        syfy,
        cbc,
        cbcPlayer,
        cbcPlayerPlaylist,
        cbcGem,
        cbcGemPlaylist,
        cbcGemContent,
        cbcGemOlympics,
        cbcGemLive,
        cbcListen,
        niconico,
        niconicoPlaylist,
        niconicoSeries,
        nicovideoSearchUrl,
        nicovideoTagUrl,
        niconicoUser,
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
        dplay = DPlayIE(http),
        hgtvDe = HGTVDeIE(http),
        goDiscovery = GoDiscoveryIE(http),
        travelChannel = TravelChannelIE(http),
        cookingChannel = CookingChannelIE(http),
        hgtvUsa = HGTVUsaIE(http),
        foodNetwork = FoodNetworkIE(http),
        destinationAmerica = DestinationAmericaIE(http),
        investigationDiscovery = InvestigationDiscoveryIE(http),
        amHistoryChannel = AmHistoryChannelIE(http),
        scienceChannel = ScienceChannelIE(http),
        discoveryLife = DiscoveryLifeIE(http),
        animalPlanet = AnimalPlanetIE(http),
        tlc = TLCIE(http),
        discoveryPlus = DiscoveryPlusIE(http),
        discoveryPlusIndia = DiscoveryPlusIndiaIE(http),
        discoveryNetworksDe = DiscoveryNetworksDeIE(http),
        discoveryPlusItaly = DiscoveryPlusItalyIE(http),
        discoveryPlusItalyShow = DiscoveryPlusItalyShowIE(http),
        discoveryPlusIndiaShow = DiscoveryPlusIndiaShowIE(http),
        nbc = NBCIE(http),
        nbcNews = NBCNewsIE(http),
        nbcOlympics = NBCOlympicsIE(http),
        nbcStations = NBCStationsIE(http),
        bravoTv = BravoTVIE(http),
        syfy = SyfyIE(http),
        cbc = CBCIE(http),
        cbcPlayer = CBCPlayerIE(http),
        cbcPlayerPlaylist = CBCPlayerPlaylistIE(http),
        cbcGem = CBCGemIE(http),
        cbcGemPlaylist = CBCGemPlaylistIE(http),
        cbcGemContent = CBCGemContentIE(http),
        cbcGemOlympics = CBCGemOlympicsIE(http),
        cbcGemLive = CBCGemLiveIE(http),
        cbcListen = CBCListenIE(http),
        niconico = NiconicoIE(http),
        niconicoPlaylist = NiconicoPlaylistIE(http),
        niconicoSeries = NiconicoSeriesIE(http),
        nicovideoSearchUrl = NicovideoSearchURLIE(http),
        nicovideoTagUrl = NicovideoTagURLIE(http),
        niconicoUser = NiconicoUserIE(http),
    )

/** Same list, built from a raw transfer; used by the host classes' constructors. */
fun productionExtractorRegistry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(ExtractorHttp(transfer), jsRuntime)
