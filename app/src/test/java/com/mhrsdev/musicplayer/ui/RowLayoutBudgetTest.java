package com.mhrsdev.musicplayer.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * SC1 (D39): every list row is bound, measured and drawn while scrolling, so row layouts stay shallow and carry no
 * per-row offscreen work: no elevation / translationZ shadow, no outline clip, no auto-size or marquee text, no
 * hardware layer, no per-view font variation (the font family XML picks the variable-font instance once).
 */
public class RowLayoutBudgetTest {
    private static final String[] ROWS = {"library_row_track", "queue_item", "home_card", "home_mix_card",
            "home_avatar", "home_chip", "library_grid_item", "home_header", "home_continue", "home_carousel",
            "home_stats", "home_actions", "lyrics_line_item", "u2_sheet_row"};
    private static final int MAX_DEPTH = 4;
    private static final String[] FORBIDDEN = {"elevation", "translationZ", "clipToOutline", "outlineProvider",
            "autoSizeTextType", "layerType", "fontVariationSettings"};

    @Test public void rowsAreShallowAndCarryNoOffscreenWork() throws Exception {
        StringBuilder bad = new StringBuilder();
        for (String name : ROWS) {
            File f = new File("src/main/res/layout/" + name + ".xml");
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getDocumentElement();
            int depth = depth(root);
            if (depth > MAX_DEPTH) bad.append(name).append(": depth ").append(depth).append('\n');
            check(root, name, bad);
        }
        assertTrue(bad.toString(), bad.length() == 0);
    }

    private static int depth(Node n) {
        int max = 0;
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE) max = Math.max(max, depth(c));
        }
        return 1 + max;
    }

    private static void check(Node n, String file, StringBuilder bad) {
        if (n.getNodeType() != Node.ELEMENT_NODE) return;
        NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            String attr = attrs.item(i).getNodeName(), value = attrs.item(i).getNodeValue();
            for (String f : FORBIDDEN) {
                if (attr.endsWith(":" + f)) bad.append(file).append(": ").append(attr).append('\n');
            }
            if (attr.endsWith(":ellipsize") && "marquee".equals(value)) bad.append(file).append(": marquee\n");
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) check(c, file, bad);
    }
}
