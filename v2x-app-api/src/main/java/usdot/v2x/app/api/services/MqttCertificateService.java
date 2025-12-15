package usdot.v2x.app.api.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import usdot.v2x.app.api.models.mqtt.MqttCertificateResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MqttCertificateService {
    
    private static final Pattern SERIAL_PATTERN = Pattern.compile("serial=([0-9A-Fa-f]+)", Pattern.CASE_INSENSITIVE);
    
    /**
     * Generate a client certificate using the shell script
     */
    public MqttCertificateResponse generateClientCertificate(String clientId, String certsDirectory) {
        log.info("Generating certificate for client: {}", clientId);
        
        try {
            // Try multiple possible script paths (local dev and Docker)
            Path scriptPath = null;
            String[] possiblePaths = {
                "/usr/local/bin/generate-client-cert.sh",  // Docker location
                "resources/mosquitto/generate-client-cert.sh",  // Local dev
                "../resources/mosquitto/generate-client-cert.sh",  // Alternative local
                System.getProperty("user.dir") + "/resources/mosquitto/generate-client-cert.sh"  // Absolute local
            };
            
            for (String path : possiblePaths) {
                Path testPath = Paths.get(path);
                if (Files.exists(testPath)) {
                    scriptPath = testPath.toAbsolutePath();
                    log.debug("Found certificate generation script at: {}", scriptPath);
                    break;
                }
            }
            
            if (scriptPath == null || !Files.exists(scriptPath)) {
                throw new IllegalStateException("Certificate generation script not found. Tried: " + 
                        String.join(", ", possiblePaths) + ". Please ensure the script is accessible.");
            }
            
            // Ensure certs directory exists
            Path certsDir = Paths.get(certsDirectory);
            if (!Files.exists(certsDir)) {
                Files.createDirectories(certsDir);
            }
            
            // Ensure certs directory is absolute path for the script
            Path certsDirAbsolute = certsDir.toAbsolutePath();
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "bash", scriptPath.toString(), clientId
            );
            processBuilder.environment().put("CLIENT_CERT_DAYS", "365");
            // Set CERT_DIR environment variable so script knows where to find/create certs
            processBuilder.environment().put("CERT_DIR", certsDirAbsolute.toString());
            // Set working directory to certs directory so script can access CA certs
            processBuilder.directory(certsDirAbsolute.toFile());
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes());
                throw new RuntimeException("Certificate generation failed: " + error);
            }
            
            log.info("Certificate generated successfully for client: {}", clientId);
            return getClientCertificate(clientId, certsDirectory);
            
        } catch (Exception e) {
            log.error("Failed to generate certificate for client: {}", clientId, e);
            throw new RuntimeException("Certificate generation failed", e);
        }
    }
    
    /**
     * Read client certificate files from disk
     */
    public MqttCertificateResponse getClientCertificate(String clientId, String certsDirectory) {
        log.debug("Reading certificate for client: {}", clientId);
        
        try {
            Path certDir = Paths.get(certsDirectory);
            Path clientCertPath = certDir.resolve(clientId + ".crt");
            Path clientKeyPath = certDir.resolve(clientId + ".key");
            Path caCertPath = certDir.resolve("ca.crt");
            
            if (!Files.exists(clientCertPath) || !Files.exists(clientKeyPath) || !Files.exists(caCertPath)) {
                throw new IllegalStateException("Certificate files not found for client: " + clientId);
            }
            
            String clientCert = Files.readString(clientCertPath);
            String clientKey = Files.readString(clientKeyPath);
            String caCert = Files.readString(caCertPath);
            
            // Extract certificate information
            Instant expiresAt = extractExpirationDate(clientCert);
            String serialNumber = extractSerialNumber(clientCert);
            
            return MqttCertificateResponse.builder()
                    .clientId(clientId)
                    .clientCertificate(clientCert)
                    .clientKey(clientKey)
                    .caCertificate(caCert)
                    .expiresAt(expiresAt)
                    .serialNumber(serialNumber)
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to read certificate files for client: {}", clientId, e);
            throw new RuntimeException("Failed to read certificate files", e);
        }
    }
    
    private Instant extractExpirationDate(String certPem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            byte[] certBytes = Base64.getDecoder().decode(
                    certPem.replace("-----BEGIN CERTIFICATE-----", "")
                           .replace("-----END CERTIFICATE-----", "")
                           .replaceAll("\\s", "")
            );
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(certBytes));
            return cert.getNotAfter().toInstant();
        } catch (Exception e) {
            log.warn("Failed to extract expiration date from certificate", e);
            return null;
        }
    }
    
    private String extractSerialNumber(String certPem) {
        try {
            // Try to extract from openssl output format
            Matcher matcher = SERIAL_PATTERN.matcher(certPem);
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // Fallback: extract from certificate
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            byte[] certBytes = Base64.getDecoder().decode(
                    certPem.replace("-----BEGIN CERTIFICATE-----", "")
                           .replace("-----END CERTIFICATE-----", "")
                           .replaceAll("\\s", "")
            );
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(certBytes));
            return cert.getSerialNumber().toString(16).toUpperCase();
        } catch (Exception e) {
            log.warn("Failed to extract serial number from certificate", e);
            return null;
        }
    }
}

