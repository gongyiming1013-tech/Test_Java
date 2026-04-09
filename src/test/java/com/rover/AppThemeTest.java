package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for App theme resolution and --theme CLI flag. */
public class AppThemeTest {

    // --- resolveTheme ---

    @Test
    public void resolveTheme_modern() {
        Theme theme = App.resolveTheme("modern");
        assertTrue(theme instanceof ModernTheme);
    }

    @Test
    public void resolveTheme_minimal() {
        Theme theme = App.resolveTheme("minimal");
        assertTrue(theme instanceof MinimalTheme);
    }

    @Test
    public void resolveTheme_mono() {
        Theme theme = App.resolveTheme("mono");
        assertTrue(theme instanceof MonoTheme);
    }

    @Test
    public void resolveTheme_caseInsensitive() {
        assertTrue(App.resolveTheme("MODERN") instanceof ModernTheme);
        assertTrue(App.resolveTheme("Modern") instanceof ModernTheme);
        assertTrue(App.resolveTheme("MONO") instanceof MonoTheme);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveTheme_invalidName_throws() {
        App.resolveTheme("neon");
    }

    @Test
    public void resolveTheme_null_autoDetects() {
        // When TERM is set (typical in most environments), should not return null
        Theme theme = App.resolveTheme(null);
        assertNotNull(theme);
    }

    // --- CLI integration ---

    @Test
    public void main_themeModern_visual_doesNotThrow() {
        App.main(new String[]{"--visual", "--delay", "0", "--theme", "modern", "MRM"});
    }

    @Test
    public void main_themeMinimal_visual_doesNotThrow() {
        App.main(new String[]{"--visual", "--delay", "0", "--theme", "minimal", "MRM"});
    }

    @Test
    public void main_themeMono_visual_doesNotThrow() {
        App.main(new String[]{"--visual", "--delay", "0", "--theme", "mono", "MRM"});
    }

    @Test
    public void main_themeWithGrid_doesNotThrow() {
        App.main(new String[]{"--grid", "5x5", "--visual", "--delay", "0",
                "--theme", "modern", "MMRM"});
    }

    @Test
    public void main_themeWithArena_doesNotThrow() {
        App.main(new String[]{"--arena", "--grid", "5x5", "--visual", "--delay", "0",
                "--theme", "minimal",
                "--rover", "R1:0,0,N:MM",
                "--rover", "R2:4,4,S:MM"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void main_invalidTheme_throws() {
        App.main(new String[]{"--visual", "--delay", "0", "--theme", "neon", "M"});
    }

    @Test
    public void main_noThemeFlag_defaultsWithoutError() {
        // Without --theme flag, should auto-detect and run fine
        App.main(new String[]{"--visual", "--delay", "0", "MRM"});
    }
}
