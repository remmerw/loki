![Maven Central Version](https://img.shields.io/maven-central/v/:io.github.remmerw/:loki)

## Loki

The **Loki** Bittorrent library implements the download of **magnet** URIs. 

The implemented DHT based on 
[Mainline DHT](https://en.wikipedia.org/wiki/Mainline_DHT).
The specification can be found here [Bittorrent](https://www.bittorrent.org/beps/bep_0000.html).


## Integration

```
    
kotlin {
    sourceSets {
        commonMain.dependencies {
            ...
            implementation("io.github.remmerw:loki:0.1.2")
        }
        ...
    }
}
    
```

## Download Magnet URI

```
    
    fun downloadMagnetUri(): Unit = runBlocking(Dispatchers.IO) {
        val uri = 
            "magnet:?xt=urn:btih:..." // needs a valid magnet Uri

        val magnetUri = parseMagnetUri(uri)
        val torrentId = magnetUri.torrentId
        val cacheDir = SystemTemporaryDirectory // temp directory where to store intermediate data


        val storage =
            downloadTorrent(cacheDir, 7777, torrentId) { torrentState: State ->
                val completePieces = torrentState.piecesComplete
                val totalPieces = torrentState.piecesTotal

                println(" pieces : $completePieces/$totalPieces")
            }

        val dataDir = Path( cacheDir, magnetUri.displayName ?: "empty")
        SystemFileSystem.createDirectories(dataDir)

        storage.storeTo(dataDir) // store files in the final directory
        storage.finish() // cleanup of intermediate files
    }
    
```