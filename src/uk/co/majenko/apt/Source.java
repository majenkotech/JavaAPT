package uk.co.majenko.apt;

import java.util.*;
import java.util.zip.*;
import java.net.*;
import java.io.*;

public class Source {
    HashMap<String, String> sectionUrls = new HashMap<String, String>();
    String urlRoot;

    public Source(String root, String dist, String arch, String[] sections) {
        urlRoot = root;
        for (String sec : sections) {
            String url = root;
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "dists/" + dist + "/" + sec + "/binary-" + arch + "/";
            sectionUrls.put(sec, url);
        }
    }

    public Package[] getPackages() {
        StringBuilder inData = new StringBuilder();
        HashMap<String, Package> packages = new HashMap<String, Package>();
        for (String url : sectionUrls.values()) {
            try {
                byte[] buffer = new byte[1024];

                URI packageFile = new URI(url + "Packages.gz");
                HttpURLConnection conn = (HttpURLConnection)(packageFile.toURL().openConnection());

                int contentLength = conn.getContentLength();
                InputStream rawIn = (InputStream)conn.getInputStream();
                GZIPInputStream in = new GZIPInputStream(rawIn);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                in.close();
                inData.append(out.toString("UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String[] lines = inData.toString().split("\n");
        StringBuilder onePackage = new StringBuilder();
        String currentLine = "";
        for (String line : lines) {
            if (line.equals("")) {
                onePackage.append(currentLine + "\n");
                Package thisPackage = new Package(urlRoot, onePackage.toString());
                if (packages.get(thisPackage.getName()) != null) {
                    Package testPackage = packages.get(thisPackage.getName());
                    if (thisPackage.getVersion().compareTo(testPackage.getVersion()) > 0) {
                        packages.put(thisPackage.getName(), thisPackage);
                    }
                } else {
                    packages.put(thisPackage.getName(), thisPackage);
                }
                onePackage = new StringBuilder();
                continue;
            }
            if (line.startsWith(" ")) {
                currentLine += line;
            } else {
                onePackage.append(currentLine + "\n");
                currentLine = line;
            }
        }

        if (!currentLine.equals("")) {
            onePackage.append(currentLine + "\n");
            Package thisPackage = new Package(urlRoot, onePackage.toString());
            if (packages.get(thisPackage.getName()) != null) {
                Package testPackage = packages.get(thisPackage.getName());
                if (thisPackage.getVersion().compareTo(testPackage.getVersion()) > 0) {
                    packages.put(thisPackage.getName(), thisPackage);
                }
            } else {
                packages.put(thisPackage.getName(), thisPackage);
            }
        }
        Package[] list = packages.values().toArray(new Package[0]);
        Arrays.sort(list);
        return list;
    }
}
