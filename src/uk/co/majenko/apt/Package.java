package uk.co.majenko.apt;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

import org.apache.commons.compress.archivers.ar.*;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.*;

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
    public boolean extractPackage(File cache, File db, File root) {
        String control = "";
        HashMap<String, Integer> installedFiles = new HashMap<String, Integer>();
        try {
            File src = new File(cache, getFilename());
            if (!src.exists()) {
                System.err.println("Unable to open cache file");
                return false;
            }


            System.out.println("Extracting " + getFilename());
            FileInputStream fis = new FileInputStream(src);
            ArArchiveInputStream ar = new ArArchiveInputStream(fis);

            ArArchiveEntry file = ar.getNextArEntry();
            while (file != null) {
                long size = file.getSize();
                String name = file.getName();

                System.err.println("Next entry: " + name + " at " + size + " bytes");
                if (name.equals("control.tar.gz")) {
                    GzipCompressorInputStream gzip = new GzipCompressorInputStream(ar);
                    TarArchiveInputStream tar = new TarArchiveInputStream(gzip);
                    TarArchiveEntry te = tar.getNextTarEntry();
                    while (te != null) {
                        int tsize = (int)te.getSize();
                        String tname = te.getName();
                        if (tname.equals("./control")) {
                            byte[] data = new byte[tsize];
                            tar.read(data, 0, tsize);
                            control = new String(data, "UTF-8");
                            System.err.println(control);
                        }
                        te = tar.getNextTarEntry();
                    }

                }

                if (name.equals("data.tar.gz")) {
                    GzipCompressorInputStream gzip = new GzipCompressorInputStream(ar);
                    TarArchiveInputStream tar = new TarArchiveInputStream(gzip);
                    TarArchiveEntry te = tar.getNextTarEntry();
                    while (te != null) {
                        int tsize = (int)te.getSize();
                        String tname = te.getName();
                        System.err.println("Extracting: " + tname + " at " + tsize + " bytes = " + te.getMode());

                        File dest = new File(root, tname);
                        if (te.isDirectory()) {
                            dest.mkdirs();
                            installedFiles.put(dest.getAbsolutePath(), -1);
                        } else {
                            byte[] buffer = new byte[1024];
                            int nread;
                            int toRead = tsize;
                            FileOutputStream fos = new FileOutputStream(dest);
                            while ((nread = tar.read(buffer, 0, toRead > 1024 ? 1024 : toRead)) > 0) {
                                toRead -= nread;
                                fos.write(buffer, 0, nread);
                            }
                            fos.close();
                            dest.setExecutable((te.getMode() & 0100) == 0100);
                            dest.setWritable((te.getMode() & 0200) == 0200);
                            dest.setReadable((te.getMode() & 0400) == 0400);
                            installedFiles.put(dest.getAbsolutePath(), tsize);
                        }
                        te = tar.getNextTarEntry();
                    }
                }
                    
                    
                file = ar.getNextArEntry();
            }


            ar.close();
            fis.close();
            

            File pf = new File(db, getName());
            pf.mkdirs();
            File cf = new File(pf, "control");
            PrintWriter pw = new PrintWriter(cf);
            pw.println(control);
            pw.close();
            File ff = new File(pf, "files");
            pw = new PrintWriter(ff);
            for (String f : installedFiles.keySet()) {
                pw.println(f);
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
}
