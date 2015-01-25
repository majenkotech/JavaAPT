package uk.co.majenko.apt;

import java.io.*;

public class CLI {
    static public void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Usage: apt <path> <command [args]");
            System.exit(10);
        }

        File root = new File(args[0]);

        APT apt = new APT(root);

        String[] sections = {
            "cores",
            "boards",
            "compilers",
            "plugins"
        };
        Source s = new Source("http://dist.majenko.co.uk", "uecide", "linux-amd64", sections);

        apt.addSource(s);

        if (args[1].equals("update")) {
            apt.update();
        }
        if (args[1].equals("list")) {
            if (args.length == 3) {
                apt.listPackages(args[2]);
            } else {
                apt.listPackages();
            }
        }
        if (args[1].equals("install")) {
            if (args.length != 3) {
                System.err.println("Usage: apt <path> install <package>");
                System.exit(10);
            }
            Package p = apt.getPackage(args[2]);
            if (p == null) {
                System.err.println("Package not found");
                System.exit(10);
            }
            apt.installPackage(p);
        }
        if (args[1].equals("show")) {
            if (args.length != 3) {
                System.err.println("Usage: apt <path> show <package>");
                System.exit(10);
            }
            Package p = apt.getPackage(args[2]);
            if (p == null) {
                System.err.println("Package not found");
                System.exit(10);
            }
            System.out.println(p.getInfo());
        }
        if (args[1].equals("deps")) {
            if (args.length != 3) {
                System.err.println("Usage: apt <path> deps <package>");
                System.exit(10);
            }
            Package p = apt.getPackage(args[2]);
            if (p == null) {
                System.err.println("Package not found");
                System.exit(10);
            }
            System.out.println(p.getName() + " depends on:");
            Package[] deps = apt.resolveDepends(p);
            for (Package px : deps) {
                System.out.println("    " + px.getName());
            }
        }
    }
}
