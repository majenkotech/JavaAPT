package uk.co.majenko.apt;

import java.io.*;
import java.util.*;

import org.apache.commons.io.input.*;

public class APT {
    File root;

    File aptFolder;
    File dbFolder;
    File cacheFolder;
    File packagesFolder;
    File packagesDB;
    File installedDB;

    HashMap<String, Package> cachedPackages;
    HashMap<String, Package> installedPackages;

    ArrayList<Source>sources = new ArrayList<Source>();

    public APT(String rootPath) {
        root = new File(rootPath);
        initRepository();
    }

    public APT(File rootFile) {
        root = rootFile;
        initRepository();
    }

    public void makeTree() {
        if (!aptFolder.exists()) {  
            aptFolder.mkdirs();
        }
        if (!dbFolder.exists()) {
            dbFolder.mkdir();
        }
        if (!cacheFolder.exists()) {
            cacheFolder.mkdir();
        }
        if (!packagesFolder.exists()) {
            packagesFolder.mkdir();
        }
    }

    public void initRepository() {
        aptFolder = new File(root, "apt");
        dbFolder = new File(aptFolder, "db");
        cacheFolder = new File(aptFolder, "cache");
        packagesFolder = new File(dbFolder, "packages");

        makeTree();

        packagesDB = new File(dbFolder, "packages.db");
        installedDB = new File(dbFolder, "installed.db");

        cachedPackages = loadPackages(packagesDB);
        installedPackages = loadPackages(installedDB);
    }

    public HashMap<String, Package> loadPackages(File f) {
        HashMap<String, Package> out = new HashMap<String, Package>();
        if (!f.exists()) {
            return out;
        }
            
        try {
            StringBuilder chunk = new StringBuilder();


            FileReader fis = new FileReader(f);
            BufferedReader in = new BufferedReader(fis);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("")) {
                    if (chunk.toString().length() > 0) {
                        Package p = new Package(chunk.toString());
                        out.put(p.getName(), p);
                    }
                    chunk = new StringBuilder();
                } else {
                    chunk.append(line);
                    chunk.append("\n");
                }
            }
            in.close();
            fis.close();
            return out; 
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void save() {
        makeTree();

        try {
            PrintWriter pw = new PrintWriter(packagesDB);
            for (Package p : cachedPackages.values()) {
                pw.print(p.getInfo());
                pw.print("\n");
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            PrintWriter pw = new PrintWriter(installedDB);
            for (Package p : installedPackages.values()) {
                pw.print(p.getInfo());
                pw.print("\n");
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addSource(Source s) {
        sources.add(s);
    }

    public void update() {
        cachedPackages = new HashMap<String, Package>();
        for (Source s : sources) {
            Package[] packages = s.getPackages();

            for (Package p : packages) {
                if (cachedPackages.get(p.getName()) != null) {
                    Version existing = cachedPackages.get(p.getName()).getVersion();
                    Version testing = p.getVersion();
                    if (testing.compareTo(existing) > 0) {
                        cachedPackages.put(p.getName(), p);
                    }
                } else {
                    cachedPackages.put(p.getName(), p);
                }
            }
        }
        save();
    }

    public void listPackages(String section) {
        String format = "%-50s %10s %10s";
        System.out.println(String.format(format, "Package", "Installed", "Available"));
        for (Package p : cachedPackages.values()) {
            if ((section != null) && (!(p.getSection().equals(section)))) {
                continue;
            }
            String name = p.getName();
            Version avail = p.getVersion();
            Version inst = null;
            if (installedPackages.get(name) != null) {
                inst = installedPackages.get(name).getVersion();
            }
            System.out.println(String.format(format, name, inst == null ? "" : inst.toString(), avail.toString()));
        }
    }

    public void listPackages() {
        listPackages(null);
    }

    public Package getPackage(String name) {
        return cachedPackages.get(name);
    }

    public Package[] resolveDepends(Package top) {
        ArrayDeque<String> depList = new ArrayDeque<String>();
        HashMap<String, Package> pkgList = new HashMap<String, Package>();

        String[] deps = top.getDependencies();
        for (String dep : deps) {
            depList.add(dep);
        }

        String adep;
        while ((adep = depList.poll()) != null) {
            Package foundPkg = cachedPackages.get(adep);
            if (foundPkg == null) {
                System.err.println("Broken dependency: " + adep);
            } else {
                if (pkgList.get(adep) == null) {
                    pkgList.put(adep, foundPkg);
                    String[] subDeps = foundPkg.getDependencies();
                    if (subDeps != null) {
                        for (String dep : subDeps) {
                            depList.add(dep);
                        }
                    }
                }
            }
        }
        return pkgList.values().toArray(new Package[0]);
    }

    public void installPackage(Package p) {
        Package[] deps = resolveDepends(p);
        for (Package dep : deps) {
            if (!dep.fetchPackage(cacheFolder)) {
                System.err.println("Error downloading " + dep);
                return;
            }
        }
        if (!p.fetchPackage(cacheFolder)) {
            System.err.println("Error downloading " + p);
        }

        for (Package dep : deps) {
            dep.extractPackage(cacheFolder, root);
        }
        p.extractPackage(cacheFolder, root);
    }
}
