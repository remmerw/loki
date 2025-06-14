The **Loki** library is currently in development [**Version 0.1.0**]

![Maven Central Version](https://img.shields.io/maven-central/v/:io.github.remmerw/:loki)

## Loki

The **Loki** Bittorrent library implements the download of **magnet** URI.

This sections describes the main goals of this project

- The library should be usable in the mobile context (Android). That requires that it is optimize
  towards memory usage and performance. The dependency on Android limits the used OpenJdk version.
  The minimum supported Android SDK version defines the OpenJdk version. Currently SDK35 with
  OpenJdk version 21 is supported [**Version 1.0.0**]
- The size of the library should be as small as possible. Reason is that it should be compiled as
  wasm library, and the more data are transferred in the Web context the less attractive the library
  will be (same goes for Android apps). The reduced size will be achieved by a well defined set of
  features and a pure kotlin implementation. [**Version 1.0.0**]
- Library is a Kotlin Multiplatform Library [**Version 1.0.0**]
- The library should be published to Maven Central [**Version 1.0.0**]

## Download Magnet URI

```
     @Test
    fun downloadMagnetUri(): Unit = runBlocking(Dispatchers.IO) {
        val uri = 
            "magnet:?xt=urn:btih:..." // needs a valid magnet Uri

        val magnetUri = parseMagnetUri(uri)
        val torrentId = magnetUri.torrentId
        val baseDirectory = Files.createTempDirectory("").toFile()

        val dataDir = File(baseDirectory, magnetUri.displayName ?: "empty")
        if (!dataDir.exists()) {
            val success = dataDir.mkdir()
            assertTrue(success)
        }
        val directory = Path(dataDir.absolutePath)

        val storage = downloadTorrent(SystemTemporaryDirectory, 7777, torrentId) { torrentState: State ->
            val completePieces = torrentState.piecesComplete
            val totalPieces = torrentState.piecesTotal

            System.err.println(" pieces : $completePieces/$totalPieces")
        }
        storage.storeTo(directory)
        storage.finish()
    }
    
```