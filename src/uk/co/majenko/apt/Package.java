package uk.co.majenko.apt;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

public class Package implements Comparable, Serializable {
    public HashMap<String, String> properties = new HashMap<String, String>();

    public Package(String data) {
        String[] lines = data.split("\n");
        Pattern p = Pattern.compile("^([^:]+):\\s+(.*)$");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                properties.put(m.group(1), m.group(2));
            }
        }
    }

    public Package(String source, String data) {
        String[] lines = data.split("\n");
        Pattern p = Pattern.compile("^([^:]+):\\s+(.*)$");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                properties.put(m.group(1), m.group(2));
            }
        }
        properties.put("Repository", source);
    }

    public String toString() {
        return properties.get("Package") + " " + properties.get("Version");
    }

    public Version getVersion() {
        return new Version(properties.get("Version"));
    }

    public String getName() {
        return properties.get("Package");
    }

    public int compareTo(Object o) {
        if (o instanceof Package) {
            Package op = (Package)o;
            return getName().compareTo(op.getName());
        }
        return 0;
    }

    public String getRepository() {
        return properties.get("Repository");
    }

    public URI getURI() {
        try {
            return new URI(getRepository() + "/" + properties.get("Filename"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getInfo() {
        StringBuilder out = new StringBuilder();
        for (String k : properties.keySet()) {
            out.append(k + ": " + properties.get(k) + "\n");
        }
        return out.toString();
    }

    public String[] getDependencies() {
        String deps = properties.get("Depends");
        if (deps == null) {
            return null;
        }
        return deps.split(" ");
    }

    public String getSection() {
        return properties.get("Section");
    }

    public String getArchitecture() {
        return properties.get("Architecture");
    }

    public String getFilename() {
        return getName() + "_" + getVersion().toString() + "_" + getArchitecture() + ".deb";
    }

    public boolean fetchPackage(File folder) {
        File downloadTo = new File(folder, getFilename());
        try {
            URL downloadFrom = getURI().toURL();
            System.out.println("Downloading " + downloadFrom);
            HttpURLConnection httpConn = (HttpURLConnection) downloadFrom.openConnection();
            int contentLength = httpConn.getContentLength();

            if (downloadTo.exists()) {
                if (downloadTo.length() == contentLength) {
                    return true;
                }
            }
            InputStream in = httpConn.getInputStream();
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(downloadTo));

            byte[] buffer = new byte[1024];
            int n;
            long tot = 0;
            while ((n = in.read(buffer)) > 0) {
                tot += n;
                Long[] l = new Long[1];
                if (contentLength == -1) {
                    l[0] = new Long(0);
                    publish(l);
                } else {
                    l[0] = (tot * 100) / contentLength;
                    publish(l);
                }
                out.write(buffer, 0, n);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            if (downloadTo.exists()) {
                downloadTo.delete();
            }
            return false;
        }
        return true;
    }

    Long lastVal = -1L;
    public void publish(Long[] vals) {
        if (vals[0] != lastVal) {
            lastVal = vals[0];
            if ((lastVal % 10L) == 0L) {
                System.out.print(lastVal + "%");
            } else {
                System.out.print(".");
            }
            if (lastVal == 100L) {
                System.out.print("\n");
            }
        }
    }

    // Extract a package and install it. Returns the control file
    // contents as a string.
    public String extractPackage(File cache, File root) {
        String control = "";
        try {
            File src = new File(cache, getFilename());
            if (!src.exists()) {
                System.err.println("Unable to open cache file");
                return null;
            }

            System.out.println("Extracting " + getFilename());
            FileReader fr = new FileReader(src);
            BufferedReader br = new BufferedReader(fr);
            String line = br.readLine();
            if (line == null) {
                System.err.println("Invalid file format");
                return null;
            }
            if (!line.equals("!<arch>")) {
                System.err.println("Invalid file format");
                return null;
            }
            line = br.readLine();
            String filename = line.substring(0, 15).trim();
            String timestamp = line.substring(16, 27).trim();
            String owner = line.substring(28, 33).trim();
            String group = line.substring(34, 39).trim();
            String mode = line.substring(40, 47).trim();
            String size = line.substring(48, 57).trim();

            int length = Integer.parseInt(size);
            System.err.println("First file: " + filename + " is " + length + " bytes.");
            char[] data = new char[length];

            br.read(data, 0, length);

            String ver = new String(data);
            System.err.println("Archive version: " + ver);

            br.close();
            fr.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return control;
    }
}
