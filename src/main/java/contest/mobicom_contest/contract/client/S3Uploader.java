package contest.mobicom_contest.contract.client;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class S3Uploader {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFile(MultipartFile multipartFile, String dirName) throws IOException {
        String fileName = dirName + "/" + UUID.randomUUID() + "_" + multipartFile.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        metadata.setContentType(multipartFile.getContentType());

        amazonS3.putObject(bucket, fileName, multipartFile.getInputStream(), metadata);
        return amazonS3.getUrl(bucket, fileName).toString();
    }


    public String uploadBytes(byte[] bytes, String dirName, String fileName) {
        String fullFileName = dirName + "/" + UUID.randomUUID() + "_" + fileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("image/jpeg");

        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        amazonS3.putObject(new PutObjectRequest(bucket, fullFileName, is, metadata));
        return amazonS3.getUrl(bucket, fullFileName).toString();
    }
}

