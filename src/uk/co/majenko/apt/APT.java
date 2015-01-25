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
        installedPackages = new HashMap<String, Package>();
        File[] pks = packagesFolder.listFiles();
        for (File pk : pks) {
            if (pk.isDirectory()) {
                if (!pk.getName().startsWith(".")) {
                    File pf = new File(pk, "control");
                    if (pf.exists()) {
                        HashMap<String, Package> ap = loadPackages(pf);
                        installedPackages.putAll(ap);
                    }
                }
            }
        }
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
        String format = "%-50s %10s %10s %s";
        System.out.println(String.format(format, "Package", "Installed", "Available", ""));
        for (Package p : cachedPackages.values()) {
            if ((section != null) && (!(p.getSection().equals(section)))) {
                continue;
            }
            String name = p.getName();
            Version avail = p.getVersion();
            Version inst = null;
            String msg = "";
            if (installedPackages.get(name) != null) {
                inst = installedPackages.get(name).getVersion();
                if (avail.compareTo(inst) > 0) {
                    msg = "UPDATE!";
                }
            }
            System.out.println(String.format(format, name, inst == null ? "" : inst.toString(), avail.toString(), msg));
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
        if (deps != null) {
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
        }
        return pkgList.values().toArray(new Package[0]);
    }

    public boolean isInstalled(Package p) {
        String name = p.getName();
        Package inst = installedPackages.get(name);
        if (inst == null) {
            return false;
        }
        Version iv = inst.getVersion();
        Version pv = p.getVersion();
        if (pv.compareTo(iv) > 0) {
            return false;
        }
        return true;
    }

    public void installPackage(Package p) {
        if (isInstalled(p)) {
            return;
        }
        Package[] deps = resolveDepends(p);
        for (Package dep : deps) {
            if (!isInstalled(dep)) {
                if (!dep.fetchPackage(cacheFolder)) {
                    System.err.println("Error downloading " + dep);
                    return;
                }
            }
        }
        if (!p.fetchPackage(cacheFolder)) {
            System.err.println("Error downloading " + p);
        }

        for (Package dep : deps) {
            if (!isInstalled(dep)) {
                dep.extractPackage(cacheFolder, packagesFolder, root);
            }
        }
        p.extractPackage(cacheFolder, packagesFolder, root);
        initRepository();
    }

    public Package[] getUpgradeList() {
        ArrayList<Package> toUpdate = new ArrayList<Package>();

        for (Package p : cachedPackages.values()) {
            String name = p.getName();
            Version avail = p.getVersion();
            Version inst = null;
            String msg = "";
            if (installedPackages.get(name) != null) {
                inst = installedPackages.get(name).getVersion();
                if (avail.compareTo(inst) > 0) {
                    toUpdate.add(p);
                }
            }
        }
        return toUpdate.toArray(new Package[0]);
    }

    public void uninstallPackage(Package p, boolean force) {
        try {
            if (!force) {
                for (Package ip : installedPackages.values()) {
                    String[] deps = ip.getDependencies();
                    if (deps == null) {
                        continue;
                    }
                    for (String dep : deps) {
                        if (dep.equals(p.getName())) {
                            System.err.println(p.getName() + " is required by " + ip.getName() + ". Will not remove.");
                            return;
                        }
                    }
                }
            }

            ArrayList<File>files = new ArrayList<File>();
            File pdir = new File(packagesFolder, p.getName());
            File plist = new File(pdir, "files");
            FileReader fr = new FileReader(plist);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                File f = new File(line);
                files.add(f);
            }
            br.close();
            fr.close();

            ArrayList<File>dirs = new ArrayList<File>();
            for (File f : files) {
                if (!f.isDirectory()) {
                    f.delete();
                } else {
                    dirs.add(f);
                }
            }

            Collections.sort(dirs);
            Collections.reverse(dirs);

            for (File dir : dirs) {
                dir.delete();
            }

            plist.delete();
            File cf = new File(pdir, "control");
            cf.delete();
            pdir.delete();
            initRepository();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
