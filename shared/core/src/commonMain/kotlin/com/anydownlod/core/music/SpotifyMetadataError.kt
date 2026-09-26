/*
 * Spotify metadata failures — AnyDownload
 *
 * Typed, redacted failures for the D6 metadata clients. A message never
 * carries a URL, a token, a client secret, or a raw Spotify body.
 */
package com.anydownlod.core.music

/** The typed failures the Spotify metadata clients may raise. */
sealed class SpotifyMetadataError(message: String) : Exception(message) {

    /** The input is neither a Spotify link nor usable search text. */
    class BadQuery(message: String = "Enter a Spotify link or search text.") : SpotifyMetadataError(message)

    /** The query resolved, but Spotify returned no usable match. */
    class NoResults(message: String = "No Spotify match was found.") : SpotifyMetadataError(message)

    /** A single requested entity does not exist or was removed. */
    class NotFound(message: String = "This Spotify item is unavailable.") : SpotifyMetadataError(message)

    /** Spotify refused the request; the token is missing or expired. */
    class NotAuthorized(message: String = "Spotify did not accept the metadata request.") : SpotifyMetadataError(message)

    /** Spotify returned a document the client could not parse. */
    class Malformed(reason: String = "Spotify returned an unexpected response.") : SpotifyMetadataError(reason)

    /** The request could not be completed (network, rate limit, host gap). */
    class Unavailable(message: String = "Spotify metadata could not be loaded.") : SpotifyMetadataError(message)
}
