package com.socle.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", System.getenv("CLOUDINARY_CLOUD_NAME"));
        config.put("api_key", System.getenv("CLOUDINARY_API_KEY"));
        config.put("api_secret", System.getenv("CLOUDINARY_API_SECRET"));
        config.put("secure", true);
        this.cloudinary = new Cloudinary(config);
    }

    @SuppressWarnings("unchecked")
    public String uploadPdf(MultipartFile file) throws IOException {
        String publicId = "doc_" + UUID.randomUUID().toString().replace("-", "") + ".pdf";

        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "resource_type", "raw",
                "folder", "socle/documents",
                "public_id", publicId,
                "use_filename", false,
                "unique_filename", false
        ));
        return (String) uploadResult.get("secure_url");
    }
}
