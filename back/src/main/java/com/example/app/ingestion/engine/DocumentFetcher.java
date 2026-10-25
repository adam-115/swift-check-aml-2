package com.example.app.ingestion.engine;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Télécharge le document lié à un enregistrement (champ dérivé {@code fetch}) et en rend le texte.
 *
 * <p>Ordre de recherche, par nom de fichier (dernier segment de l'adresse) : les dossiers locaux de la
 * source (fichiers de test hors ligne), puis le cache, puis le réseau. Un document téléchargé est gardé
 * dans le cache : les actes publiés ne changent pas, une nouvelle exécution ne télécharge que les nouveaux.
 * Un document introuvable donne un texte vide (compté dans {@link #getMissing()}), sans arrêter l'exécution.
 */
@Slf4j
public class DocumentFetcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration PAUSE = Duration.ofMillis(50);

    private final Path cacheDir;
    private final List<Path> localDirs;
    private final HttpClient client;
    private int downloaded;
    private int fromCache;
    private int missing;

    public DocumentFetcher(Path cacheDir, List<Path> localDirs) {
        this.cacheDir = cacheDir;
        this.localDirs = localDirs;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Texte du document, ou {@code null} s'il est introuvable. */
    public String fetchText(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String name = fileName(url);
        try {
            for (Path dir : localDirs) {
                Path local = dir.resolve(name);
                if (Files.isRegularFile(local)) {
                    fromCache++;
                    return text(Files.readString(local, StandardCharsets.UTF_8));
                }
            }
            Path cached = cacheDir == null ? null : cacheDir.resolve(name);
            if (cached != null && Files.isRegularFile(cached) && Files.size(cached) > 0) {
                fromCache++;
                return text(Files.readString(cached, StandardCharsets.UTF_8));
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                missing++;
                return null;
            }
            byte[] body = download(url);
            if (body == null) {
                missing++;
                return null;
            }
            downloaded++;
            if (cached != null) {
                Files.createDirectories(cacheDir);
                Files.write(cached, body);
            }
            return text(new String(body, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Document illisible {} : {}", url, e.getMessage());
            missing++;
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Téléchargement interrompu", e);
        }
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Mozilla/5.0 (compatible; SwiftCheckAML/1.0)")
                .GET()
                .build();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                Thread.sleep(PAUSE.toMillis());    // on ne surcharge pas le serveur
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() < 500) {
                    log.debug("Document absent ({}) : {}", response.statusCode(), url);
                    return null;
                }
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
            Thread.sleep(500L * attempt);
        }
        return null;
    }

    /** Texte d'un document XML ou HTML, espaces regroupés. */
    static String text(String content) {
        String text = content.trim().startsWith("<")
                ? Jsoup.parse(content, "", Parser.xmlParser()).text()
                : content;
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    static String fileName(String url) {
        String path = url.split("[?#]")[0].replaceAll("/+$", "");
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public int getDownloaded() {
        return downloaded;
    }

    public int getFromCache() {
        return fromCache;
    }

    public int getMissing() {
        return missing;
    }
}
