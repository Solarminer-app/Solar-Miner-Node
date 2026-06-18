package de.verdox.solarminer.pcagent.xmr.download;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class XmrDownloadService {
    public static final Path MAIN_PATH = Path.of("./solarminer-agent/xmrig/");
    public static final Path CONFIG_PATH = Path.of(MAIN_PATH+"/config.json");

    private static final Logger LOGGER = Logger.getLogger(XmrDownloadService.class.getName());

    private final XMRigApiClient apiClient;

    public XmrDownloadService(XMRigApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void detectOSAndDownloadXMRig() {
        SystemInfo systemInfo = new SystemInfo();
        String osFamily = systemInfo.getOperatingSystem().getFamily();
        String osArch = System.getProperty("os.arch").toLowerCase();

        String xmrigOsString = mapToXmrigOsString(osFamily, osArch);

        if (xmrigOsString == null) {
            LOGGER.warning("Could not map detected OS family '" + osFamily + "' and architecture '" + osArch + "' to a supported XMRig target.");
            return;
        }

        LOGGER.info("Detected target system for XMRig: " + xmrigOsString);

        try {
            LOGGER.info("Fetching the latest XMRig release info...");
            XMRigRelease release = apiClient.getLatestXmrigRelease();
            System.out.println(release);

            Optional<XMRigAssetObject> targetAsset = release.assets().stream()
                    .filter(asset -> asset.os().equals(xmrigOsString))
                    .findFirst();

            if (targetAsset.isPresent()) {
                downloadFile(targetAsset.get());
            } else {
                LOGGER.warning("No suitable download asset found for target '" + xmrigOsString + "' in release version " + release.version() + ".");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while fetching or downloading XMRig: " + e.getMessage(), e);
        }
    }

    private String mapToXmrigOsString(String family, String arch) {
        String os = family.toLowerCase();
        String mappedOs;

        if (os.contains("windows")) mappedOs = "windows";
        else if (os.contains("linux")) mappedOs = "linux";
        else if (os.contains("macos") || os.contains("mac")) mappedOs = "macos";
        else if (os.contains("freebsd")) mappedOs = "freebsd";
        else return null;

        String mappedArch;
        if (arch.contains("amd64") || arch.contains("x86_64")) mappedArch = "x64";
        else if (arch.contains("aarch64") || arch.contains("arm64")) mappedArch = "arm64";
        else return null;

        return mappedOs + "-" + mappedArch;
    }

    private void downloadFile(XMRigAssetObject asset) throws Exception {
        long sizeInMb = asset.size() / 1024 / 1024;

        if (!asset.name().toLowerCase().endsWith(".zip")) {
            LOGGER.warning("The asset " + asset.name() + " is not a .zip file! Standard Java cannot extract .tar.gz files without external libraries.");
            return;
        }

        LOGGER.info("Starting download and specific extraction: " + asset.name() + " (" + sizeInMb + " MB)");

        Path targetDir = Paths.get("./solarminer-agent/xmrig/").toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        int extractedFilesCount = 0;

        try (InputStream in = new URL(asset.url()).openStream();
             ZipInputStream zis = new ZipInputStream(in)) {

            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    String fileName = Paths.get(zipEntry.getName()).getFileName().toString();

                    if (fileName.equals("xmrig.exe") || fileName.equals("xmrig")) {

                        Path newPath = targetDir.resolve(fileName).toAbsolutePath().normalize();

                        if (!newPath.startsWith(targetDir)) {
                            throw new IOException("Bad zip entry trying to escape target directory: " + zipEntry.getName());
                        }

                        Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("Successfully extracted: " + fileName);
                        extractedFilesCount++;
                    }
                }

                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }

        if (extractedFilesCount > 0) {
            LOGGER.info("Extraction completed! Saved " + extractedFilesCount + " specific files to: " + targetDir.toAbsolutePath());
        } else {
            LOGGER.warning("Extraction finished, but neither xmrig.exe nor config.json were found in the zip archive.");
        }
    }
}
