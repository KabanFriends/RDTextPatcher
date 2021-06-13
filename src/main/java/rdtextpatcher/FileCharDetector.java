package rdtextpatcher;

import org.mozilla.universalchardet.UniversalDetector;

public class FileCharDetector {

    private String file;

    public FileCharDetector(String file) {
        this.file = file;
    }

    public String detector() throws java.io.IOException {
        byte[] buf = new byte[4096];
        String fileName = this.file;
        java.io.FileInputStream fis = new java.io.FileInputStream(fileName);

        UniversalDetector detector = new UniversalDetector(null);

        int nread;
        while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }
        detector.dataEnd();

        String encType = detector.getDetectedCharset();
        if (encType == null) {
            encType = "UTF-8";
        }

        detector.reset();

        return encType;
    }
}
