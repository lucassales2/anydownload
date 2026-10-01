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
package com.anydownload.core.di

import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.bbc.BBCCoUkIE
import com.anydownload.core.extract.bandlab.BandlabIE
import com.anydownload.core.extract.bandlab.BandlabPlaylistIE
import com.anydownload.core.extract.adn.ADNIE
import com.anydownload.core.extract.adn.ADNSeasonIE
import com.anydownload.core.extract.agora.TokFMAuditionIE
import com.anydownload.core.extract.agora.TokFMPodcastIE
import com.anydownload.core.extract.agora.WyborczaPodcastIE
import com.anydownload.core.extract.agora.WyborczaVideoIE
import com.anydownload.core.extract.art19.Art19IE
import com.anydownload.core.extract.art19.Art19ShowIE
import com.anydownload.core.extract.arte.ArteTVCategoryIE
import com.anydownload.core.extract.arte.ArteTVEmbedIE
import com.anydownload.core.extract.arte.ArteTVIE
import com.anydownload.core.extract.arte.ArteTVPlaylistIE
import com.anydownload.core.extract.amazonminitv.AmazonMiniTvIE
import com.anydownload.core.extract.amazonminitv.AmazonMiniTvSeasonIE
import com.anydownload.core.extract.amazonminitv.AmazonMiniTvSeriesIE
import com.anydownload.core.extract.audius.AudiusIE
import com.anydownload.core.extract.audius.AudiusPlaylistIE
import com.anydownload.core.extract.audius.AudiusProfileIE
import com.anydownload.core.extract.audius.AudiusTrackIE
import com.anydownload.core.extract.bandcamp.BandcampAlbumIE
import com.anydownload.core.extract.bandcamp.BandcampIE
import com.anydownload.core.extract.bandcamp.BandcampUserIE
import com.anydownload.core.extract.bandcamp.BandcampWeeklyIE
import com.anydownload.core.extract.brainpop.BrainPOPELLIE
import com.anydownload.core.extract.brainpop.BrainPOPEspIE
import com.anydownload.core.extract.brainpop.BrainPOPFrIE
import com.anydownload.core.extract.brainpop.BrainPOPIE
import com.anydownload.core.extract.brainpop.BrainPOPIlIE
import com.anydownload.core.extract.brainpop.BrainPOPJrIE
import com.anydownload.core.extract.brightcove.BrightcoveLegacyIE
import com.anydownload.core.extract.bitchute.BitChuteChannelIE
import com.anydownload.core.extract.bitchute.BitChuteIE
import com.anydownload.core.extract.bluesky.BlueskyIE
import com.anydownload.core.extract.boosty.BoostyIE
import com.anydownload.core.extract.brightcove.BrightcoveNewIE
import com.anydownload.core.extract.afreecatv.AfreecaTVCatchStoryIE
import com.anydownload.core.extract.afreecatv.AfreecaTVIE
import com.anydownload.core.extract.afreecatv.AfreecaTVLiveIE
import com.anydownload.core.extract.afreecatv.AfreecaTVUserIE
import com.anydownload.core.extract.abc.ABCIE
import com.anydownload.core.extract.abc.ABCIViewIE
import com.anydownload.core.extract.abc.ABCIViewShowSeriesIE
import com.anydownload.core.extract.aenetworks.AENetworksCollectionIE
import com.anydownload.core.extract.aenetworks.AENetworksIE
import com.anydownload.core.extract.aenetworks.AENetworksShowIE
import com.anydownload.core.extract.aenetworks.BiographyIE
import com.anydownload.core.extract.aenetworks.HistoryPlayerIE
import com.anydownload.core.extract.aenetworks.HistoryTopicIE
import com.anydownload.core.extract.anvato.AnvatoIE
import com.anydownload.core.extract.abematv.AbemaTVIE
import com.anydownload.core.extract.abematv.AbemaTVTitleIE
import com.anydownload.core.extract.archiveorg.ArchiveOrgIE
import com.anydownload.core.extract.ard.ARDAudiothekIE
import com.anydownload.core.extract.ard.ARDAudiothekPlaylistIE
import com.anydownload.core.extract.ard.ARDBetaMediathekIE
import com.anydownload.core.extract.ard.ARDMediathekCollectionIE
import com.anydownload.core.extract.cbc.CBCGemContentIE
import com.anydownload.core.extract.cbc.CBCGemIE
import com.anydownload.core.extract.cbc.CBCGemLiveIE
import com.anydownload.core.extract.cbc.CBCGemOlympicsIE
import com.anydownload.core.extract.cbc.CBCGemPlaylistIE
import com.anydownload.core.extract.cbc.CBCIE
import com.anydownload.core.extract.cda.CDAFolderIE
import com.anydownload.core.extract.cnn.CNNIE
import com.anydownload.core.extract.cnn.CNNIndonesiaIE
import com.anydownload.core.extract.cda.CDAIE
import com.anydownload.core.extract.cspan.CSpanCongressIE
import com.anydownload.core.extract.cspan.CSpanIE
import com.anydownload.core.extract.cbsnews.CBSLocalArticleIE
import com.anydownload.core.extract.condenast.CondeNastIE
import com.anydownload.core.extract.cbsnews.CBSLocalIE
import com.anydownload.core.extract.cbsnews.CBSLocalLiveIE
import com.anydownload.core.extract.cbsnews.CBSNewsEmbedIE
import com.anydownload.core.extract.cbsnews.CBSNewsIE
import com.anydownload.core.extract.cbs.CBSIE
import com.anydownload.core.extract.cbs.ParamountPressExpressIE
import com.anydownload.core.extract.ceskatelevize.CeskaTelevizeIE
import com.anydownload.core.extract.cbsnews.CBSNewsLiveIE
import com.anydownload.core.extract.cbsnews.CBSNewsLiveVideoIE
import com.anydownload.core.extract.cbc.CBCListenIE
import com.anydownload.core.extract.cbc.CBCPlayerIE
import com.anydownload.core.extract.cbc.CBCPlayerPlaylistIE
import com.anydownload.core.extract.dailymotion.DailymotionIE
import com.anydownload.core.extract.dropout.DropoutIE
import com.anydownload.core.extract.dropout.DropoutSeasonIE
import com.anydownload.core.extract.douyutv.DouyuShowIE
import com.anydownload.core.extract.douyutv.DouyuTVIE
import com.anydownload.core.extract.dplay.AmHistoryChannelIE
import com.anydownload.core.extract.dplay.AnimalPlanetIE
import com.anydownload.core.extract.dplay.CookingChannelIE
import com.anydownload.core.extract.dplay.DestinationAmericaIE
import com.anydownload.core.extract.dplay.DiscoveryLifeIE
import com.anydownload.core.extract.dplay.DiscoveryNetworksDeIE
import com.anydownload.core.extract.dplay.DiscoveryPlusIE
import com.anydownload.core.extract.dplay.DiscoveryPlusIndiaIE
import com.anydownload.core.extract.dplay.DiscoveryPlusIndiaShowIE
import com.anydownload.core.extract.dplay.DiscoveryPlusItalyIE
import com.anydownload.core.extract.dplay.DiscoveryPlusItalyShowIE
import com.anydownload.core.extract.digitalconcerthall.DigitalConcertHallIE
import com.anydownload.core.extract.dplay.DPlayIE
import com.anydownload.core.extract.dplay.FoodNetworkIE
import com.anydownload.core.extract.dplay.GoDiscoveryIE
import com.anydownload.core.extract.dplay.HGTVDeIE
import com.anydownload.core.extract.dplay.HGTVUsaIE
import com.anydownload.core.extract.dplay.InvestigationDiscoveryIE
import com.anydownload.core.extract.dplay.ScienceChannelIE
import com.anydownload.core.extract.dplay.TLCIE
import com.anydownload.core.extract.dplay.TravelChannelIE
import com.anydownload.core.extract.espn.ESPNArticleIE
import com.anydownload.core.extract.espn.ESPNCricInfoIE
import com.anydownload.core.extract.drtv.DRTVLiveIE
import com.anydownload.core.extract.drtv.DRTVSeasonIE
import com.anydownload.core.extract.drtv.DRTVSeriesIE
import com.anydownload.core.extract.drtv.DRTVIE
import com.anydownload.core.extract.ertgr.ERTFlixCodenameIE
import com.anydownload.core.extract.err.ERRArhiivIE
import com.anydownload.core.extract.err.ERRJupiterIE
import com.anydownload.core.extract.ertgr.ERTFlixIE
import com.anydownload.core.extract.ertgr.ERTWebtvEmbedIE
import com.anydownload.core.extract.espn.ESPNIE
import com.anydownload.core.extract.espn.FiveThirtyEightIE
import com.anydownload.core.extract.espn.WatchESPNIE
import com.anydownload.core.extract.facebook.FacebookIE
import com.anydownload.core.extract.facebook.FacebookPluginsVideoIE
import com.anydownload.core.extract.facebook.FacebookRedirectURLIE
import com.anydownload.core.extract.facebook.FacebookReelIE
import com.anydownload.core.extract.fc2.FC2EmbedIE
import com.anydownload.core.extract.fc2.FC2IE
import com.anydownload.core.extract.fc2.FC2LiveIE
import com.anydownload.core.extract.gamejolt.GameJoltCommunityIE
import com.anydownload.core.extract.gamejolt.GameJoltGameIE
import com.anydownload.core.extract.gamejolt.GameJoltGameSoundtrackIE
import com.anydownload.core.extract.floatplane.FloatplaneChannelIE
import com.anydownload.core.extract.floatplane.FloatplaneIE
import com.anydownload.core.extract.fourtube.FourTubeIE
import com.anydownload.core.extract.fourtube.FuxIE
import com.anydownload.core.extract.fourtube.PornerBrosIE
import com.anydownload.core.extract.fourtube.PornTubeIE
import com.anydownload.core.extract.francetv.FranceTVInfoIE
import com.anydownload.core.extract.francetv.FranceTVSiteIE
import com.anydownload.core.extract.francetv.FranceTVIE
import com.anydownload.core.extract.gamejolt.GameJoltIE
import com.anydownload.core.extract.gamejolt.GameJoltSearchIE
import com.anydownload.core.extract.gamejolt.GameJoltUserIE
import com.anydownload.core.extract.glomex.GlomexEmbedIE
import com.anydownload.core.extract.glomex.GlomexIE
import com.anydownload.core.extract.go.GoIE
import com.anydownload.core.extract.googledrive.GoogleDriveFolderIE
import com.anydownload.core.extract.googledrive.GoogleDriveIE
import com.anydownload.core.extract.goplay.GoPlayIE
import com.anydownload.core.extract.hotstar.HotStarIE
import com.anydownload.core.extract.hotstar.HotStarPrefixIE
import com.anydownload.core.extract.hotstar.HotStarSeriesIE
import com.anydownload.core.extract.iqiyi.IqAlbumIE
import com.anydownload.core.extract.idagio.IdagioAlbumIE
import com.anydownload.core.extract.idagio.IdagioPersonalPlaylistIE
import com.anydownload.core.extract.idagio.IdagioPlaylistIE
import com.anydownload.core.extract.idagio.IdagioRecordingIE
import com.anydownload.core.extract.idagio.IdagioTrackIE
import com.anydownload.core.extract.ign.IGNArticleIE
import com.anydownload.core.extract.ign.IGNIE
import com.anydownload.core.extract.ign.IGNVideoIE
import com.anydownload.core.extract.iqiyi.IqIE
import com.anydownload.core.extract.iqiyi.IqiyiIE
import com.anydownload.core.extract.imgur.ImgurAlbumIE
import com.anydownload.core.extract.imgur.ImgurGalleryIE
import com.anydownload.core.extract.imgur.ImgurIE
import com.anydownload.core.extract.instagram.InstagramIE
import com.anydownload.core.extract.itv.ITVBTCCIE
import com.anydownload.core.extract.itv.ITVIE
import com.anydownload.core.extract.iprima.IPrimaCNNIE
import com.anydownload.core.extract.iprima.IPrimaIE
import com.anydownload.core.extract.ivi.IviCompilationIE
import com.anydownload.core.extract.ivi.IviIE
import com.anydownload.core.extract.iwara.IwaraIE
import com.anydownload.core.extract.iwara.IwaraPlaylistIE
import com.anydownload.core.extract.iwara.IwaraUserIE
import com.anydownload.core.extract.japandiet.SangiinIE
import com.anydownload.core.extract.japandiet.SangiinInstructionIE
import com.anydownload.core.extract.japandiet.ShugiinItvLiveIE
import com.anydownload.core.extract.japandiet.ShugiinItvLiveRoomIE
import com.anydownload.core.extract.japandiet.ShugiinItvVodIE
import com.anydownload.core.extract.jiosaavn.JioSaavnAlbumIE
import com.anydownload.core.extract.jiosaavn.JioSaavnArtistIE
import com.anydownload.core.extract.jiosaavn.JioSaavnPlaylistIE
import com.anydownload.core.extract.jiosaavn.JioSaavnShowIE
import com.anydownload.core.extract.jiosaavn.JioSaavnShowPlaylistIE
import com.anydownload.core.extract.jiosaavn.JioSaavnSongIE
import com.anydownload.core.extract.kaltura.KalturaIE
import com.anydownload.core.extract.kick.KickClipIE
import com.anydownload.core.extract.kick.KickIE
import com.anydownload.core.extract.kick.KickVODIE
import com.anydownload.core.extract.kuwo.KuwoAlbumIE
import com.anydownload.core.extract.kuwo.KuwoCategoryIE
import com.anydownload.core.extract.kuwo.KuwoChartIE
import com.anydownload.core.extract.kuwo.KuwoIE
import com.anydownload.core.extract.kuwo.KuwoMvIE
import com.anydownload.core.extract.kuwo.KuwoSingerIE
import com.anydownload.core.extract.nba.NBAChannelIE
import com.anydownload.core.extract.naver.NaverIE
import com.anydownload.core.extract.naver.NaverLiveIE
import com.anydownload.core.extract.nba.NBAEmbedIE
import com.anydownload.core.extract.nba.NBAIE
import com.anydownload.core.extract.nba.NBAWatchCollectionIE
import com.anydownload.core.extract.nba.NBAWatchEmbedIE
import com.anydownload.core.extract.nba.NBAWatchIE
import com.anydownload.core.extract.nfb.NFBIE
import com.anydownload.core.extract.nfb.NFBSeriesIE
import com.anydownload.core.extract.nfl.NFLArticleIE
import com.anydownload.core.extract.ninaprotocol.NinaProtocolIE
import com.anydownload.core.extract.newgrounds.NewgroundsIE
import com.anydownload.core.extract.nekohacker.NekoHackerIE
import com.anydownload.core.extract.newgrounds.NewgroundsPlaylistIE
import com.anydownload.core.extract.newgrounds.NewgroundsUserIE
import com.anydownload.core.extract.nfl.NFLIE
import com.anydownload.core.extract.nfl.NFLPlusEpisodeIE
import com.anydownload.core.extract.nfl.NFLPlusReplayIE
import com.anydownload.core.extract.nbc.BravoTVIE
import com.anydownload.core.extract.ndr.NDRIE
import com.anydownload.core.extract.ndr.NDREmbedBaseIE
import com.anydownload.core.extract.ndr.NDREmbedIE
import com.anydownload.core.extract.ndr.NJoyEmbedIE
import com.anydownload.core.extract.ndr.NJoyIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicAlbumIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicDjRadioIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicListIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicMvIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicProgramIE
import com.anydownload.core.extract.neteasemusic.NetEaseMusicSingerIE
import com.anydownload.core.extract.nexx.NexxEmbedIE
import com.anydownload.core.extract.nexx.NexxIE
import com.anydownload.core.extract.niconicochannelplus.NiconicoChannelPlusChannelLivesIE
import com.anydownload.core.extract.niconicochannelplus.NiconicoChannelPlusChannelVideosIE
import com.anydownload.core.extract.niconicochannelplus.NiconicoChannelPlusIE
import com.anydownload.core.extract.nhk.NhkForSchoolBangumiIE
import com.anydownload.core.extract.nhk.NhkForSchoolProgramListIE
import com.anydownload.core.extract.nhk.NhkForSchoolSubjectIE
import com.anydownload.core.extract.nhk.NhkRadioNewsPageIE
import com.anydownload.core.extract.nhk.NhkRadiruIE
import com.anydownload.core.extract.nhk.NhkRadiruLiveIE
import com.anydownload.core.extract.nhk.NhkVodIE
import com.anydownload.core.extract.nhk.NhkVodProgramIE
import com.anydownload.core.extract.lbry.LBRYChannelIE
import com.anydownload.core.extract.lbry.LBRYIE
import com.anydownload.core.extract.lbry.LBRYPlaylistIE
import com.anydownload.core.extract.lifenews.LifeEmbedIE
import com.anydownload.core.extract.lifenews.LifeNewsIE
import com.anydownload.core.extract.linkedin.LinkedInEventsIE
import com.anydownload.core.extract.linkedin.LinkedInIE
import com.anydownload.core.extract.linkedin.LinkedInLearningCourseIE
import com.anydownload.core.extract.linkedin.LinkedInLearningIE
import com.anydownload.core.extract.loom.LoomFolderIE
import com.anydownload.core.extract.loom.LoomIE
import com.anydownload.core.extract.mediasite.MediasiteCatalogIE
import com.anydownload.core.extract.mediaset.MediasetIE
import com.anydownload.core.extract.mediaset.MediasetShowIE
import com.anydownload.core.extract.mediastream.MediaStreamIE
import com.anydownload.core.extract.mediastream.WinSportsVideoIE
import com.anydownload.core.extract.mailru.MailRuIE
import com.anydownload.core.extract.mailru.MailRuMusicIE
import com.anydownload.core.extract.mailru.MailRuMusicSearchIE
import com.anydownload.core.extract.mediasite.MediasiteIE
import com.anydownload.core.extract.mediasite.MediasiteNamedCatalogIE
import com.anydownload.core.extract.mlb.MLBArticleIE
import com.anydownload.core.extract.microsoftembed.MicrosoftBuildIE
import com.anydownload.core.extract.microsoftembed.MicrosoftEmbedIE
import com.anydownload.core.extract.microsoftembed.MicrosoftLearnEpisodeIE
import com.anydownload.core.extract.microsoftembed.MicrosoftLearnPlaylistIE
import com.anydownload.core.extract.microsoftembed.MicrosoftLearnSessionIE
import com.anydownload.core.extract.microsoftembed.MicrosoftMediusIE
import com.anydownload.core.extract.lsm.LSMLREmbedIE
import com.anydownload.core.extract.lsm.LSMLTVEmbedIE
import com.anydownload.core.extract.lsm.LSMReplayIE
import com.anydownload.core.extract.mixcloud.MixcloudIE
import com.anydownload.core.extract.mixcloud.MixcloudPlaylistIE
import com.anydownload.core.extract.mixcloud.MixcloudUserIE
import com.anydownload.core.extract.mlb.MLBIE
import com.anydownload.core.extract.mlb.MLBTVIE
import com.anydownload.core.extract.mlb.MLBVideoIE
import com.anydownload.core.extract.mxplayer.MxplayerIE
import com.anydownload.core.extract.mxplayer.MxplayerRedirectIE
import com.anydownload.core.extract.mxplayer.MxplayerSeasonIE
import com.anydownload.core.extract.mxplayer.MxplayerShowIE
import com.anydownload.core.extract.msn.MSNIE
import com.anydownload.core.extract.mtv.MTVIE
import com.anydownload.core.extract.nbc.NBCIE
import com.anydownload.core.extract.nbc.NBCNewsIE
import com.anydownload.core.extract.nbc.NBCOlympicsIE
import com.anydownload.core.extract.nbc.NBCStationsIE
import com.anydownload.core.extract.nebula.NebulaChannelIE
import com.anydownload.core.extract.nebula.NebulaClassIE
import com.anydownload.core.extract.nebula.NebulaIE
import com.anydownload.core.extract.nebula.NebulaSeasonIE
import com.anydownload.core.extract.nebula.NebulaSubscriptionsIE
import com.anydownload.core.extract.nbc.SyfyIE
import com.anydownload.core.extract.niconico.NiconicoIE
import com.anydownload.core.extract.npo.AndereTijdenIE
import com.anydownload.core.extract.npo.HetKlokhuisIE
import com.anydownload.core.extract.nitter.NitterIE
import com.anydownload.core.extract.npo.NPOIE
import com.anydownload.core.extract.npo.NPOLiveIE
import com.anydownload.core.extract.npo.NPORadioFragmentIE
import com.anydownload.core.extract.npo.NPORadioIE
import com.anydownload.core.extract.npo.SchoolTVIE
import com.anydownload.core.extract.npo.VPROIE
import com.anydownload.core.extract.npo.WNLIE
import com.anydownload.core.extract.nova.NovaEmbedIE
import com.anydownload.core.extract.nova.NovaIE
import com.anydownload.core.extract.nrk.NRKIE
import com.anydownload.core.extract.nrk.NRKPlaylistIE
import com.anydownload.core.extract.nrk.NRKRadioPodkastIE
import com.anydownload.core.extract.nrk.NRKSkoleIE
import com.anydownload.core.extract.nrk.NRKTVDirekteIE
import com.anydownload.core.extract.nrk.NRKTVEpisodeIE
import com.anydownload.core.extract.nrk.NRKTVEpisodesIE
import com.anydownload.core.extract.nrk.NRKTVSeasonIE
import com.anydownload.core.extract.nrk.NRKTVSeriesIE
import com.anydownload.core.extract.nrk.NRKTVIE
import com.anydownload.core.extract.niconico.NiconicoPlaylistIE
import com.anydownload.core.extract.niconico.NiconicoSeriesIE
import com.anydownload.core.extract.niconico.NiconicoUserIE
import com.anydownload.core.extract.niconico.NicovideoSearchURLIE
import com.anydownload.core.extract.niconico.NicovideoTagURLIE
import com.anydownload.core.extract.instagram.InstagramIOSIE
import com.anydownload.core.extract.bilibili.BiliBiliIE
import com.anydownload.core.extract.bilibili.BiliBiliPlayerIE
import com.anydownload.core.extract.peertube.PeerTubeIE
import com.anydownload.core.extract.prx.PRXAccountIE
import com.anydownload.core.extract.prx.PRXSeriesIE
import com.anydownload.core.extract.prx.PRXStoryIE
import com.anydownload.core.extract.qqmusic.QQMusicAlbumIE
import com.anydownload.core.extract.qqmusic.QQMusicIE
import com.anydownload.core.extract.qqmusic.QQMusicPlaylistIE
import com.anydownload.core.extract.qqmusic.QQMusicSingerIE
import com.anydownload.core.extract.qqmusic.QQMusicToplistIE
import com.anydownload.core.extract.qqmusic.QQMusicVideoIE
import com.anydownload.core.extract.radiko.RadikoIE
import com.anydownload.core.extract.radiko.RadikoRadioIE
import com.anydownload.core.extract.radiofrance.FranceCultureIE
import com.anydownload.core.extract.radiofrance.RadioFranceIE
import com.anydownload.core.extract.radiofrance.RadioFranceLiveIE
import com.anydownload.core.extract.radiofrance.RadioFrancePodcastIE
import com.anydownload.core.extract.radiofrance.RadioFranceProfileIE
import com.anydownload.core.extract.radiofrance.RadioFranceProgramScheduleIE
import com.anydownload.core.extract.rai.RaiCulturaIE
import com.anydownload.core.extract.rai.RaiIE
import com.anydownload.core.extract.rai.RaiNewsIE
import com.anydownload.core.extract.rai.RaiPlayIE
import com.anydownload.core.extract.rai.RaiPlayLiveIE
import com.anydownload.core.extract.rai.RaiPlayPlaylistIE
import com.anydownload.core.extract.rai.RaiPlaySoundIE
import com.anydownload.core.extract.rai.RaiPlaySoundLiveIE
import com.anydownload.core.extract.redgifs.RedGifsIE
import com.anydownload.core.extract.redgifs.RedGifsSearchIE
import com.anydownload.core.extract.redgifs.RedGifsUserIE
import com.anydownload.core.extract.rokfin.RokfinChannelIE
import com.anydownload.core.extract.rcs.RCSEmbedsIE
import com.anydownload.core.extract.rcs.RCSIE
import com.anydownload.core.extract.rcs.RCSVariousIE
import com.anydownload.core.extract.rcti.RCTIPlusIE
import com.anydownload.core.extract.rcti.RCTIPlusSeriesIE
import com.anydownload.core.extract.rcti.RCTIPlusTVIE
import com.anydownload.core.extract.redbee.ParliamentLiveUKIE
import com.anydownload.core.extract.redbee.RTBFIE
import com.anydownload.core.extract.reddit.RedditIE
import com.anydownload.core.extract.rokfin.RokfinIE
import com.anydownload.core.extract.rokfin.RokfinStackIE
import com.anydownload.core.extract.rai.RaiPlaySoundPlaylistIE
import com.anydownload.core.extract.rai.RaiSudtirolIE
import com.anydownload.core.extract.openrec.OpenRecCaptureIE
import com.anydownload.core.extract.omnyfm.OmnyfmIE
import com.anydownload.core.extract.omnyfm.OmnyfmPlaylistIE
import com.anydownload.core.extract.omnyfm.OmnyfmShowIE
import com.anydownload.core.extract.openrec.OpenRecChannelIE
import com.anydownload.core.extract.openrec.OpenRecChannelSearchIE
import com.anydownload.core.extract.nytimes.NYTimesArticleIE
import com.anydownload.core.extract.nytimes.NYTimesCookingIE
import com.anydownload.core.extract.nytimes.NYTimesCookingRecipeIE
import com.anydownload.core.extract.nytimes.NYTimesIE
import com.anydownload.core.extract.odnoklassniki.OdnoklassnikiIE
import com.anydownload.core.extract.onet.OnetChannelIE
import com.anydownload.core.extract.onet.OnetIE
import com.anydownload.core.extract.onet.OnetMVPIE
import com.anydownload.core.extract.onet.OnetPlIE
import com.anydownload.core.extract.openrec.OpenRecIE
import com.anydownload.core.extract.openrec.OpenRecMovieIE
import com.anydownload.core.extract.openrec.OpenRecPlaylistIE
import com.anydownload.core.extract.orf.ORFFM4StoryIE
import com.anydownload.core.extract.orf.ORFIPTVIE
import com.anydownload.core.extract.orf.ORFONIE
import com.anydownload.core.extract.orf.ORFPodcastIE
import com.anydownload.core.extract.orf.ORFRadioIE
import com.anydownload.core.extract.panopto.PanoptoIE
import com.anydownload.core.extract.panopto.PanoptoListIE
import com.anydownload.core.extract.panopto.PanoptoPlaylistIE
import com.anydownload.core.extract.patreon.PatreonIE
import com.anydownload.core.extract.pbs.PBSIE
import com.anydownload.core.extract.pinterest.PinterestCollectionIE
import com.anydownload.core.extract.pinterest.PinterestIE
import com.anydownload.core.extract.pluralsight.PluralsightCourseIE
import com.anydownload.core.extract.playsuisse.PlaySuisseIE
import com.anydownload.core.extract.pluralsight.PluralsightIE
import com.anydownload.core.extract.polskieradio.PolskieRadioAuditionIE
import com.anydownload.core.extract.polskieradio.PolskieRadioCategoryIE
import com.anydownload.core.extract.polskieradio.PolskieRadioIE
import com.anydownload.core.extract.polskieradio.PolskieRadioLegacyIE
import com.anydownload.core.extract.polskieradio.PolskieRadioPlayerIE
import com.anydownload.core.extract.polskieradio.PolskieRadioPodcastIE
import com.anydownload.core.extract.polskieradio.PolskieRadioPodcastListIE
import com.anydownload.core.extract.pornhub.PornHubIE
import com.anydownload.core.extract.pornhub.PornHubPagedVideoListIE
import com.anydownload.core.extract.pornhub.PornHubPlaylistIE
import com.anydownload.core.extract.pornhub.PornHubUserIE
import com.anydownload.core.extract.pornhub.PornHubUserVideosUploadIE
import com.anydownload.core.extract.pbs.PBSKidsIE
import com.anydownload.core.extract.soundcloud.SoundcloudEmbedIE
import com.anydownload.core.extract.soundcloud.SoundcloudIE
import com.anydownload.core.extract.sonyliv.SonyLivIE
import com.anydownload.core.extract.sonyliv.SonyLivSeriesIE
import com.anydownload.core.extract.srgssr.SRGSSRIE
import com.anydownload.core.extract.srgssr.SRGSSRPlayIE
import com.anydownload.core.extract.stacommu.StacommuLiveIE
import com.anydownload.core.extract.stacommu.StacommuVODIE
import com.anydownload.core.extract.stacommu.TheaterComplexTownPPVIE
import com.anydownload.core.extract.stacommu.TheaterComplexTownVODIE
import com.anydownload.core.extract.roosterteeth.RoosterTeethIE
import com.anydownload.core.extract.roosterteeth.RoosterTeethSeriesIE
import com.anydownload.core.extract.rozhlas.MujRozhlasIE
import com.anydownload.core.extract.rozhlas.RozhlasIE
import com.anydownload.core.extract.rts.RTSIE
import com.anydownload.core.extract.rozhlas.RozhlasVltavaIE
import com.anydownload.core.extract.rtp.RTPIE
import com.anydownload.core.extract.rtlnl.RtlNlIE
import com.anydownload.core.extract.rtlnl.RTLLuArticleIE
import com.anydownload.core.extract.rtlnl.RTLLuLiveIE
import com.anydownload.core.extract.rtlnl.RTLLuRadioIE
import com.anydownload.core.extract.rtlnl.RTLLuTeleVODIE
import com.anydownload.core.extract.rtve.RTVEALaCartaIE
import com.anydownload.core.extract.rtvcplay.RTVCKalturaIE
import com.anydownload.core.extract.rtvcplay.RTVCPlayEmbedIE
import com.anydownload.core.extract.rtvcplay.RTVCPlayIE
import com.anydownload.core.extract.rtve.RTVEAudioIE
import com.anydownload.core.extract.rtve.RTVELiveIE
import com.anydownload.core.extract.rtve.RTVEProgramIE
import com.anydownload.core.extract.rtve.RTVETelevisionIE
import com.anydownload.core.extract.rumble.RumbleChannelIE
import com.anydownload.core.extract.rumble.RumbleEmbedIE
import com.anydownload.core.extract.rumble.RumbleIE
import com.anydownload.core.extract.rutube.RutubeChannelIE
import com.anydownload.core.extract.rutube.RutubeEmbedIE
import com.anydownload.core.extract.rutube.RutubeIE
import com.anydownload.core.extract.ruutu.RuutuIE
import com.anydownload.core.extract.rutube.RutubeMovieIE
import com.anydownload.core.extract.rutube.RutubePersonIE
import com.anydownload.core.extract.safari.SafariApiIE
import com.anydownload.core.extract.safari.SafariCourseIE
import com.anydownload.core.extract.safari.SafariIE
import com.anydownload.core.extract.rutube.RutubePlaylistIE
import com.anydownload.core.extract.rutube.RutubeTagsIE
import com.anydownload.core.extract.skyit.CieloTVItIE
import com.anydownload.core.extract.skyit.SkyItArteIE
import com.anydownload.core.extract.skyit.SkyItIE
import com.anydownload.core.extract.skyit.SkyItPlayerIE
import com.anydownload.core.extract.skyit.SkyItVideoIE
import com.anydownload.core.extract.skyit.SkyItVideoLiveIE
import com.anydownload.core.extract.skyit.TV8ItIE
import com.anydownload.core.extract.skyit.TV8ItLiveIE
import com.anydownload.core.extract.skyit.TV8ItPlaylistIE
import com.anydownload.core.extract.senategov.SenateGovIE
import com.anydownload.core.extract.senategov.SenateISVPIE
import com.anydownload.core.extract.slideslive.SlidesLiveIE
import com.anydownload.core.extract.theplatform.ThePlatformFeedIE
import com.anydownload.core.extract.theplatform.ThePlatformIE
import com.anydownload.core.extract.tiktok.TikTokIE
import com.anydownload.core.extract.tiktok.TikTokVMIE
import com.anydownload.core.extract.taptap.TapTapAppIE
import com.anydownload.core.extract.taptap.TapTapAppIntlIE
import com.anydownload.core.extract.taptap.TapTapMomentIE
import com.anydownload.core.extract.taptap.TapTapPostIntlIE
import com.anydownload.core.extract.tmz.TMZIE
import com.anydownload.core.extract.txxx.PornTopIE
import com.anydownload.core.extract.twitcasting.TwitCastingIE
import com.anydownload.core.extract.twitcasting.TwitCastingLiveIE
import com.anydownload.core.extract.twitcasting.TwitCastingUserIE
import com.anydownload.core.extract.txxx.TxxxIE
import com.anydownload.core.extract.udemy.UdemyCourseIE
import com.anydownload.core.extract.udemy.UdemyIE
import com.anydownload.core.extract.tv2.KatsomoIE
import com.anydownload.core.extract.tv2.MTVUutisetArticleIE
import com.anydownload.core.extract.tv2.TV2ArticleIE
import com.anydownload.core.extract.tv2.TV2IE
import com.anydownload.core.extract.tver.TVerIE
import com.anydownload.core.extract.tver.TVerOlympicIE
import com.anydownload.core.extract.tvp.TVPEmbedIE
import com.anydownload.core.extract.tvplay.TVPlayHomeIE
import com.anydownload.core.extract.tvplay.TVPlayIE
import com.anydownload.core.extract.tvp.TVPIE
import com.anydownload.core.extract.tvp.TVPStreamIE
import com.anydownload.core.extract.tvp.TVPVODSeriesIE
import com.anydownload.core.extract.tvp.TVPVODVideoIE
import com.anydownload.core.extract.svt.SVTPlayIE
import com.anydownload.core.extract.stageplus.StagePlusVODConcertIE
import com.anydownload.core.extract.streaks.StreaksIE
import com.anydownload.core.extract.southpark.SouthParkCoUkIE
import com.anydownload.core.extract.southpark.SouthParkComBrIE
import com.anydownload.core.extract.southpark.SouthParkDeIE
import com.anydownload.core.extract.southpark.SouthParkDkIE
import com.anydownload.core.extract.southpark.SouthParkEsIE
import com.anydownload.core.extract.southpark.SouthParkIE
import com.anydownload.core.extract.southpark.SouthParkLatIE
import com.anydownload.core.extract.smotrim.SmotrimAudioIE
import com.anydownload.core.extract.smotrim.SmotrimIE
import com.anydownload.core.extract.smotrim.SmotrimLiveIE
import com.anydownload.core.extract.sohu.SohuIE
import com.anydownload.core.extract.sohu.SohuVIE
import com.anydownload.core.extract.smotrim.SmotrimPlaylistIE
import com.anydownload.core.extract.svt.SVTPageIE
import com.anydownload.core.extract.svt.SVTSeriesIE
import com.anydownload.core.extract.tenplay.TenPlayIE
import com.anydownload.core.extract.tenplay.TenPlaySeasonIE
import com.anydownload.core.extract.tencent.IflixEpisodeIE
import com.anydownload.core.extract.tencent.IflixSeriesIE
import com.anydownload.core.extract.tencent.VQQSeriesIE
import com.anydownload.core.extract.tencent.VQQVideoIE
import com.anydownload.core.extract.tencent.WeTvEpisodeIE
import com.anydownload.core.extract.tencent.WeTvSeriesIE
import com.anydownload.core.extract.teachable.TeachableCourseIE
import com.anydownload.core.extract.teachable.TeachableIE
import com.anydownload.core.extract.teamcoco.ConanClassicIE
import com.anydownload.core.extract.teamcoco.TeamcocoIE
import com.anydownload.core.extract.ted.TedEmbedIE
import com.anydownload.core.extract.ted.TedPlaylistIE
import com.anydownload.core.extract.ted.TedSeriesIE
import com.anydownload.core.extract.ted.TedTalkIE
import com.anydownload.core.extract.tnaflix.EMPFlixIE
import com.anydownload.core.extract.tnaflix.MovieFapIE
import com.anydownload.core.extract.tnaflix.TNAFlixIE
import com.anydownload.core.extract.tnaflix.TNAFlixNetworkEmbedIE
import com.anydownload.core.extract.tubetugraz.TubeTuGrazIE
import com.anydownload.core.extract.tubetugraz.TubeTuGrazSeriesIE
import com.anydownload.core.extract.tumblr.TumblrIE
import com.anydownload.core.extract.tunein.TuneInEmbedIE
import com.anydownload.core.extract.tunein.TuneInPodcastEpisodeIE
import com.anydownload.core.extract.tunein.TuneInPodcastIE
import com.anydownload.core.extract.tunein.TuneInShortenerIE
import com.anydownload.core.extract.tunein.TuneInStationIE
import com.anydownload.core.extract.twitch.TwitchStreamIE
import com.anydownload.core.extract.twitch.TwitchVodIE
import com.anydownload.core.extract.twitter.TwitterAmplifyIE
import com.anydownload.core.extract.twitter.TwitterBroadcastIE
import com.anydownload.core.extract.twitter.TwitterCardIE
import com.anydownload.core.extract.twitter.TwitterIE
import com.anydownload.core.extract.twitter.TwitterShortenerIE
import com.anydownload.core.extract.twitter.TwitterSpacesIE
import com.anydownload.core.extract.vrt.DagelijkseKostIE
import com.anydownload.core.extract.vrt.Radio1BeIE
import com.anydownload.core.extract.vrt.VRTIE
import com.anydownload.core.extract.vrt.VrtNUIE
import com.anydownload.core.extract.viewlift.ViewLiftEmbedIE
import com.anydownload.core.extract.viewlift.ViewLiftIE
import com.anydownload.core.extract.unsupported.KnownDRMIE
import com.anydownload.core.extract.unsupported.KnownLiabilityIE
import com.anydownload.core.extract.unsupported.KnownPiracyIE
import com.anydownload.core.extract.ustream.UstreamChannelIE
import com.anydownload.core.extract.ustream.UstreamIE
import com.anydownload.core.extract.viu.ViuIE
import com.anydownload.core.extract.viu.ViuOTTIE
import com.anydownload.core.extract.viu.ViuOTTIndonesiaIE
import com.anydownload.core.extract.viu.ViuPlaylistIE
import com.anydownload.core.extract.wdr.WDRElefantIE
import com.anydownload.core.extract.wdr.WDRIE
import com.anydownload.core.extract.wdr.WDRPageIE
import com.anydownload.core.extract.weibo.WeiboIE
import com.anydownload.core.extract.weibo.WeiboUserIE
import com.anydownload.core.extract.weibo.WeiboVideoIE
import com.anydownload.core.extract.wistia.WistiaChannelIE
import com.anydownload.core.extract.wistia.WistiaIE
import com.anydownload.core.extract.wistia.WistiaPlaylistIE
import com.anydownload.core.extract.wykop.WykopDigCommentIE
import com.anydownload.core.extract.wykop.WykopDigIE
import com.anydownload.core.extract.wykop.WykopPostCommentIE
import com.anydownload.core.extract.wykop.WykopPostIE
import com.anydownload.core.extract.vk.VKIE
import com.anydownload.core.extract.weverse.WeverseIE
import com.anydownload.core.extract.weverse.WeverseLiveIE
import com.anydownload.core.extract.weverse.WeverseLiveTabIE
import com.anydownload.core.extract.weverse.WeverseMediaIE
import com.anydownload.core.extract.weverse.WeverseMediaTabIE
import com.anydownload.core.extract.weverse.WeverseMomentIE
import com.anydownload.core.extract.vevo.VevoIE
import com.anydownload.core.extract.vevo.VevoPlaylistIE
import com.anydownload.core.extract.vgtv.BTArticleIE
import com.anydownload.core.extract.vgtv.BTVestlendingenIE
import com.anydownload.core.extract.vgtv.VGTVIE
import com.anydownload.core.extract.vice.ViceArticleIE
import com.anydownload.core.extract.vice.ViceIE
import com.anydownload.core.extract.vice.ViceShowIE
import com.anydownload.core.extract.vidio.VidioIE
import com.anydownload.core.extract.vidio.VidioLiveIE
import com.anydownload.core.extract.vidio.VidioPremierIE
import com.anydownload.core.extract.vidyard.VidyardIE
import com.anydownload.core.extract.videocampus_sachsen.VideocampusSachsenIE
import com.anydownload.core.extract.videocampus_sachsen.ViMPPlaylistIE
import com.anydownload.core.extract.vimeo.VimeoIE
import com.anydownload.core.extract.xhamster.XHamsterEmbedIE
import com.anydownload.core.extract.wrestleuniverse.WrestleUniversePPVIE
import com.anydownload.core.extract.wrestleuniverse.WrestleUniverseVODIE
import com.anydownload.core.extract.xhamster.XHamsterIE
import com.anydownload.core.extract.xhamster.XHamsterUserIE
import com.anydownload.core.extract.ximalaya.XimalayaAlbumIE
import com.anydownload.core.extract.ximalaya.XimalayaIE
import com.anydownload.core.extract.youtube.YoutubeIE
import com.anydownload.core.extract.zan.ZanIE
import com.anydownload.core.extract.zattoo.ZattooIE
import com.anydownload.core.extract.zattoo.ZattooLiveIE
import com.anydownload.core.extract.zattoo.ZattooMoviesIE
import com.anydownload.core.extract.zattoo.ZattooRecordingsIE
import com.anydownload.core.extract.zattoo.NetPlusTVIE
import com.anydownload.core.extract.zattoo.NetPlusTVLiveIE
import com.anydownload.core.extract.zattoo.NetPlusTVRecordingsIE
import com.anydownload.core.extract.zattoo.MNetTVIE
import com.anydownload.core.extract.zattoo.MNetTVLiveIE
import com.anydownload.core.extract.zattoo.MNetTVRecordingsIE
import com.anydownload.core.extract.zattoo.WalyTVIE
import com.anydownload.core.extract.zattoo.WalyTVLiveIE
import com.anydownload.core.extract.zattoo.WalyTVRecordingsIE
import com.anydownload.core.extract.zattoo.BBVTVIE
import com.anydownload.core.extract.zattoo.BBVTVLiveIE
import com.anydownload.core.extract.zattoo.BBVTVRecordingsIE
import com.anydownload.core.extract.zattoo.VTXTVIE
import com.anydownload.core.extract.zattoo.VTXTVLiveIE
import com.anydownload.core.extract.zattoo.VTXTVRecordingsIE
import com.anydownload.core.extract.zattoo.GlattvisionTVIE
import com.anydownload.core.extract.zattoo.GlattvisionTVLiveIE
import com.anydownload.core.extract.zattoo.GlattvisionTVRecordingsIE
import com.anydownload.core.extract.zattoo.SAKTVIE
import com.anydownload.core.extract.zattoo.SAKTVLiveIE
import com.anydownload.core.extract.zattoo.SAKTVRecordingsIE
import com.anydownload.core.extract.zattoo.EWETVIE
import com.anydownload.core.extract.zattoo.EWETVLiveIE
import com.anydownload.core.extract.zattoo.EWETVRecordingsIE
import com.anydownload.core.extract.zattoo.QuantumTVIE
import com.anydownload.core.extract.zattoo.QuantumTVLiveIE
import com.anydownload.core.extract.zattoo.QuantumTVRecordingsIE
import com.anydownload.core.extract.zattoo.OsnatelTVIE
import com.anydownload.core.extract.zattoo.OsnatelTVLiveIE
import com.anydownload.core.extract.zattoo.OsnatelTVRecordingsIE
import com.anydownload.core.extract.zattoo.EinsUndEinsTVIE
import com.anydownload.core.extract.zattoo.EinsUndEinsTVLiveIE
import com.anydownload.core.extract.zattoo.EinsUndEinsTVRecordingsIE
import com.anydownload.core.extract.zattoo.SaltTVIE
import com.anydownload.core.extract.zattoo.SaltTVLiveIE
import com.anydownload.core.extract.zattoo.SaltTVRecordingsIE
import com.anydownload.core.extract.zdf.ZDFChannelIE
import com.anydownload.core.extract.zdf.ZDFIE
import com.anydownload.core.extract.yahoo.YahooIE
import com.anydownload.core.extract.yahoo.YahooJapanNewsIE
import com.anydownload.core.extract.yandexmusic.YandexMusicAlbumIE
import com.anydownload.core.extract.yandexmusic.YandexMusicArtistAlbumsIE
import com.anydownload.core.extract.yandexmusic.YandexMusicArtistTracksIE
import com.anydownload.core.extract.yandexmusic.YandexMusicPlaylistIE
import com.anydownload.core.extract.yandexmusic.YandexMusicTrackIE
import com.anydownload.core.extract.yandexvideo.YandexVideoIE
import com.anydownload.core.extract.youku.YoukuIE
import com.anydownload.core.extract.youku.YoukuShowIE
import com.anydownload.core.extract.yandexvideo.YandexVideoPreviewIE
import com.anydownload.core.extract.yandexvideo.ZenYandexChannelIE
import com.anydownload.core.extract.yandexvideo.ZenYandexIE
import com.anydownload.core.extract.youporn.YouPornCategoryIE
import com.anydownload.core.extract.youporn.YouPornChannelIE
import com.anydownload.core.extract.youporn.YouPornCollectionIE
import com.anydownload.core.extract.youporn.YouPornIE
import com.anydownload.core.extract.youporn.YouPornStarIE
import com.anydownload.core.extract.youporn.YouPornTagIE
import com.anydownload.core.extract.youporn.YouPornVideosIE
import com.anydownload.core.extract.zingmp3.ZingMp3AlbumIE
import com.anydownload.core.extract.zingmp3.ZingMp3ChartHomeIE
import com.anydownload.core.extract.zingmp3.ZingMp3ChartMusicVideoIE
import com.anydownload.core.extract.zingmp3.ZingMp3HubIE
import com.anydownload.core.extract.zingmp3.ZingMp3IE
import com.anydownload.core.extract.zingmp3.ZingMp3LiveRadioIE
import com.anydownload.core.extract.zingmp3.ZingMp3PodcastEpisodeIE
import com.anydownload.core.extract.zingmp3.ZingMp3PodcastIE
import com.anydownload.core.extract.zingmp3.ZingMp3UserIE
import com.anydownload.core.extract.zingmp3.ZingMp3WeekChartIE
import com.anydownload.core.extract.youtube.YoutubeTabIE
import com.anydownload.core.jsc.JsRuntime
import com.anydownload.core.platform.HttpTransfer
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
    fun tapTapMoment(http: ExtractorHttp): TapTapMomentIE = TapTapMomentIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tapTapApp(http: ExtractorHttp): TapTapAppIE = TapTapAppIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tapTapAppIntl(http: ExtractorHttp): TapTapAppIntlIE = TapTapAppIntlIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tapTapPostIntl(http: ExtractorHttp): TapTapPostIntlIE = TapTapPostIntlIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tmz(http: ExtractorHttp): TMZIE = TMZIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun soundcloud(http: ExtractorHttp): SoundcloudIE = SoundcloudIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun soundcloudEmbed(http: ExtractorHttp): SoundcloudEmbedIE = SoundcloudEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun sonyLiv(http: ExtractorHttp): SonyLivIE = SonyLivIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun sonyLivSeries(http: ExtractorHttp): SonyLivSeriesIE = SonyLivSeriesIE(http)

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
    fun fc2(http: ExtractorHttp): FC2IE = FC2IE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun fc2Embed(http: ExtractorHttp): FC2EmbedIE = FC2EmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun fc2Live(http: ExtractorHttp): FC2LiveIE = FC2LiveIE(http)

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
    fun iwara(http: ExtractorHttp): IwaraIE = IwaraIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iwaraUser(http: ExtractorHttp): IwaraUserIE = IwaraUserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iwaraPlaylist(http: ExtractorHttp): IwaraPlaylistIE = IwaraPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun itv(http: ExtractorHttp): ITVIE = ITVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun itvBtcc(http: ExtractorHttp): ITVBTCCIE = ITVBTCCIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun iprima(http: ExtractorHttp): IPrimaIE = IPrimaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iprimaCnn(http: ExtractorHttp): IPrimaCNNIE = IPrimaCNNIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ivi(http: ExtractorHttp): IviIE = IviIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iviCompilation(http: ExtractorHttp): IviCompilationIE = IviCompilationIE(http)

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
    fun pinterest(http: ExtractorHttp): PinterestIE = PinterestIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pinterestCollection(http: ExtractorHttp): PinterestCollectionIE = PinterestCollectionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun archiveOrg(http: ExtractorHttp): ArchiveOrgIE = ArchiveOrgIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun dplay(http: ExtractorHttp): DPlayIE = DPlayIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun douyuTV(http: ExtractorHttp): DouyuTVIE = DouyuTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun douyuShow(http: ExtractorHttp): DouyuShowIE = DouyuShowIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun dropout(http: ExtractorHttp): DropoutIE = DropoutIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun dropoutSeason(http: ExtractorHttp): DropoutSeasonIE = DropoutSeasonIE(http)

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
    fun brightcoveLegacy(http: ExtractorHttp): BrightcoveLegacyIE = BrightcoveLegacyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brightcoveNew(http: ExtractorHttp): BrightcoveNewIE = BrightcoveNewIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pbs(http: ExtractorHttp): PBSIE = PBSIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pbsKids(http: ExtractorHttp): PBSKidsIE = PBSKidsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkVod(http: ExtractorHttp): NhkVodIE = NhkVodIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkVodProgram(http: ExtractorHttp): NhkVodProgramIE = NhkVodProgramIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkForSchoolBangumi(http: ExtractorHttp): NhkForSchoolBangumiIE = NhkForSchoolBangumiIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkForSchoolSubject(http: ExtractorHttp): NhkForSchoolSubjectIE = NhkForSchoolSubjectIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkForSchoolProgramList(http: ExtractorHttp): NhkForSchoolProgramListIE = NhkForSchoolProgramListIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkRadiru(http: ExtractorHttp): NhkRadiruIE = NhkRadiruIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkRadioNewsPage(http: ExtractorHttp): NhkRadioNewsPageIE = NhkRadioNewsPageIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nhkRadiruLive(http: ExtractorHttp): NhkRadiruLiveIE = NhkRadiruLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zdf(http: ExtractorHttp): ZDFIE = ZDFIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zdfChannel(http: ExtractorHttp): ZDFChannelIE = ZDFChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlay(http: ExtractorHttp): RaiPlayIE = RaiPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlayLive(http: ExtractorHttp): RaiPlayLiveIE = RaiPlayLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlayPlaylist(http: ExtractorHttp): RaiPlayPlaylistIE = RaiPlayPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlaySound(http: ExtractorHttp): RaiPlaySoundIE = RaiPlaySoundIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlaySoundLive(http: ExtractorHttp): RaiPlaySoundLiveIE = RaiPlaySoundLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiPlaySoundPlaylist(http: ExtractorHttp): RaiPlaySoundPlaylistIE = RaiPlaySoundPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rai(http: ExtractorHttp): RaiIE = RaiIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiNews(http: ExtractorHttp): RaiNewsIE = RaiNewsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiCultura(http: ExtractorHttp): RaiCulturaIE = RaiCulturaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun raiSudtirol(http: ExtractorHttp): RaiSudtirolIE = RaiSudtirolIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrk(http: ExtractorHttp): NRKIE = NRKIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTv(http: ExtractorHttp): NRKTVIE = NRKTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTvEpisode(http: ExtractorHttp): NRKTVEpisodeIE = NRKTVEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTvSeason(http: ExtractorHttp): NRKTVSeasonIE = NRKTVSeasonIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTvSeries(http: ExtractorHttp): NRKTVSeriesIE = NRKTVSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTvDirekte(http: ExtractorHttp): NRKTVDirekteIE = NRKTVDirekteIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkRadioPodkast(http: ExtractorHttp): NRKRadioPodkastIE = NRKRadioPodkastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkPlaylist(http: ExtractorHttp): NRKPlaylistIE = NRKPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkTvEpisodes(http: ExtractorHttp): NRKTVEpisodesIE = NRKTVEpisodesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nrkSkole(http: ExtractorHttp): NRKSkoleIE = NRKSkoleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverse(http: ExtractorHttp): WeverseIE = WeverseIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverseMedia(http: ExtractorHttp): WeverseMediaIE = WeverseMediaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverseMoment(http: ExtractorHttp): WeverseMomentIE = WeverseMomentIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverseLiveTab(http: ExtractorHttp): WeverseLiveTabIE = WeverseLiveTabIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverseMediaTab(http: ExtractorHttp): WeverseMediaTabIE = WeverseMediaTabIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weverseLive(http: ExtractorHttp): WeverseLiveIE = WeverseLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornHub(http: ExtractorHttp): PornHubIE = PornHubIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornHubUser(http: ExtractorHttp): PornHubUserIE = PornHubUserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornHubPagedVideoList(http: ExtractorHttp): PornHubPagedVideoListIE = PornHubPagedVideoListIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornHubUserVideosUpload(http: ExtractorHttp): PornHubUserVideosUploadIE =
        PornHubUserVideosUploadIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornHubPlaylist(http: ExtractorHttp): PornHubPlaylistIE = PornHubPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ardBetaMediathek(http: ExtractorHttp): ARDBetaMediathekIE = ARDBetaMediathekIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ardMediathekCollection(http: ExtractorHttp): ARDMediathekCollectionIE = ARDMediathekCollectionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ardAudiothek(http: ExtractorHttp): ARDAudiothekIE = ARDAudiothekIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ardAudiothekPlaylist(http: ExtractorHttp): ARDAudiothekPlaylistIE = ARDAudiothekPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zattoo(http: ExtractorHttp): ZattooIE = ZattooIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun zan(http: ExtractorHttp): ZanIE = ZanIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun zattooLive(http: ExtractorHttp): ZattooLiveIE = ZattooLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun zattooMovies(http: ExtractorHttp): ZattooMoviesIE = ZattooMoviesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun zattooRecordings(http: ExtractorHttp): ZattooRecordingsIE = ZattooRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun netPlusTv(http: ExtractorHttp): NetPlusTVIE = NetPlusTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun netPlusTvLive(http: ExtractorHttp): NetPlusTVLiveIE = NetPlusTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun netPlusTvRecordings(http: ExtractorHttp): NetPlusTVRecordingsIE = NetPlusTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mNetTv(http: ExtractorHttp): MNetTVIE = MNetTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mNetTvLive(http: ExtractorHttp): MNetTVLiveIE = MNetTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mNetTvRecordings(http: ExtractorHttp): MNetTVRecordingsIE = MNetTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun walyTv(http: ExtractorHttp): WalyTVIE = WalyTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun walyTvLive(http: ExtractorHttp): WalyTVLiveIE = WalyTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun walyTvRecordings(http: ExtractorHttp): WalyTVRecordingsIE = WalyTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bBVTv(http: ExtractorHttp): BBVTVIE = BBVTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bBVTvLive(http: ExtractorHttp): BBVTVLiveIE = BBVTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bBVTvRecordings(http: ExtractorHttp): BBVTVRecordingsIE = BBVTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vTXTv(http: ExtractorHttp): VTXTVIE = VTXTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vTXTvLive(http: ExtractorHttp): VTXTVLiveIE = VTXTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vTXTvRecordings(http: ExtractorHttp): VTXTVRecordingsIE = VTXTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun glattvisionTv(http: ExtractorHttp): GlattvisionTVIE = GlattvisionTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun glattvisionTvLive(http: ExtractorHttp): GlattvisionTVLiveIE = GlattvisionTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun glattvisionTvRecordings(http: ExtractorHttp): GlattvisionTVRecordingsIE = GlattvisionTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun sAKTv(http: ExtractorHttp): SAKTVIE = SAKTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun sAKTvLive(http: ExtractorHttp): SAKTVLiveIE = SAKTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun sAKTvRecordings(http: ExtractorHttp): SAKTVRecordingsIE = SAKTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun eWETv(http: ExtractorHttp): EWETVIE = EWETVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun eWETvLive(http: ExtractorHttp): EWETVLiveIE = EWETVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun eWETvRecordings(http: ExtractorHttp): EWETVRecordingsIE = EWETVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun quantumTv(http: ExtractorHttp): QuantumTVIE = QuantumTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun quantumTvLive(http: ExtractorHttp): QuantumTVLiveIE = QuantumTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun quantumTvRecordings(http: ExtractorHttp): QuantumTVRecordingsIE = QuantumTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun osnatelTv(http: ExtractorHttp): OsnatelTVIE = OsnatelTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun osnatelTvLive(http: ExtractorHttp): OsnatelTVLiveIE = OsnatelTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun osnatelTvRecordings(http: ExtractorHttp): OsnatelTVRecordingsIE = OsnatelTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun einsUndEinsTv(http: ExtractorHttp): EinsUndEinsTVIE = EinsUndEinsTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun einsUndEinsTvLive(http: ExtractorHttp): EinsUndEinsTVLiveIE = EinsUndEinsTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun einsUndEinsTvRecordings(http: ExtractorHttp): EinsUndEinsTVRecordingsIE = EinsUndEinsTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun saltTv(http: ExtractorHttp): SaltTVIE = SaltTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun saltTvLive(http: ExtractorHttp): SaltTVLiveIE = SaltTVLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun saltTvRecordings(http: ExtractorHttp): SaltTVRecordingsIE = SaltTVRecordingsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun openRec(http: ExtractorHttp): OpenRecIE = OpenRecIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun openRecCapture(http: ExtractorHttp): OpenRecCaptureIE = OpenRecCaptureIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun openRecMovie(http: ExtractorHttp): OpenRecMovieIE = OpenRecMovieIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun openRecPlaylist(http: ExtractorHttp): OpenRecPlaylistIE = OpenRecPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun openRecChannel(http: ExtractorHttp): OpenRecChannelIE = OpenRecChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun openRecChannelSearch(http: ExtractorHttp): OpenRecChannelSearchIE = OpenRecChannelSearchIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun xHamster(http: ExtractorHttp): XHamsterIE = XHamsterIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun xHamsterEmbed(http: ExtractorHttp): XHamsterEmbedIE = XHamsterEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun xHamsterUser(http: ExtractorHttp): XHamsterUserIE = XHamsterUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ximalaya(http: ExtractorHttp): XimalayaIE = XimalayaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ximalayaAlbum(http: ExtractorHttp): XimalayaAlbumIE = XimalayaAlbumIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusic(http: ExtractorHttp): NetEaseMusicIE = NetEaseMusicIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicAlbum(http: ExtractorHttp): NetEaseMusicAlbumIE = NetEaseMusicAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicSinger(http: ExtractorHttp): NetEaseMusicSingerIE = NetEaseMusicSingerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicList(http: ExtractorHttp): NetEaseMusicListIE = NetEaseMusicListIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicMv(http: ExtractorHttp): NetEaseMusicMvIE = NetEaseMusicMvIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicProgram(http: ExtractorHttp): NetEaseMusicProgramIE = NetEaseMusicProgramIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun netEaseMusicDjRadio(http: ExtractorHttp): NetEaseMusicDjRadioIE = NetEaseMusicDjRadioIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun panopto(http: ExtractorHttp): PanoptoIE = PanoptoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun panoptoPlaylist(http: ExtractorHttp): PanoptoPlaylistIE = PanoptoPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun panoptoList(http: ExtractorHttp): PanoptoListIE = PanoptoListIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tvp(http: ExtractorHttp): TVPIE = TVPIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tvpStream(http: ExtractorHttp): TVPStreamIE = TVPStreamIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tvpEmbed(http: ExtractorHttp): TVPEmbedIE = TVPEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tvpVodVideo(http: ExtractorHttp): TVPVODVideoIE = TVPVODVideoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tvPlay(http: ExtractorHttp): TVPlayIE = TVPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tvPlayHome(http: ExtractorHttp): TVPlayHomeIE = TVPlayHomeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tvpVodSeries(http: ExtractorHttp): TVPVODSeriesIE = TVPVODSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vrt(http: ExtractorHttp): VRTIE = VRTIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vrtNu(http: ExtractorHttp): VrtNUIE = VrtNUIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun dagelijkseKost(http: ExtractorHttp): DagelijkseKostIE = DagelijkseKostIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radio1Be(http: ExtractorHttp): Radio1BeIE = Radio1BeIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun kaltura(http: ExtractorHttp): KalturaIE = KalturaIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun kuwo(http: ExtractorHttp): KuwoIE = KuwoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kuwoAlbum(http: ExtractorHttp): KuwoAlbumIE = KuwoAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kuwoChart(http: ExtractorHttp): KuwoChartIE = KuwoChartIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kuwoSinger(http: ExtractorHttp): KuwoSingerIE = KuwoSingerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kuwoCategory(http: ExtractorHttp): KuwoCategoryIE = KuwoCategoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kuwoMv(http: ExtractorHttp): KuwoMvIE = KuwoMvIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun kick(http: ExtractorHttp): KickIE = KickIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kickVod(http: ExtractorHttp): KickVODIE = KickVODIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun kickClip(http: ExtractorHttp): KickClipIE = KickClipIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun gameJolt(http: ExtractorHttp): GameJoltIE = GameJoltIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun glomex(http: ExtractorHttp): GlomexIE = GlomexIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun glomexEmbed(http: ExtractorHttp): GlomexEmbedIE = GlomexEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun gameJoltUser(http: ExtractorHttp): GameJoltUserIE = GameJoltUserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun gameJoltGame(http: ExtractorHttp): GameJoltGameIE = GameJoltGameIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun gameJoltGameSoundtrack(http: ExtractorHttp): GameJoltGameSoundtrackIE = GameJoltGameSoundtrackIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun gameJoltCommunity(http: ExtractorHttp): GameJoltCommunityIE = GameJoltCommunityIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun gameJoltSearch(http: ExtractorHttp): GameJoltSearchIE = GameJoltSearchIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tumblr(http: ExtractorHttp): TumblrIE = TumblrIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tubeTuGraz(http: ExtractorHttp): TubeTuGrazIE = TubeTuGrazIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tubeTuGrazSeries(http: ExtractorHttp): TubeTuGrazSeriesIE = TubeTuGrazSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun iqiyi(http: ExtractorHttp): IqiyiIE = IqiyiIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iq(http: ExtractorHttp): IqIE = IqIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iqAlbum(http: ExtractorHttp): IqAlbumIE = IqAlbumIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun idagioTrack(http: ExtractorHttp): IdagioTrackIE = IdagioTrackIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun idagioRecording(http: ExtractorHttp): IdagioRecordingIE = IdagioRecordingIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun idagioAlbum(http: ExtractorHttp): IdagioAlbumIE = IdagioAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun idagioPlaylist(http: ExtractorHttp): IdagioPlaylistIE = IdagioPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun idagioPersonalPlaylist(http: ExtractorHttp): IdagioPersonalPlaylistIE = IdagioPersonalPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun anvato(http: ExtractorHttp): AnvatoIE = AnvatoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioLegacy(http: ExtractorHttp): PolskieRadioLegacyIE = PolskieRadioLegacyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadio(http: ExtractorHttp): PolskieRadioIE = PolskieRadioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioAudition(http: ExtractorHttp): PolskieRadioAuditionIE = PolskieRadioAuditionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioCategory(http: ExtractorHttp): PolskieRadioCategoryIE = PolskieRadioCategoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioPlayer(http: ExtractorHttp): PolskieRadioPlayerIE = PolskieRadioPlayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioPodcastList(http: ExtractorHttp): PolskieRadioPodcastListIE = PolskieRadioPodcastListIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun polskieRadioPodcast(http: ExtractorHttp): PolskieRadioPodcastIE = PolskieRadioPodcastIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nebula(http: ExtractorHttp): NebulaIE = NebulaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nebulaClass(http: ExtractorHttp): NebulaClassIE = NebulaClassIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nebulaSubscriptions(http: ExtractorHttp): NebulaSubscriptionsIE = NebulaSubscriptionsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nebulaChannel(http: ExtractorHttp): NebulaChannelIE = NebulaChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nebulaSeason(http: ExtractorHttp): NebulaSeasonIE = NebulaSeasonIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3(http: ExtractorHttp): ZingMp3IE = ZingMp3IE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3Album(http: ExtractorHttp): ZingMp3AlbumIE = ZingMp3AlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3ChartHome(http: ExtractorHttp): ZingMp3ChartHomeIE = ZingMp3ChartHomeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3WeekChart(http: ExtractorHttp): ZingMp3WeekChartIE = ZingMp3WeekChartIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3ChartMusicVideo(http: ExtractorHttp): ZingMp3ChartMusicVideoIE = ZingMp3ChartMusicVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3User(http: ExtractorHttp): ZingMp3UserIE = ZingMp3UserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3Hub(http: ExtractorHttp): ZingMp3HubIE = ZingMp3HubIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3LiveRadio(http: ExtractorHttp): ZingMp3LiveRadioIE = ZingMp3LiveRadioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3PodcastEpisode(http: ExtractorHttp): ZingMp3PodcastEpisodeIE = ZingMp3PodcastEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zingMp3Podcast(http: ExtractorHttp): ZingMp3PodcastIE = ZingMp3PodcastIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun youPorn(http: ExtractorHttp): YouPornIE = YouPornIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornCategory(http: ExtractorHttp): YouPornCategoryIE = YouPornCategoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornChannel(http: ExtractorHttp): YouPornChannelIE = YouPornChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornCollection(http: ExtractorHttp): YouPornCollectionIE = YouPornCollectionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornTag(http: ExtractorHttp): YouPornTagIE = YouPornTagIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornStar(http: ExtractorHttp): YouPornStarIE = YouPornStarIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youPornVideos(http: ExtractorHttp): YouPornVideosIE = YouPornVideosIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun youku(http: ExtractorHttp): YoukuIE = YoukuIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun youkuShow(http: ExtractorHttp): YoukuShowIE = YoukuShowIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun npo(http: ExtractorHttp): NPOIE = NPOIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun npoLive(http: ExtractorHttp): NPOLiveIE = NPOLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun npoRadio(http: ExtractorHttp): NPORadioIE = NPORadioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun npoRadioFragment(http: ExtractorHttp): NPORadioFragmentIE = NPORadioFragmentIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun schoolTv(http: ExtractorHttp): SchoolTVIE = SchoolTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun hetKlokhuis(http: ExtractorHttp): HetKlokhuisIE = HetKlokhuisIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vpro(http: ExtractorHttp): VPROIE = VPROIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wnl(http: ExtractorHttp): WNLIE = WNLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun andereTijden(http: ExtractorHttp): AndereTijdenIE = AndereTijdenIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun orfRadio(http: ExtractorHttp): ORFRadioIE = ORFRadioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun orfPodcast(http: ExtractorHttp): ORFPodcastIE = ORFPodcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun orfIptv(http: ExtractorHttp): ORFIPTVIE = ORFIPTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun orfFm4Story(http: ExtractorHttp): ORFFM4StoryIE = ORFFM4StoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun orfOn(http: ExtractorHttp): ORFONIE = ORFONIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun omnyfm(http: ExtractorHttp): OmnyfmIE = OmnyfmIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun omnyfmPlaylist(http: ExtractorHttp): OmnyfmPlaylistIE = OmnyfmPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun omnyfmShow(http: ExtractorHttp): OmnyfmShowIE = OmnyfmShowIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bandcamp(http: ExtractorHttp): BandcampIE = BandcampIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bandcampAlbum(http: ExtractorHttp): BandcampAlbumIE = BandcampAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bandcampWeekly(http: ExtractorHttp): BandcampWeeklyIE = BandcampWeeklyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bandcampUser(http: ExtractorHttp): BandcampUserIE = BandcampUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun viu(http: ExtractorHttp): ViuIE = ViuIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viuPlaylist(http: ExtractorHttp): ViuPlaylistIE = ViuPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viuOtt(http: ExtractorHttp): ViuOTTIE = ViuOTTIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viuOttIndonesia(http: ExtractorHttp): ViuOTTIndonesiaIE = ViuOTTIndonesiaIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rokfin(http: ExtractorHttp): RokfinIE = RokfinIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rokfinStack(http: ExtractorHttp): RokfinStackIE = RokfinStackIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rokfinChannel(http: ExtractorHttp): RokfinChannelIE = RokfinChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun slidesLive(http: ExtractorHttp): SlidesLiveIE = SlidesLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun senateISVP(http: ExtractorHttp): SenateISVPIE = SenateISVPIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun senateGov(http: ExtractorHttp): SenateGovIE = SenateGovIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nexx(http: ExtractorHttp): NexxIE = NexxIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nexxEmbed(http: ExtractorHttp): NexxEmbedIE = NexxEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun franceTv(http: ExtractorHttp): FranceTVIE = FranceTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun franceTvSite(http: ExtractorHttp): FranceTVSiteIE = FranceTVSiteIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun franceTvInfo(http: ExtractorHttp): FranceTVInfoIE = FranceTVInfoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mlb(http: ExtractorHttp): MLBIE = MLBIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mlbVideo(http: ExtractorHttp): MLBVideoIE = MLBVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mlbTv(http: ExtractorHttp): MLBTVIE = MLBTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mlbArticle(http: ExtractorHttp): MLBArticleIE = MLBArticleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun abemaTv(http: ExtractorHttp): AbemaTVIE = AbemaTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun abemaTvTitle(http: ExtractorHttp): AbemaTVTitleIE = AbemaTVTitleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vqqVideo(http: ExtractorHttp): VQQVideoIE = VQQVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vqqSeries(http: ExtractorHttp): VQQSeriesIE = VQQSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weTvEpisode(http: ExtractorHttp): WeTvEpisodeIE = WeTvEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weTvSeries(http: ExtractorHttp): WeTvSeriesIE = WeTvSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iflixEpisode(http: ExtractorHttp): IflixEpisodeIE = IflixEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun iflixSeries(http: ExtractorHttp): IflixSeriesIE = IflixSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun afreecaTv(http: ExtractorHttp): AfreecaTVIE = AfreecaTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun afreecaTvCatchStory(http: ExtractorHttp): AfreecaTVCatchStoryIE = AfreecaTVCatchStoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun afreecaTvLive(http: ExtractorHttp): AfreecaTVLiveIE = AfreecaTVLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun afreecaTvUser(http: ExtractorHttp): AfreecaTVUserIE = AfreecaTVUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vidyard(http: ExtractorHttp): VidyardIE = VidyardIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun loom(http: ExtractorHttp): LoomIE = LoomIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun loomFolder(http: ExtractorHttp): LoomFolderIE = LoomFolderIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun udemy(http: ExtractorHttp): UdemyIE = UdemyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun udemyCourse(http: ExtractorHttp): UdemyCourseIE = UdemyCourseIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun hotStar(http: ExtractorHttp): HotStarIE = HotStarIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun hotStarPrefix(http: ExtractorHttp): HotStarPrefixIE = HotStarPrefixIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun hotStarSeries(http: ExtractorHttp): HotStarSeriesIE = HotStarSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun qqMusic(http: ExtractorHttp): QQMusicIE = QQMusicIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun qqMusicSinger(http: ExtractorHttp): QQMusicSingerIE = QQMusicSingerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun qqMusicAlbum(http: ExtractorHttp): QQMusicAlbumIE = QQMusicAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun qqMusicToplist(http: ExtractorHttp): QQMusicToplistIE = QQMusicToplistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun qqMusicPlaylist(http: ExtractorHttp): QQMusicPlaylistIE = QQMusicPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun qqMusicVideo(http: ExtractorHttp): QQMusicVideoIE = QQMusicVideoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bandlab(http: ExtractorHttp): BandlabIE = BandlabIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bandlabPlaylist(http: ExtractorHttp): BandlabPlaylistIE = BandlabPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun cbsNews(http: ExtractorHttp): CBSNewsIE = CBSNewsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun cbs(http: ExtractorHttp): CBSIE = CBSIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun paramountPressExpress(http: ExtractorHttp): ParamountPressExpressIE = ParamountPressExpressIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ceskaTelevize(http: ExtractorHttp): CeskaTelevizeIE = CeskaTelevizeIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun condeNast(http: ExtractorHttp): CondeNastIE = CondeNastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsNewsEmbed(http: ExtractorHttp): CBSNewsEmbedIE = CBSNewsEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsLocal(http: ExtractorHttp): CBSLocalIE = CBSLocalIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsLocalArticle(http: ExtractorHttp): CBSLocalArticleIE = CBSLocalArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsLocalLive(http: ExtractorHttp): CBSLocalLiveIE = CBSLocalLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsNewsLive(http: ExtractorHttp): CBSNewsLiveIE = CBSNewsLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cbsNewsLiveVideo(http: ExtractorHttp): CBSNewsLiveVideoIE = CBSNewsLiveVideoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun abc(http: ExtractorHttp): ABCIE = ABCIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun abcIView(http: ExtractorHttp): ABCIViewIE = ABCIViewIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun abcIViewShowSeries(http: ExtractorHttp): ABCIViewShowSeriesIE = ABCIViewShowSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun aeNetworks(http: ExtractorHttp): AENetworksIE = AENetworksIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun aeNetworksCollection(http: ExtractorHttp): AENetworksCollectionIE = AENetworksCollectionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun aeNetworksShow(http: ExtractorHttp): AENetworksShowIE = AENetworksShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun historyTopic(http: ExtractorHttp): HistoryTopicIE = HistoryTopicIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun historyPlayer(http: ExtractorHttp): HistoryPlayerIE = HistoryPlayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun biography(http: ExtractorHttp): BiographyIE = BiographyIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun reddit(http: ExtractorHttp): RedditIE = RedditIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun redGifs(http: ExtractorHttp): RedGifsIE = RedGifsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun redGifsSearch(http: ExtractorHttp): RedGifsSearchIE = RedGifsSearchIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun redGifsUser(http: ExtractorHttp): RedGifsUserIE = RedGifsUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun thePlatform(http: ExtractorHttp): ThePlatformIE = ThePlatformIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun thePlatformFeed(http: ExtractorHttp): ThePlatformFeedIE = ThePlatformFeedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nyTimes(http: ExtractorHttp): NYTimesIE = NYTimesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nyTimesArticle(http: ExtractorHttp): NYTimesArticleIE = NYTimesArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nyTimesCooking(http: ExtractorHttp): NYTimesCookingIE = NYTimesCookingIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nyTimesCookingRecipe(http: ExtractorHttp): NYTimesCookingRecipeIE = NYTimesCookingRecipeIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun radioFrance(http: ExtractorHttp): RadioFranceIE = RadioFranceIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun radiko(http: ExtractorHttp): RadikoIE = RadikoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radikoRadio(http: ExtractorHttp): RadikoRadioIE = RadikoRadioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun franceCulture(http: ExtractorHttp): FranceCultureIE = FranceCultureIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radioFranceLive(http: ExtractorHttp): RadioFranceLiveIE = RadioFranceLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radioFrancePodcast(http: ExtractorHttp): RadioFrancePodcastIE = RadioFrancePodcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radioFranceProfile(http: ExtractorHttp): RadioFranceProfileIE = RadioFranceProfileIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun radioFranceProgramSchedule(http: ExtractorHttp): RadioFranceProgramScheduleIE = RadioFranceProgramScheduleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ndr(http: ExtractorHttp): NDRIE = NDRIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun newgrounds(http: ExtractorHttp): NewgroundsIE = NewgroundsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nekoHacker(http: ExtractorHttp): NekoHackerIE = NekoHackerIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ninaProtocol(http: ExtractorHttp): NinaProtocolIE = NinaProtocolIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun newgroundsPlaylist(http: ExtractorHttp): NewgroundsPlaylistIE = NewgroundsPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun newgroundsUser(http: ExtractorHttp): NewgroundsUserIE = NewgroundsUserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nJoy(http: ExtractorHttp): NJoyIE = NJoyIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ndrEmbedBase(http: ExtractorHttp): NDREmbedBaseIE = NDREmbedBaseIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ndrEmbed(http: ExtractorHttp): NDREmbedIE = NDREmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nJoyEmbed(http: ExtractorHttp): NJoyEmbedIE = NJoyEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun pluralsight(http: ExtractorHttp): PluralsightIE = PluralsightIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pluralsightCourse(http: ExtractorHttp): PluralsightCourseIE = PluralsightCourseIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun playSuisse(http: ExtractorHttp): PlaySuisseIE = PlaySuisseIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun lbry(http: ExtractorHttp): LBRYIE = LBRYIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun lbryChannel(http: ExtractorHttp): LBRYChannelIE = LBRYChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun lbryPlaylist(http: ExtractorHttp): LBRYPlaylistIE = LBRYPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun lifeNews(http: ExtractorHttp): LifeNewsIE = LifeNewsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun lifeEmbed(http: ExtractorHttp): LifeEmbedIE = LifeEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun espn(http: ExtractorHttp): ESPNIE = ESPNIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun espnArticle(http: ExtractorHttp): ESPNArticleIE = ESPNArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun fiveThirtyEight(http: ExtractorHttp): FiveThirtyEightIE = FiveThirtyEightIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun espnCricInfo(http: ExtractorHttp): ESPNCricInfoIE = ESPNCricInfoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun watchEspn(http: ExtractorHttp): WatchESPNIE = WatchESPNIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun niconicoChannelPlus(http: ExtractorHttp): NiconicoChannelPlusIE = NiconicoChannelPlusIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconicoChannelPlusChannelVideos(http: ExtractorHttp): NiconicoChannelPlusChannelVideosIE =
        NiconicoChannelPlusChannelVideosIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun niconicoChannelPlusChannelLives(http: ExtractorHttp): NiconicoChannelPlusChannelLivesIE =
        NiconicoChannelPlusChannelLivesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rutube(http: ExtractorHttp): RutubeIE = RutubeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubeEmbed(http: ExtractorHttp): RutubeEmbedIE = RutubeEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubeTags(http: ExtractorHttp): RutubeTagsIE = RutubeTagsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubeMovie(http: ExtractorHttp): RutubeMovieIE = RutubeMovieIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubePerson(http: ExtractorHttp): RutubePersonIE = RutubePersonIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun safari(http: ExtractorHttp): SafariIE = SafariIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun safariApi(http: ExtractorHttp): SafariApiIE = SafariApiIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun safariCourse(http: ExtractorHttp): SafariCourseIE = SafariCourseIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubePlaylist(http: ExtractorHttp): RutubePlaylistIE = RutubePlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rutubeChannel(http: ExtractorHttp): RutubeChannelIE = RutubeChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ruutu(http: ExtractorHttp): RuutuIE = RuutuIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun yandexVideo(http: ExtractorHttp): YandexVideoIE = YandexVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yandexVideoPreview(http: ExtractorHttp): YandexVideoPreviewIE = YandexVideoPreviewIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zenYandex(http: ExtractorHttp): ZenYandexIE = ZenYandexIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun zenYandexChannel(http: ExtractorHttp): ZenYandexChannelIE = ZenYandexChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mxplayer(http: ExtractorHttp): MxplayerIE = MxplayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mxplayerSeason(http: ExtractorHttp): MxplayerSeasonIE = MxplayerSeasonIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mxplayerShow(http: ExtractorHttp): MxplayerShowIE = MxplayerShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mxplayerRedirect(http: ExtractorHttp): MxplayerRedirectIE = MxplayerRedirectIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mtv(http: ExtractorHttp): MTVIE = MTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun msn(http: ExtractorHttp): MSNIE = MSNIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun goPlay(http: ExtractorHttp): GoPlayIE = GoPlayIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun yandexMusicTrack(http: ExtractorHttp): YandexMusicTrackIE = YandexMusicTrackIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yandexMusicAlbum(http: ExtractorHttp): YandexMusicAlbumIE = YandexMusicAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yandexMusicPlaylist(http: ExtractorHttp): YandexMusicPlaylistIE = YandexMusicPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yandexMusicArtistTracks(http: ExtractorHttp): YandexMusicArtistTracksIE = YandexMusicArtistTracksIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yandexMusicArtistAlbums(http: ExtractorHttp): YandexMusicArtistAlbumsIE = YandexMusicArtistAlbumsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bluesky(http: ExtractorHttp): BlueskyIE = BlueskyIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun boosty(http: ExtractorHttp): BoostyIE = BoostyIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun weibo(http: ExtractorHttp): WeiboIE = WeiboIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weiboVideo(http: ExtractorHttp): WeiboVideoIE = WeiboVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun weiboUser(http: ExtractorHttp): WeiboUserIE = WeiboUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun floatplane(http: ExtractorHttp): FloatplaneIE = FloatplaneIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun floatplaneChannel(http: ExtractorHttp): FloatplaneChannelIE = FloatplaneChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun fourTube(http: ExtractorHttp): FourTubeIE = FourTubeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun fux(http: ExtractorHttp): FuxIE = FuxIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornTube(http: ExtractorHttp): PornTubeIE = PornTubeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornerBros(http: ExtractorHttp): PornerBrosIE = PornerBrosIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun arteTv(http: ExtractorHttp): ArteTVIE = ArteTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun arteTvEmbed(http: ExtractorHttp): ArteTVEmbedIE = ArteTVEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun arteTvPlaylist(http: ExtractorHttp): ArteTVPlaylistIE = ArteTVPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun arteTvCategory(http: ExtractorHttp): ArteTVCategoryIE = ArteTVCategoryIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun amazonMiniTv(http: ExtractorHttp): AmazonMiniTvIE = AmazonMiniTvIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun amazonMiniTvSeason(http: ExtractorHttp): AmazonMiniTvSeasonIE = AmazonMiniTvSeasonIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun amazonMiniTvSeries(http: ExtractorHttp): AmazonMiniTvSeriesIE = AmazonMiniTvSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun audius(http: ExtractorHttp): AudiusIE = AudiusIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun audiusTrack(http: ExtractorHttp): AudiusTrackIE = AudiusTrackIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun audiusPlaylist(http: ExtractorHttp): AudiusPlaylistIE = AudiusPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun audiusProfile(http: ExtractorHttp): AudiusProfileIE = AudiusProfileIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun odnoklassniki(http: ExtractorHttp): OdnoklassnikiIE = OdnoklassnikiIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun onet(http: ExtractorHttp): OnetIE = OnetIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun onetMvp(http: ExtractorHttp): OnetMVPIE = OnetMVPIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun onetChannel(http: ExtractorHttp): OnetChannelIE = OnetChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun onetPl(http: ExtractorHttp): OnetPlIE = OnetPlIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun txxx(http: ExtractorHttp): TxxxIE = TxxxIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun pornTop(http: ExtractorHttp): PornTopIE = PornTopIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun wistia(http: ExtractorHttp): WistiaIE = WistiaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wistiaPlaylist(http: ExtractorHttp): WistiaPlaylistIE = WistiaPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wistiaChannel(http: ExtractorHttp): WistiaChannelIE = WistiaChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun wykopDig(http: ExtractorHttp): WykopDigIE = WykopDigIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wykopDigComment(http: ExtractorHttp): WykopDigCommentIE = WykopDigCommentIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wykopPost(http: ExtractorHttp): WykopPostIE = WykopPostIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wykopPostComment(http: ExtractorHttp): WykopPostCommentIE = WykopPostCommentIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun svtPlay(http: ExtractorHttp): SVTPlayIE = SVTPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun svtSeries(http: ExtractorHttp): SVTSeriesIE = SVTSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun svtPage(http: ExtractorHttp): SVTPageIE = SVTPageIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun stagePlusVodConcert(http: ExtractorHttp): StagePlusVODConcertIE = StagePlusVODConcertIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun cda(http: ExtractorHttp): CDAIE = CDAIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun cspan(http: ExtractorHttp): CSpanIE = CSpanIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cspanCongress(http: ExtractorHttp): CSpanCongressIE = CSpanCongressIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cdaFolder(http: ExtractorHttp): CDAFolderIE = CDAFolderIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rctiPlus(http: ExtractorHttp): RCTIPlusIE = RCTIPlusIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rctiPlusSeries(http: ExtractorHttp): RCTIPlusSeriesIE = RCTIPlusSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rctiPlusTv(http: ExtractorHttp): RCTIPlusTVIE = RCTIPlusTVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mediasite(http: ExtractorHttp): MediasiteIE = MediasiteIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mediasiteCatalog(http: ExtractorHttp): MediasiteCatalogIE = MediasiteCatalogIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mediasiteNamedCatalog(http: ExtractorHttp): MediasiteNamedCatalogIE = MediasiteNamedCatalogIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rtveALaCarta(http: ExtractorHttp): RTVEALaCartaIE = RTVEALaCartaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtveAudio(http: ExtractorHttp): RTVEAudioIE = RTVEAudioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtveLive(http: ExtractorHttp): RTVELiveIE = RTVELiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtveTelevision(http: ExtractorHttp): RTVETelevisionIE = RTVETelevisionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtveProgram(http: ExtractorHttp): RTVEProgramIE = RTVEProgramIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rtvcPlay(http: ExtractorHttp): RTVCPlayIE = RTVCPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtvcPlayEmbed(http: ExtractorHttp): RTVCPlayEmbedIE = RTVCPlayEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtvcKaltura(http: ExtractorHttp): RTVCKalturaIE = RTVCKalturaIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tver(http: ExtractorHttp): TVerIE = TVerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tverOlympic(http: ExtractorHttp): TVerOlympicIE = TVerOlympicIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rumbleEmbed(http: ExtractorHttp): RumbleEmbedIE = RumbleEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rumble(http: ExtractorHttp): RumbleIE = RumbleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rumbleChannel(http: ExtractorHttp): RumbleChannelIE = RumbleChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnSong(http: ExtractorHttp): JioSaavnSongIE = JioSaavnSongIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun shugiinItvLive(http: ExtractorHttp): ShugiinItvLiveIE = ShugiinItvLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun shugiinItvLiveRoom(http: ExtractorHttp): ShugiinItvLiveRoomIE = ShugiinItvLiveRoomIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun shugiinItvVod(http: ExtractorHttp): ShugiinItvVodIE = ShugiinItvVodIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun sangiin(http: ExtractorHttp): SangiinIE = SangiinIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun sangiinInstruction(http: ExtractorHttp): SangiinInstructionIE = SangiinInstructionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnShow(http: ExtractorHttp): JioSaavnShowIE = JioSaavnShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnAlbum(http: ExtractorHttp): JioSaavnAlbumIE = JioSaavnAlbumIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnPlaylist(http: ExtractorHttp): JioSaavnPlaylistIE = JioSaavnPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnShowPlaylist(http: ExtractorHttp): JioSaavnShowPlaylistIE = JioSaavnShowPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun jioSaavnArtist(http: ExtractorHttp): JioSaavnArtistIE = JioSaavnArtistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nbaWatchEmbed(http: ExtractorHttp): NBAWatchEmbedIE = NBAWatchEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbaWatch(http: ExtractorHttp): NBAWatchIE = NBAWatchIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbaWatchCollection(http: ExtractorHttp): NBAWatchCollectionIE = NBAWatchCollectionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbaEmbed(http: ExtractorHttp): NBAEmbedIE = NBAEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nba(http: ExtractorHttp): NBAIE = NBAIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun naver(http: ExtractorHttp): NaverIE = NaverIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun naverLive(http: ExtractorHttp): NaverLiveIE = NaverLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nbaChannel(http: ExtractorHttp): NBAChannelIE = NBAChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rcsEmbeds(http: ExtractorHttp): RCSEmbedsIE = RCSEmbedsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rcs(http: ExtractorHttp): RCSIE = RCSIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rcsVarious(http: ExtractorHttp): RCSVariousIE = RCSVariousIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ign(http: ExtractorHttp): IGNIE = IGNIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ignVideo(http: ExtractorHttp): IGNVideoIE = IGNVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ignArticle(http: ExtractorHttp): IGNArticleIE = IGNArticleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nfl(http: ExtractorHttp): NFLIE = NFLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nflArticle(http: ExtractorHttp): NFLArticleIE = NFLArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nflPlusReplay(http: ExtractorHttp): NFLPlusReplayIE = NFLPlusReplayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nflPlusEpisode(http: ExtractorHttp): NFLPlusEpisodeIE = NFLPlusEpisodeIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun prxStory(http: ExtractorHttp): PRXStoryIE = PRXStoryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun prxSeries(http: ExtractorHttp): PRXSeriesIE = PRXSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun prxAccount(http: ExtractorHttp): PRXAccountIE = PRXAccountIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun imgur(http: ExtractorHttp): ImgurIE = ImgurIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun imgurGallery(http: ExtractorHttp): ImgurGalleryIE = ImgurGalleryIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun imgurAlbum(http: ExtractorHttp): ImgurAlbumIE = ImgurAlbumIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun drtv(http: ExtractorHttp): DRTVIE = DRTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun drtvLive(http: ExtractorHttp): DRTVLiveIE = DRTVLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun drtvSeason(http: ExtractorHttp): DRTVSeasonIE = DRTVSeasonIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun drtvSeries(http: ExtractorHttp): DRTVSeriesIE = DRTVSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun roosterTeeth(http: ExtractorHttp): RoosterTeethIE = RoosterTeethIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun roosterTeethSeries(http: ExtractorHttp): RoosterTeethSeriesIE = RoosterTeethSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun yahoo(http: ExtractorHttp): YahooIE = YahooIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun yahooJapanNews(http: ExtractorHttp): YahooJapanNewsIE = YahooJapanNewsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun go(http: ExtractorHttp): GoIE = GoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun microsoftEmbed(http: ExtractorHttp): MicrosoftEmbedIE = MicrosoftEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun microsoftMedius(http: ExtractorHttp): MicrosoftMediusIE = MicrosoftMediusIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun microsoftLearnPlaylist(http: ExtractorHttp): MicrosoftLearnPlaylistIE = MicrosoftLearnPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun microsoftLearnEpisode(http: ExtractorHttp): MicrosoftLearnEpisodeIE = MicrosoftLearnEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun microsoftLearnSession(http: ExtractorHttp): MicrosoftLearnSessionIE = MicrosoftLearnSessionIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun microsoftBuild(http: ExtractorHttp): MicrosoftBuildIE = MicrosoftBuildIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun viewLiftEmbed(http: ExtractorHttp): ViewLiftEmbedIE = ViewLiftEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viewLift(http: ExtractorHttp): ViewLiftIE = ViewLiftIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun smotrim(http: ExtractorHttp): SmotrimIE = SmotrimIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun smotrimAudio(http: ExtractorHttp): SmotrimAudioIE = SmotrimAudioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun smotrimLive(http: ExtractorHttp): SmotrimLiveIE = SmotrimLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun sohu(http: ExtractorHttp): SohuIE = SohuIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun sohuV(http: ExtractorHttp): SohuVIE = SohuVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun smotrimPlaylist(http: ExtractorHttp): SmotrimPlaylistIE = SmotrimPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun southPark(http: ExtractorHttp): SouthParkIE = SouthParkIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkEs(http: ExtractorHttp): SouthParkEsIE = SouthParkEsIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkDe(http: ExtractorHttp): SouthParkDeIE = SouthParkDeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkLat(http: ExtractorHttp): SouthParkLatIE = SouthParkLatIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkDk(http: ExtractorHttp): SouthParkDkIE = SouthParkDkIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkComBr(http: ExtractorHttp): SouthParkComBrIE = SouthParkComBrIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun southParkCoUk(http: ExtractorHttp): SouthParkCoUkIE = SouthParkCoUkIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun streaks(http: ExtractorHttp): StreaksIE = StreaksIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun srgssr(http: ExtractorHttp): SRGSSRIE = SRGSSRIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun srgssrPlay(http: ExtractorHttp): SRGSSRPlayIE = SRGSSRPlayIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun stacommuVod(http: ExtractorHttp): StacommuVODIE = StacommuVODIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun stacommuLive(http: ExtractorHttp): StacommuLiveIE = StacommuLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun theaterComplexTownVod(http: ExtractorHttp): TheaterComplexTownVODIE = TheaterComplexTownVODIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun theaterComplexTownPpv(http: ExtractorHttp): TheaterComplexTownPPVIE = TheaterComplexTownPPVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rozhlas(http: ExtractorHttp): RozhlasIE = RozhlasIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rozhlasVltava(http: ExtractorHttp): RozhlasVltavaIE = RozhlasVltavaIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mujRozhlas(http: ExtractorHttp): MujRozhlasIE = MujRozhlasIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun linkedIn(http: ExtractorHttp): LinkedInIE = LinkedInIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun linkedInLearning(http: ExtractorHttp): LinkedInLearningIE = LinkedInLearningIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun linkedInLearningCourse(http: ExtractorHttp): LinkedInLearningCourseIE = LinkedInLearningCourseIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun linkedInEvents(http: ExtractorHttp): LinkedInEventsIE = LinkedInEventsIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rtp(http: ExtractorHttp): RTPIE = RTPIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rtlNl(http: ExtractorHttp): RtlNlIE = RtlNlIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtlLuTeleVod(http: ExtractorHttp): RTLLuTeleVODIE = RTLLuTeleVODIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun rts(http: ExtractorHttp): RTSIE = RTSIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtlLuArticle(http: ExtractorHttp): RTLLuArticleIE = RTLLuArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtlLuLive(http: ExtractorHttp): RTLLuLiveIE = RTLLuLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtlLuRadio(http: ExtractorHttp): RTLLuRadioIE = RTLLuRadioIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun parliamentLiveUk(http: ExtractorHttp): ParliamentLiveUKIE = ParliamentLiveUKIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun rtbf(http: ExtractorHttp): RTBFIE = RTBFIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun wdr(http: ExtractorHttp): WDRIE = WDRIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wdrPage(http: ExtractorHttp): WDRPageIE = WDRPageIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wdrElefant(http: ExtractorHttp): WDRElefantIE = WDRElefantIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun wrestleUniverseVod(http: ExtractorHttp): WrestleUniverseVODIE = WrestleUniverseVODIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wrestleUniversePpv(http: ExtractorHttp): WrestleUniversePPVIE = WrestleUniversePPVIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun adn(http: ExtractorHttp): ADNIE = ADNIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun adnSeason(http: ExtractorHttp): ADNSeasonIE = ADNSeasonIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun wyborczaVideo(http: ExtractorHttp): WyborczaVideoIE = WyborczaVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun wyborczaPodcast(http: ExtractorHttp): WyborczaPodcastIE = WyborczaPodcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tokFMPodcast(http: ExtractorHttp): TokFMPodcastIE = TokFMPodcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tokFMAudition(http: ExtractorHttp): TokFMAuditionIE = TokFMAuditionIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vidio(http: ExtractorHttp): VidioIE = VidioIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vidioPremier(http: ExtractorHttp): VidioPremierIE = VidioPremierIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vidioLive(http: ExtractorHttp): VidioLiveIE = VidioLiveIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vgtv(http: ExtractorHttp): VGTVIE = VGTVIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun btArticle(http: ExtractorHttp): BTArticleIE = BTArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun btVestlendingen(http: ExtractorHttp): BTVestlendingenIE = BTVestlendingenIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tv2(http: ExtractorHttp): TV2IE = TV2IE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tv2Article(http: ExtractorHttp): TV2ArticleIE = TV2ArticleIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun katsomo(http: ExtractorHttp): KatsomoIE = KatsomoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mtvUutisetArticle(http: ExtractorHttp): MTVUutisetArticleIE = MTVUutisetArticleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tenPlay(http: ExtractorHttp): TenPlayIE = TenPlayIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tenPlaySeason(http: ExtractorHttp): TenPlaySeasonIE = TenPlaySeasonIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun teachable(http: ExtractorHttp): TeachableIE = TeachableIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun teachableCourse(http: ExtractorHttp): TeachableCourseIE = TeachableCourseIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun teamcoco(http: ExtractorHttp): TeamcocoIE = TeamcocoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun conanClassic(http: ExtractorHttp): ConanClassicIE = ConanClassicIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tedTalk(http: ExtractorHttp): TedTalkIE = TedTalkIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tedSeries(http: ExtractorHttp): TedSeriesIE = TedSeriesIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tedPlaylist(http: ExtractorHttp): TedPlaylistIE = TedPlaylistIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tedEmbed(http: ExtractorHttp): TedEmbedIE = TedEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun bitChute(http: ExtractorHttp): BitChuteIE = BitChuteIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun bitChuteChannel(http: ExtractorHttp): BitChuteChannelIE = BitChuteChannelIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nitter(http: ExtractorHttp): NitterIE = NitterIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun novaEmbed(http: ExtractorHttp): NovaEmbedIE = NovaEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nova(http: ExtractorHttp): NovaIE = NovaIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tnaFlixNetworkEmbed(http: ExtractorHttp): TNAFlixNetworkEmbedIE = TNAFlixNetworkEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tnaFlix(http: ExtractorHttp): TNAFlixIE = TNAFlixIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun empFlix(http: ExtractorHttp): EMPFlixIE = EMPFlixIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun movieFap(http: ExtractorHttp): MovieFapIE = MovieFapIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun tuneInStation(http: ExtractorHttp): TuneInStationIE = TuneInStationIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tuneInPodcast(http: ExtractorHttp): TuneInPodcastIE = TuneInPodcastIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tuneInPodcastEpisode(http: ExtractorHttp): TuneInPodcastEpisodeIE = TuneInPodcastEpisodeIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tuneInEmbed(http: ExtractorHttp): TuneInEmbedIE = TuneInEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tuneInShortener(http: ExtractorHttp): TuneInShortenerIE = TuneInShortenerIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun googleDrive(http: ExtractorHttp): GoogleDriveIE = GoogleDriveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun googleDriveFolder(http: ExtractorHttp): GoogleDriveFolderIE = GoogleDriveFolderIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vevo(http: ExtractorHttp): VevoIE = VevoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vevoPlaylist(http: ExtractorHttp): VevoPlaylistIE = VevoPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun vice(http: ExtractorHttp): ViceIE = ViceIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viceShow(http: ExtractorHttp): ViceShowIE = ViceShowIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun viceArticle(http: ExtractorHttp): ViceArticleIE = ViceArticleIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun cnn(http: ExtractorHttp): CNNIE = CNNIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cnnIndonesia(http: ExtractorHttp): CNNIndonesiaIE = CNNIndonesiaIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mediaset(http: ExtractorHttp): MediasetIE = MediasetIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mailRu(http: ExtractorHttp): MailRuIE = MailRuIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mailRuMusic(http: ExtractorHttp): MailRuMusicIE = MailRuMusicIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mailRuMusicSearch(http: ExtractorHttp): MailRuMusicSearchIE = MailRuMusicSearchIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mediasetShow(http: ExtractorHttp): MediasetShowIE = MediasetShowIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mediaStream(http: ExtractorHttp): MediaStreamIE = MediaStreamIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun winSportsVideo(http: ExtractorHttp): WinSportsVideoIE = WinSportsVideoIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun twitCasting(http: ExtractorHttp): TwitCastingIE = TwitCastingIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitCastingLive(http: ExtractorHttp): TwitCastingLiveIE = TwitCastingLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun twitCastingUser(http: ExtractorHttp): TwitCastingUserIE = TwitCastingUserIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun brainPop(http: ExtractorHttp): BrainPOPIE = BrainPOPIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brainPopJr(http: ExtractorHttp): BrainPOPJrIE = BrainPOPJrIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brainPopEll(http: ExtractorHttp): BrainPOPELLIE = BrainPOPELLIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brainPopEsp(http: ExtractorHttp): BrainPOPEspIE = BrainPOPEspIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brainPopFr(http: ExtractorHttp): BrainPOPFrIE = BrainPOPFrIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun brainPopIl(http: ExtractorHttp): BrainPOPIlIE = BrainPOPIlIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun art19(http: ExtractorHttp): Art19IE = Art19IE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun art19Show(http: ExtractorHttp): Art19ShowIE = Art19ShowIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun lsmLrEmbed(http: ExtractorHttp): LSMLREmbedIE = LSMLREmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun lsmLtvEmbed(http: ExtractorHttp): LSMLTVEmbedIE = LSMLTVEmbedIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun lsmReplay(http: ExtractorHttp): LSMReplayIE = LSMReplayIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun digitalConcertHall(http: ExtractorHttp): DigitalConcertHallIE = DigitalConcertHallIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun mixcloud(http: ExtractorHttp): MixcloudIE = MixcloudIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mixcloudUser(http: ExtractorHttp): MixcloudUserIE = MixcloudUserIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun mixcloudPlaylist(http: ExtractorHttp): MixcloudPlaylistIE = MixcloudPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun nfb(http: ExtractorHttp): NFBIE = NFBIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun nfbSeries(http: ExtractorHttp): NFBSeriesIE = NFBSeriesIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun skyItPlayer(http: ExtractorHttp): SkyItPlayerIE = SkyItPlayerIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun skyItVideo(http: ExtractorHttp): SkyItVideoIE = SkyItVideoIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun skyItVideoLive(http: ExtractorHttp): SkyItVideoLiveIE = SkyItVideoLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun skyIt(http: ExtractorHttp): SkyItIE = SkyItIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun skyItArte(http: ExtractorHttp): SkyItArteIE = SkyItArteIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun cieloTVIt(http: ExtractorHttp): CieloTVItIE = CieloTVItIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tv8It(http: ExtractorHttp): TV8ItIE = TV8ItIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tv8ItLive(http: ExtractorHttp): TV8ItLiveIE = TV8ItLiveIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun tv8ItPlaylist(http: ExtractorHttp): TV8ItPlaylistIE = TV8ItPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ertFlixCodename(http: ExtractorHttp): ERTFlixCodenameIE = ERTFlixCodenameIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ertFlix(http: ExtractorHttp): ERTFlixIE = ERTFlixIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ertWebtvEmbed(http: ExtractorHttp): ERTWebtvEmbedIE = ERTWebtvEmbedIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun errJupiter(http: ExtractorHttp): ERRJupiterIE = ERRJupiterIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun errArhiiv(http: ExtractorHttp): ERRArhiivIE = ERRArhiivIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun videocampusSachsen(http: ExtractorHttp): VideocampusSachsenIE = VideocampusSachsenIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun vimpPlaylist(http: ExtractorHttp): ViMPPlaylistIE = ViMPPlaylistIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun knownDrm(http: ExtractorHttp): KnownDRMIE = KnownDRMIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun knownPiracy(http: ExtractorHttp): KnownPiracyIE = KnownPiracyIE(http)
    @Provides
    @SingleIn(AppScope::class)
    fun ustream(http: ExtractorHttp): UstreamIE = UstreamIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun ustreamChannel(http: ExtractorHttp): UstreamChannelIE = UstreamChannelIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun knownLiability(http: ExtractorHttp): KnownLiabilityIE = KnownLiabilityIE(http)

    @Provides
    @SingleIn(AppScope::class)
    fun extractorRegistry(http: ExtractorHttp, jsRuntime: JsRuntime): ExtractorRegistry =
        productionExtractorRegistry(http, jsRuntime)
}

