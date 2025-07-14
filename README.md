<div>
    <div>
        <img src="https://img.shields.io/maven-central/v/io.github.remmerw/loki" alt="Kotlin Maven Version" />
        <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android" alt="Badge Android" />
        <img src="https://img.shields.io/badge/Platform-JVM-8A2BE2.svg?logo=openjdk" alt="Badge JVM" />
    </div>
</div>

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
            implementation("io.github.remmerw:loki:0.3.1")
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
        
        // temp directory where to store intermediate files
        val cacheDir = SystemTemporaryDirectory 
        val port = 7777 // port the DHT is working on


        val storage =
            download(magnetUri, cacheDir, port) { torrentState: State ->
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