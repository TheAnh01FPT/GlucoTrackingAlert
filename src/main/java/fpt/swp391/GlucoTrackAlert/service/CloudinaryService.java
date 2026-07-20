package fpt.swp391.GlucoTrackAlert.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload a MultipartFile to Cloudinary into a specified folder
     *
     * @param file MultipartFile to upload
     * @param folder Destination folder name on Cloudinary (e.g.
     * "patient_evidences", "doctors")
     * @return Secure HTTPS URL of the uploaded image
     * @throws IOException if uploading fails
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File upload không được để trống");
        }

        Map<?, ?> params = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"
        );

        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
        return (String) uploadResult.get("secure_url");
    }
}