/**
 * Convenience for host classes and tests that hold a transfer and a runtime
 * but no graph yet. It constructs the same ordered list as the graph bindings.
 */
fun productionExtractorRegistry(http: ExtractorHttp, jsRuntime: JsRuntime): ExtractorRegistry =
    ExtractorRegistry(
        listOf(
            YoutubeIE(http, jsRuntime),
            YoutubeTabIE(http),
            TwitterIE(http),
            TwitterCardIE(http),
            TwitterAmplifyIE(http),
            TwitterBroadcastIE(http),
            TwitterSpacesIE(http),
            TwitterShortenerIE(http),
            BiliBiliIE(http),
            BiliBiliPlayerIE(http),
            VimeoIE(http),
            PeerTubeIE(http),
            BBCCoUkIE(http),
            TikTokIE(http),
            TikTokVMIE(http),
            TapTapMomentIE(http),
            TapTapAppIE(http),
            TapTapAppIntlIE(http),
            TapTapPostIntlIE(http),
            TMZIE(http),
            SoundcloudIE(http),
            SoundcloudEmbedIE(http),
            SonyLivIE(http),
            SonyLivSeriesIE(http),
            FacebookIE(http),
            FacebookReelIE(http),
            FacebookPluginsVideoIE(http),
            FacebookRedirectURLIE(http),
            FC2IE(http),
            FC2EmbedIE(http),
            FC2LiveIE(http),
            TwitchVodIE(http),
            TwitchStreamIE(http),
            InstagramIE(http),
            IwaraIE(http),
            IwaraUserIE(http),
            IwaraPlaylistIE(http),
            ITVIE(http),
            ITVBTCCIE(http),
            IPrimaIE(http),
            IPrimaCNNIE(http),
            IviIE(http),
            IviCompilationIE(http),
            InstagramIOSIE(http),
            DailymotionIE(http),
            VKIE(http),
            PatreonIE(http),
            PinterestIE(http),
            PinterestCollectionIE(http),
            ArchiveOrgIE(http),
            DPlayIE(http),
            DouyuTVIE(http),
            DouyuShowIE(http),
            DropoutIE(http),
            DropoutSeasonIE(http),
            HGTVDeIE(http),
            GoDiscoveryIE(http),
            TravelChannelIE(http),
            CookingChannelIE(http),
            HGTVUsaIE(http),
            FoodNetworkIE(http),
            DestinationAmericaIE(http),
            InvestigationDiscoveryIE(http),
            AmHistoryChannelIE(http),
            ScienceChannelIE(http),
            DiscoveryLifeIE(http),
            AnimalPlanetIE(http),
            TLCIE(http),
            DiscoveryPlusIE(http),
            DiscoveryPlusIndiaIE(http),
            DiscoveryNetworksDeIE(http),
            DiscoveryPlusItalyIE(http),
            DiscoveryPlusItalyShowIE(http),
            DiscoveryPlusIndiaShowIE(http),
            NBCIE(http),
            NBCNewsIE(http),
            NBCOlympicsIE(http),
            NBCStationsIE(http),
            BravoTVIE(http),
            SyfyIE(http),
            CBCIE(http),
            CBCPlayerIE(http),
            CBCPlayerPlaylistIE(http),
            CBCGemIE(http),
            CBCGemPlaylistIE(http),
            CBCGemContentIE(http),
            CBCGemOlympicsIE(http),
            CBCGemLiveIE(http),
            CBCListenIE(http),
            NiconicoIE(http),
            NiconicoPlaylistIE(http),
            NiconicoSeriesIE(http),
            NicovideoSearchURLIE(http),
            NicovideoTagURLIE(http),
            NiconicoUserIE(http),
            BrightcoveLegacyIE(http),
            BrightcoveNewIE(http),
            PBSIE(http),
            PBSKidsIE(http),
            NhkVodIE(http),
            NhkVodProgramIE(http),
            NhkForSchoolBangumiIE(http),
            NhkForSchoolSubjectIE(http),
            NhkForSchoolProgramListIE(http),
            NhkRadiruIE(http),
            NhkRadioNewsPageIE(http),
            NhkRadiruLiveIE(http),
            ZDFIE(http),
            ZDFChannelIE(http),
            RaiPlayIE(http),
            RaiPlayLiveIE(http),
            RaiPlayPlaylistIE(http),
            RaiPlaySoundIE(http),
            RaiPlaySoundLiveIE(http),
            RaiPlaySoundPlaylistIE(http),
            RaiIE(http),
            RaiNewsIE(http),
            RaiCulturaIE(http),
            RaiSudtirolIE(http),
            NRKIE(http),
            NRKTVIE(http),
            NRKTVEpisodeIE(http),
            NRKTVSeasonIE(http),
            NRKTVSeriesIE(http),
            NRKTVDirekteIE(http),
            NRKRadioPodkastIE(http),
            NRKPlaylistIE(http),
            NRKTVEpisodesIE(http),
            NRKSkoleIE(http),
            WeverseIE(http),
            WeverseMediaIE(http),
            WeverseMomentIE(http),
            WeverseLiveTabIE(http),
            WeverseMediaTabIE(http),
            WeverseLiveIE(http),
            PornHubIE(http),
            PornHubUserIE(http),
            PornHubPagedVideoListIE(http),
            PornHubUserVideosUploadIE(http),
            PornHubPlaylistIE(http),
            ARDBetaMediathekIE(http),
            ARDMediathekCollectionIE(http),
            ARDAudiothekIE(http),
            ARDAudiothekPlaylistIE(http),
            ZattooIE(http),
            ZanIE(http),
            ZattooLiveIE(http),
            ZattooMoviesIE(http),
            ZattooRecordingsIE(http),
            NetPlusTVIE(http),
            NetPlusTVLiveIE(http),
            NetPlusTVRecordingsIE(http),
            MNetTVIE(http),
            MNetTVLiveIE(http),
            MNetTVRecordingsIE(http),
            WalyTVIE(http),
            WalyTVLiveIE(http),
            WalyTVRecordingsIE(http),
            BBVTVIE(http),
            BBVTVLiveIE(http),
            BBVTVRecordingsIE(http),
            VTXTVIE(http),
            VTXTVLiveIE(http),
            VTXTVRecordingsIE(http),
            GlattvisionTVIE(http),
            GlattvisionTVLiveIE(http),
            GlattvisionTVRecordingsIE(http),
            SAKTVIE(http),
            SAKTVLiveIE(http),
            SAKTVRecordingsIE(http),
            EWETVIE(http),
            EWETVLiveIE(http),
            EWETVRecordingsIE(http),
            QuantumTVIE(http),
            QuantumTVLiveIE(http),
            QuantumTVRecordingsIE(http),
            OsnatelTVIE(http),
            OsnatelTVLiveIE(http),
            OsnatelTVRecordingsIE(http),
            EinsUndEinsTVIE(http),
            EinsUndEinsTVLiveIE(http),
            EinsUndEinsTVRecordingsIE(http),
            SaltTVIE(http),
            SaltTVLiveIE(http),
            SaltTVRecordingsIE(http),
            OpenRecIE(http),
            OpenRecCaptureIE(http),
            OpenRecMovieIE(http),
            OpenRecPlaylistIE(http),
            OpenRecChannelIE(http),
            OpenRecChannelSearchIE(http),
            XHamsterIE(http),
            XHamsterEmbedIE(http),
            XHamsterUserIE(http),
            XimalayaIE(http),
            XimalayaAlbumIE(http),
            NetEaseMusicIE(http),
            NetEaseMusicAlbumIE(http),
            NetEaseMusicSingerIE(http),
            NetEaseMusicListIE(http),
            NetEaseMusicMvIE(http),
            NetEaseMusicProgramIE(http),
            NetEaseMusicDjRadioIE(http),
            PanoptoIE(http),
            PanoptoPlaylistIE(http),
            PanoptoListIE(http),
            TVPIE(http),
            TVPStreamIE(http),
            TVPEmbedIE(http),
            TVPVODVideoIE(http),
            TVPlayIE(http),
            TVPlayHomeIE(http),
            TVPVODSeriesIE(http),
            VRTIE(http),
            VrtNUIE(http),
            DagelijkseKostIE(http),
            Radio1BeIE(http),
            KalturaIE(http),
            KuwoIE(http),
            KuwoAlbumIE(http),
            KuwoChartIE(http),
            KuwoSingerIE(http),
            KuwoCategoryIE(http),
            KuwoMvIE(http),
            KickIE(http),
            KickVODIE(http),
            KickClipIE(http),
            GameJoltIE(http),
            GlomexIE(http),
            GlomexEmbedIE(http),
            GameJoltUserIE(http),
            GameJoltGameIE(http),
            GameJoltGameSoundtrackIE(http),
            GameJoltCommunityIE(http),
            GameJoltSearchIE(http),
            TumblrIE(http),
            TubeTuGrazIE(http),
            TubeTuGrazSeriesIE(http),
            IqiyiIE(http),
            IqIE(http),
            IqAlbumIE(http),
            IdagioTrackIE(http),
            IdagioRecordingIE(http),
            IdagioAlbumIE(http),
            IdagioPlaylistIE(http),
            IdagioPersonalPlaylistIE(http),
            AnvatoIE(http),
            PolskieRadioLegacyIE(http),
            PolskieRadioIE(http),
            PolskieRadioAuditionIE(http),
            PolskieRadioCategoryIE(http),
            PolskieRadioPlayerIE(http),
            PolskieRadioPodcastListIE(http),
            PolskieRadioPodcastIE(http),
            NebulaIE(http),
            NebulaClassIE(http),
            NebulaSubscriptionsIE(http),
            NebulaChannelIE(http),
            NebulaSeasonIE(http),
            ZingMp3IE(http),
            ZingMp3AlbumIE(http),
            ZingMp3ChartHomeIE(http),
            ZingMp3WeekChartIE(http),
            ZingMp3ChartMusicVideoIE(http),
            ZingMp3UserIE(http),
            ZingMp3HubIE(http),
            ZingMp3LiveRadioIE(http),
            ZingMp3PodcastEpisodeIE(http),
            ZingMp3PodcastIE(http),
            YouPornIE(http),
            YouPornCategoryIE(http),
            YouPornChannelIE(http),
            YouPornCollectionIE(http),
            YouPornTagIE(http),
            YouPornStarIE(http),
            YouPornVideosIE(http),
            YoukuIE(http),
            YoukuShowIE(http),
            NPOIE(http),
            NPOLiveIE(http),
            NPORadioIE(http),
            NPORadioFragmentIE(http),
            SchoolTVIE(http),
            HetKlokhuisIE(http),
            VPROIE(http),
            WNLIE(http),
            AndereTijdenIE(http),
            ORFRadioIE(http),
            ORFPodcastIE(http),
            ORFIPTVIE(http),
            ORFFM4StoryIE(http),
            ORFONIE(http),
            OmnyfmIE(http),
            OmnyfmPlaylistIE(http),
            OmnyfmShowIE(http),
            BandcampIE(http),
            BandcampAlbumIE(http),
            BandcampWeeklyIE(http),
            BandcampUserIE(http),
            ViuIE(http),
            ViuPlaylistIE(http),
            ViuOTTIE(http),
            ViuOTTIndonesiaIE(http),
            RokfinIE(http),
            RokfinStackIE(http),
            RokfinChannelIE(http),
            SlidesLiveIE(http),
            SenateISVPIE(http),
            SenateGovIE(http),
            NexxIE(http),
            NexxEmbedIE(http),
            FranceTVIE(http),
            FranceTVSiteIE(http),
            FranceTVInfoIE(http),
            MLBIE(http),
            MLBVideoIE(http),
            MLBTVIE(http),
            MLBArticleIE(http),
            AbemaTVIE(http),
            AbemaTVTitleIE(http),
            VQQVideoIE(http),
            VQQSeriesIE(http),
            WeTvEpisodeIE(http),
            WeTvSeriesIE(http),
            IflixEpisodeIE(http),
            IflixSeriesIE(http),
            AfreecaTVIE(http),
            AfreecaTVCatchStoryIE(http),
            AfreecaTVLiveIE(http),
            AfreecaTVUserIE(http),
            VidyardIE(http),
            LoomIE(http),
            LoomFolderIE(http),
            UdemyIE(http),
            UdemyCourseIE(http),
            HotStarIE(http),
            HotStarPrefixIE(http),
            HotStarSeriesIE(http),
            QQMusicIE(http),
            QQMusicSingerIE(http),
            QQMusicAlbumIE(http),
            QQMusicToplistIE(http),
            QQMusicPlaylistIE(http),
            QQMusicVideoIE(http),
            BandlabIE(http),
            BandlabPlaylistIE(http),
            CBSNewsIE(http),
            CBSIE(http),
            ParamountPressExpressIE(http),
            CeskaTelevizeIE(http),
            CondeNastIE(http),
            CBSNewsEmbedIE(http),
            CBSLocalIE(http),
            CBSLocalArticleIE(http),
            CBSLocalLiveIE(http),
            CBSNewsLiveIE(http),
            CBSNewsLiveVideoIE(http),
            ABCIE(http),
            ABCIViewIE(http),
            ABCIViewShowSeriesIE(http),
            AENetworksIE(http),
            AENetworksCollectionIE(http),
            AENetworksShowIE(http),
            HistoryTopicIE(http),
            HistoryPlayerIE(http),
            BiographyIE(http),
            RedditIE(http),
            RedGifsIE(http),
            RedGifsSearchIE(http),
            RedGifsUserIE(http),
            ThePlatformIE(http),
            ThePlatformFeedIE(http),
            NYTimesIE(http),
            NYTimesArticleIE(http),
            NYTimesCookingIE(http),
            NYTimesCookingRecipeIE(http),
            RadioFranceIE(http),
            RadikoIE(http),
            RadikoRadioIE(http),
            FranceCultureIE(http),
            RadioFranceLiveIE(http),
            RadioFrancePodcastIE(http),
            RadioFranceProfileIE(http),
            RadioFranceProgramScheduleIE(http),
            NDRIE(http),
            NewgroundsIE(http),
            NekoHackerIE(http),
            NinaProtocolIE(http),
            NewgroundsPlaylistIE(http),
            NewgroundsUserIE(http),
            NJoyIE(http),
            NDREmbedBaseIE(http),
            NDREmbedIE(http),
            NJoyEmbedIE(http),
            PluralsightIE(http),
            PluralsightCourseIE(http),
            PlaySuisseIE(http),
            LBRYIE(http),
            LBRYChannelIE(http),
            LBRYPlaylistIE(http),
            LifeNewsIE(http),
            LifeEmbedIE(http),
            ESPNIE(http),
            ESPNArticleIE(http),
            FiveThirtyEightIE(http),
            ESPNCricInfoIE(http),
            WatchESPNIE(http),
            NiconicoChannelPlusIE(http),
            NiconicoChannelPlusChannelVideosIE(http),
            NiconicoChannelPlusChannelLivesIE(http),
            RutubeIE(http),
            RutubeEmbedIE(http),
            RutubeTagsIE(http),
            RutubeMovieIE(http),
            RutubePersonIE(http),
            SafariIE(http),
            SafariApiIE(http),
            SafariCourseIE(http),
            RutubePlaylistIE(http),
            RutubeChannelIE(http),
            RuutuIE(http),
            YandexVideoIE(http),
            YandexVideoPreviewIE(http),
            ZenYandexIE(http),
            ZenYandexChannelIE(http),
            MxplayerIE(http),
            MxplayerSeasonIE(http),
            MxplayerShowIE(http),
            MxplayerRedirectIE(http),
            MTVIE(http),
            MSNIE(http),
            GoPlayIE(http),
            YandexMusicTrackIE(http),
            YandexMusicAlbumIE(http),
            YandexMusicPlaylistIE(http),
            YandexMusicArtistTracksIE(http),
            YandexMusicArtistAlbumsIE(http),
            BlueskyIE(http),
            BoostyIE(http),
            WeiboIE(http),
            WeiboVideoIE(http),
            WeiboUserIE(http),
            FloatplaneIE(http),
            FourTubeIE(http),
            FuxIE(http),
            PornTubeIE(http),
            PornerBrosIE(http),
            FloatplaneChannelIE(http),
            ArteTVIE(http),
            ArteTVEmbedIE(http),
            ArteTVPlaylistIE(http),
            ArteTVCategoryIE(http),
            AudiusIE(http),
            AudiusTrackIE(http),
            AudiusPlaylistIE(http),
            AudiusProfileIE(http),
            AmazonMiniTvIE(http),
            AmazonMiniTvSeasonIE(http),
            AmazonMiniTvSeriesIE(http),
            OdnoklassnikiIE(http),
            OnetIE(http),
            OnetMVPIE(http),
            OnetChannelIE(http),
            OnetPlIE(http),
            TxxxIE(http),
            PornTopIE(http),
            WistiaIE(http),
            WistiaPlaylistIE(http),
            WistiaChannelIE(http),
            WykopDigIE(http),
            WykopDigCommentIE(http),
            WykopPostIE(http),
            WykopPostCommentIE(http),
            SVTPlayIE(http),
            SVTSeriesIE(http),
            SVTPageIE(http),
            StagePlusVODConcertIE(http),
            CDAIE(http),
            CSpanIE(http),
            CSpanCongressIE(http),
            CDAFolderIE(http),
            RCTIPlusIE(http),
            RCTIPlusSeriesIE(http),
            RCTIPlusTVIE(http),
            MediasiteIE(http),
            MediasiteCatalogIE(http),
            MediasiteNamedCatalogIE(http),
            RTVEALaCartaIE(http),
            RTVEAudioIE(http),
            RTVELiveIE(http),
            RTVETelevisionIE(http),
            RTVEProgramIE(http),
            RTVCPlayIE(http),
            RTVCPlayEmbedIE(http),
            RTVCKalturaIE(http),
            TVerIE(http),
            TVerOlympicIE(http),
            RumbleEmbedIE(http),
            RumbleIE(http),
            RumbleChannelIE(http),
            JioSaavnSongIE(http),
            ShugiinItvLiveIE(http),
            ShugiinItvLiveRoomIE(http),
            ShugiinItvVodIE(http),
            SangiinIE(http),
            SangiinInstructionIE(http),
            JioSaavnShowIE(http),
            JioSaavnAlbumIE(http),
            JioSaavnPlaylistIE(http),
            JioSaavnShowPlaylistIE(http),
            JioSaavnArtistIE(http),
            NBAWatchEmbedIE(http),
            NBAWatchIE(http),
            NBAWatchCollectionIE(http),
            NBAEmbedIE(http),
            NBAIE(http),
            NaverIE(http),
            NaverLiveIE(http),
            NBAChannelIE(http),
            RCSEmbedsIE(http),
            RCSIE(http),
            RCSVariousIE(http),
            IGNIE(http),
            IGNVideoIE(http),
            IGNArticleIE(http),
            NFLIE(http),
            NFLArticleIE(http),
            NFLPlusReplayIE(http),
            NFLPlusEpisodeIE(http),
            PRXStoryIE(http),
            PRXSeriesIE(http),
            PRXAccountIE(http),
            ImgurIE(http),
            ImgurGalleryIE(http),
            ImgurAlbumIE(http),
            DRTVIE(http),
            DRTVLiveIE(http),
            DRTVSeasonIE(http),
            DRTVSeriesIE(http),
            RoosterTeethIE(http),
            RoosterTeethSeriesIE(http),
            YahooIE(http),
            YahooJapanNewsIE(http),
            GoIE(http),
            MicrosoftEmbedIE(http),
            MicrosoftMediusIE(http),
            MicrosoftLearnPlaylistIE(http),
            MicrosoftLearnEpisodeIE(http),
            MicrosoftLearnSessionIE(http),
            MicrosoftBuildIE(http),
            ViewLiftEmbedIE(http),
            ViewLiftIE(http),
            SmotrimIE(http),
            SmotrimAudioIE(http),
            SmotrimLiveIE(http),
            SmotrimPlaylistIE(http),
            SohuIE(http),
            SohuVIE(http),
            SouthParkIE(http),
            SouthParkEsIE(http),
            SouthParkDeIE(http),
            SouthParkLatIE(http),
            SouthParkDkIE(http),
            SouthParkComBrIE(http),
            SouthParkCoUkIE(http),
            StreaksIE(http),
            SRGSSRIE(http),
            SRGSSRPlayIE(http),
            StacommuVODIE(http),
            StacommuLiveIE(http),
            TheaterComplexTownVODIE(http),
            TheaterComplexTownPPVIE(http),
            RozhlasIE(http),
            RozhlasVltavaIE(http),
            MujRozhlasIE(http),
            LinkedInIE(http),
            LinkedInLearningIE(http),
            LinkedInLearningCourseIE(http),
            LinkedInEventsIE(http),
            RTPIE(http),
            RtlNlIE(http),
            RTLLuTeleVODIE(http),
            RTSIE(http),
            RTLLuArticleIE(http),
            RTLLuLiveIE(http),
            RTLLuRadioIE(http),
            ParliamentLiveUKIE(http),
            RTBFIE(http),
            WDRIE(http),
            WDRPageIE(http),
            WDRElefantIE(http),
            WrestleUniverseVODIE(http),
            WrestleUniversePPVIE(http),
            ADNIE(http),
            ADNSeasonIE(http),
            WyborczaVideoIE(http),
            WyborczaPodcastIE(http),
            TokFMPodcastIE(http),
            TokFMAuditionIE(http),
            VidioIE(http),
            VidioPremierIE(http),
            VidioLiveIE(http),
            VGTVIE(http),
            BTArticleIE(http),
            BTVestlendingenIE(http),
            TV2IE(http),
            TV2ArticleIE(http),
            KatsomoIE(http),
            MTVUutisetArticleIE(http),
            TenPlayIE(http),
            TenPlaySeasonIE(http),
            TeachableIE(http),
            TeachableCourseIE(http),
            TeamcocoIE(http),
            ConanClassicIE(http),
            TedTalkIE(http),
            TedSeriesIE(http),
            TedPlaylistIE(http),
            TedEmbedIE(http),
            BitChuteIE(http),
            BitChuteChannelIE(http),
            NitterIE(http),
            NovaEmbedIE(http),
            NovaIE(http),
            TNAFlixNetworkEmbedIE(http),
            TNAFlixIE(http),
            EMPFlixIE(http),
            MovieFapIE(http),
            TuneInStationIE(http),
            TuneInPodcastIE(http),
            TuneInPodcastEpisodeIE(http),
            TuneInEmbedIE(http),
            TuneInShortenerIE(http),
            GoogleDriveIE(http),
            GoogleDriveFolderIE(http),
            VevoIE(http),
            VevoPlaylistIE(http),
            ViceIE(http),
            ViceShowIE(http),
            ViceArticleIE(http),
            CNNIE(http),
            CNNIndonesiaIE(http),
            MediasetIE(http),
            MailRuIE(http),
            MailRuMusicIE(http),
            MailRuMusicSearchIE(http),
            MediasetShowIE(http),
            MediaStreamIE(http),
            WinSportsVideoIE(http),
            TwitCastingIE(http),
            TwitCastingLiveIE(http),
            TwitCastingUserIE(http),
            BrainPOPIE(http),
            BrainPOPJrIE(http),
            BrainPOPELLIE(http),
            BrainPOPEspIE(http),
            BrainPOPFrIE(http),
            BrainPOPIlIE(http),
            Art19IE(http),
            Art19ShowIE(http),
            LSMLREmbedIE(http),
            LSMLTVEmbedIE(http),
            LSMReplayIE(http),
            DigitalConcertHallIE(http),
            MixcloudIE(http),
            MixcloudUserIE(http),
            MixcloudPlaylistIE(http),
            NFBIE(http),
            NFBSeriesIE(http),
            SkyItPlayerIE(http),
            SkyItVideoIE(http),
            SkyItVideoLiveIE(http),
            SkyItIE(http),
            SkyItArteIE(http),
            CieloTVItIE(http),
            TV8ItIE(http),
            TV8ItLiveIE(http),
            TV8ItPlaylistIE(http),
            ERTFlixCodenameIE(http),
            ERTFlixIE(http),
            ERTWebtvEmbedIE(http),
            ERRJupiterIE(http),
            ERRArhiivIE(http),
            VideocampusSachsenIE(http),
            ViMPPlaylistIE(http),
            UstreamIE(http),
            UstreamChannelIE(http),
            KnownDRMIE(http),
            KnownPiracyIE(http),
            KnownLiabilityIE(http),
        ),
    )

/** Same list, built from a raw transfer; used by the host classes' constructors. */
fun productionExtractorRegistry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry =
    productionExtractorRegistry(ExtractorHttp(transfer), jsRuntime)
