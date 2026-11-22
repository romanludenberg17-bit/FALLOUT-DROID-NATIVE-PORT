package com.wastelanddroid.mods;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A FOMOD installer (fomod/ModuleConfig.xml) without its dialog: the required files, and in
 * each group of options the ones the author marked Required or Recommended, else (a group
 * where one must be picked) the first usable one. Conditional installs whose flags the
 * chosen options set are added. The result maps each archive entry to the Data paths it
 * is copied to, the higher priority winning where two write the same path.
 */
final class Fomod {
    /** source folder or file (archive path, lower case, '/' separators) -> destination. */
    static final class Rule {
        final String source, destination;
        final boolean folder;
        final int priority, order;

        Rule(String s, String d, boolean f, int p, int o) {
            source = s;
            destination = d;
            folder = f;
            priority = p;
            order = o;
        }
    }

    final List<Rule> rules = new ArrayList<>();
    final List<String> chosen = new ArrayList<>();
    private final Map<String, String> flags = new HashMap<>();
    private int order;

    static String norm(String p) {
        String s = p == null ? "" : p.replace('\\', '/').trim();
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.toLowerCase(Locale.ROOT);
    }

    static Fomod parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        Fomod m = new Fomod();
        Element root = doc.getDocumentElement();
        for (Element req : children(root, "requiredInstallFiles")) m.files(req, 0);
        for (Element steps : children(root, "installSteps")) {
            for (Element step : children(steps, "installStep")) {
                for (Element groups : children(step, "optionalFileGroups")) {
                    for (Element g : children(groups, "group")) m.group(g);
                }
            }
        }
        for (Element cond : children(root, "conditionalFileInstalls")) {
            for (Element patterns : children(cond, "patterns")) {
                for (Element pat : children(patterns, "pattern")) {
                    boolean ok = true;
                    for (Element deps : children(pat, "dependencies")) ok = m.depsMet(deps);
                    if (ok) for (Element files : children(pat, "files")) m.files(files, 0);
                }
            }
        }
        return m;
    }

    private boolean depsMet(Element deps) {
        boolean or = "Or".equalsIgnoreCase(deps.getAttribute("operator"));
        boolean any = false, all = true, seen = false;
        for (Element d : children(deps, "flagDependency")) {
            seen = true;
            String v = flags.get(d.getAttribute("flag"));
            boolean met = v != null && v.equalsIgnoreCase(d.getAttribute("value"));
            any |= met;
            all &= met;
        }
        for (Element d : children(deps, "dependencies")) {
            seen = true;
            boolean met = depsMet(d);
            any |= met;
            all &= met;
        }
        // File and game-version conditions are not checked: taken as met.
        if (!seen) return true;
        return or ? any : all;
    }

    private void group(Element g) {
        String type = g.getAttribute("type");
        List<Element> plugins = new ArrayList<>();
        for (Element ps : children(g, "plugins")) plugins.addAll(children(ps, "plugin"));
        List<Element> pick = new ArrayList<>();
        Element firstUsable = null;
        for (Element p : plugins) {
            String t = pluginType(p);
            if (!"NotUsable".equalsIgnoreCase(t) && firstUsable == null) firstUsable = p;
            if ("SelectAll".equalsIgnoreCase(type) || "Required".equalsIgnoreCase(t) || "Recommended".equalsIgnoreCase(t)) pick.add(p);
        }
        boolean mustPick = "SelectExactlyOne".equalsIgnoreCase(type) || "SelectAtLeastOne".equalsIgnoreCase(type);
        if (pick.isEmpty() && mustPick && firstUsable != null) pick.add(firstUsable);
        if ("SelectExactlyOne".equalsIgnoreCase(type) && pick.size() > 1) pick = pick.subList(0, 1);
        for (Element p : pick) {
            chosen.add(p.getAttribute("name"));
            for (Element files : children(p, "files")) files(files, 0);
            for (Element cf : children(p, "conditionFlags")) {
                for (Element fl : children(cf, "flag")) flags.put(fl.getAttribute("name"), fl.getTextContent().trim());
            }
        }
    }

    private static String pluginType(Element p) {
        for (Element td : children(p, "typeDescriptor")) {
            for (Element t : children(td, "type")) return t.getAttribute("name");
            for (Element dt : children(td, "dependencyType")) {
                for (Element t : children(dt, "defaultType")) return t.getAttribute("name");
            }
        }
        return "Optional";
    }

    private void files(Element files, int base) {
        NodeList l = files.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            Node n = l.item(i);
            if (!(n instanceof Element)) continue;
            Element e = (Element) n;
            String tag = e.getTagName();
            if (!tag.equals("file") && !tag.equals("folder")) continue;
            String src = norm(e.getAttribute("source"));
            String dst = e.hasAttribute("destination") ? norm(e.getAttribute("destination")) : (tag.equals("file") ? src : "");
            int pr = 0;
            try {
                pr = Integer.parseInt(e.getAttribute("priority").trim());
            } catch (NumberFormatException ignored) {
            }
            rules.add(new Rule(src, dst, tag.equals("folder"), pr + base, order++));
        }
    }

    private static List<Element> children(Element e, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList l = e.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            Node n = l.item(i);
            if (n instanceof Element && ((Element) n).getTagName().equalsIgnoreCase(tag)) out.add((Element) n);
        }
        return out;
    }

    /**
     * Archive entry -> Data paths, for the entries under {@code base} (the folder holding
     * fomod/). A later rule of equal or higher priority replaces an earlier one's file.
     */
    Map<String, List<String>> plan(List<String> entries, String base) {
        String b = norm(base);
        if (!b.isEmpty()) b += "/";
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort((x, y) -> x.priority != y.priority ? Integer.compare(x.priority, y.priority) : Integer.compare(x.order, y.order));
        Map<String, String> byDest = new HashMap<>();
        Map<String, String> destCase = new HashMap<>();
        for (Rule r : sorted) {
            String src = b + r.source;
            for (String entry : entries) {
                String low = entry.toLowerCase(Locale.ROOT);
                String rest;
                if (r.folder) {
                    if (r.source.isEmpty()) {
                        if (!low.startsWith(b)) continue;
                        rest = entry.substring(b.length());
                    } else if (low.startsWith(src + "/")) {
                        rest = entry.substring(src.length() + 1);
                    } else continue;
                } else {
                    if (!low.equals(src)) continue;
                    rest = "";
                }
                if (rest.toLowerCase(Locale.ROOT).startsWith("fomod/")) continue;
                String dest = r.folder ? (r.destination.isEmpty() ? rest : r.destination + "/" + rest) : r.destination;
                if (dest.isEmpty()) continue;
                byDest.put(dest.toLowerCase(Locale.ROOT), entry);
                destCase.put(dest.toLowerCase(Locale.ROOT), dest);
            }
        }
        Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, String> e : byDest.entrySet()) {
            out.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(destCase.get(e.getKey()));
        }
        return out;
    }
}
