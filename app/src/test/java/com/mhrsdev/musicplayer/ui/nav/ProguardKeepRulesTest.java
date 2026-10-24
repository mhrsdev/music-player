package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * The release build is R8-minified: a class only ever loaded by its name string is removed or renamed unless a keep
 * rule names it. Fragments are covered by the Fragment rule; the views {@link Banners} loads need their own rule.
 */
public class ProguardKeepRulesTest {
    @Test public void everyViewLoadedByNameIsKept() throws Exception {
        File rules = new File("proguard-rules.pro");
        if (!rules.isFile()) rules = new File("app/proguard-rules.pro");
        String text = new String(Files.readAllBytes(rules.toPath()), StandardCharsets.UTF_8);
        for (String name : new String[] {Banners.UPDATE_BANNER}) {
            assertTrue(name + " is created by reflection but has no keep rule",
                    text.contains("-keep class " + name + " { public <init>(android.content.Context); }"));
        }
        assertTrue("fragments are instantiated by class name (FeatureScreens, player, restore)",
                text.contains("-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }"));
    }
}
