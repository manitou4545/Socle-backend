package com.socle.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CloudinaryService() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", System.getenv("CLOUDINARY_CLOUD_NAME"));
        config.put("api_key", System.getenv("CLOUDINARY_API_KEY"));
        config.put("api_secret", System.getenv("CLOUDINARY_API_SECRET"));
        config.put("secure", true);
        this.cloudinary = new Cloudinary(config);
    }

    /** Envoie un fichier PDF PUBLIC vers Cloudinary et retourne son URL sécurisée (https). */
    @SuppressWarnings("unchecked")
    public String uploadPdf(MultipartFile file) throws IOException {
        return uploadPublicBytes(file.getBytes());
    }

    /** Envoie des octets PDF PUBLICS (ex: la version "publique" avec pages verrouillées
     *  remplacées) et retourne son URL sécurisée (https), accessible à tous. */
    @SuppressWarnings("unchecked")
    public String uploadPublicBytes(byte[] bytes) throws IOException {
        Map<String, Object> uploadResult = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "resource_type", "raw",
                "folder", "socle/documents/public",
                "public_id", UUID.randomUUID().toString()
        ));
        return (String) uploadResult.get("secure_url");
    }

    /** Envoie le PDF ORIGINAL COMPLET en mode "authenticated" : Cloudinary refuse toute
     *  requête sans URL signée, donc personne ne peut le récupérer en devinant/partageant
     *  l'URL. Retourne le public_id Cloudinary, à conserver côté document (jamais exposé
     *  tel quel à l'API — seulement utilisé en interne par le backend). */
    @SuppressWarnings("unchecked")
    public String uploadPrivatePdf(byte[] bytes) throws IOException {
        String publicId = "socle/documents/private/" + UUID.randomUUID();
        cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "resource_type", "raw",
                "type", "authenticated",
                "public_id", publicId
        ));
        return publicId;
    }

    /** Télécharge les octets d'un PDF PUBLIC déjà hébergé (ex: pour extraire son texte
     *  côté serveur avant de le donner à l'assistant IA). Pas de signature nécessaire :
     *  cette URL est déjà librement accessible. */
    public byte[] downloadPublicBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Échec du téléchargement du PDF public (code " + response.statusCode() + ")");
        }
        return response.body();
    }

    /** Récupère les octets du PDF original privé, en générant une URL signée à durée de
     *  vie courte puis en la téléchargeant immédiatement côté serveur. */
    public byte[] downloadPrivateBytes(String publicId) throws IOException, InterruptedException {
        String signedUrl = cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .signed(true)
                .generate(publicId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(signedUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Échec du téléchargement du PDF original depuis Cloudinary (code " + response.statusCode() + ")");
        }
        return response.body();
    }
}

